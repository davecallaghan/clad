package clad.gateway.roc

import clad.gateway.config.{Canonical, GatewayConfig}
import clad.gateway.store.GatewayStore
import clad.integrity.{GilEntry, InteractionId}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.Instant
import scala.util.Try

/** One labelled item of classifiers/validation.jsonl. `category` (e.g. S6) names the Llama Guard category an unsafe
  * item belongs to; without one the item is judged on P(unsafe). */
final case class ValidationItem(id: String, conversation: Vector[ujson.Obj], expected: String, category: Option[String])

/** Measured error rates at the enterprise τ. fp = safe items scored unsafe / safe items; fn = unsafe items scored safe
  * / unsafe items. */
final case class ValidationResult(profile: String, validatedAt: Instant, fp: Double, fn: Double, items: Int,
                                  misclassified: Vector[String]):
  def json: ujson.Obj = ujson.Obj("profile" -> profile, "validatedAt" -> validatedAt.toString, "fp" -> fp, "fn" -> fn,
    "items" -> items, "misclassified" -> ujson.Arr.from(misclassified.map(ujson.Str(_))))

  /** The lines the operator copies into classifiers/llama_guard.yaml. */
  def profileLines: Vector[String] =
    Vector(s"measured_fp: $fp", s"measured_fn: $fn", s"""validated_at: "$validatedAt"""")

/** `clad-gateway validate-classifier` (spec 8.4): scores every labelled item and measures FP and FN. It runs only when a
  * human has attested the labels: an attestation of `classifier.llama_guard.validation` whose template_version is the
  * sha256 of the file as it is now. Each scoring call is a model call, so it is registered in the GIL first. */
object Validation:
  val TemplateId = "classifier.llama_guard.validation"
  val Principal = "classifier.validation"
  val FileName = "validation.jsonl"
  private val Labels = Set("safe", "unsafe")

  def file(configDir: Path): Path = configDir.resolve("classifiers").resolve(FileName)

  def read(path: Path): Either[String, (String, Vector[ValidationItem])] =
    Try(Files.readString(path, UTF_8)).toEither.left.map(e => s"cannot read $path: ${e.getMessage}").flatMap { text =>
      val lines = text.linesIterator.zipWithIndex.filter(_._1.trim.nonEmpty).toVector
      lines.foldLeft[Either[String, Vector[ValidationItem]]](Right(Vector.empty)) { case (acc, (line, i)) =>
        acc.flatMap(items => item(line).left.map(e => s"$path line ${i + 1}: $e").map(items :+ _))
      }.flatMap { items =>
        val dup = items.groupBy(_.id).collect { case (id, xs) if xs.size > 1 => id }
        if items.isEmpty then Left(s"$path holds no items")
        else if dup.nonEmpty then Left(s"$path: duplicate ids ${dup.toVector.sorted.mkString(", ")}")
        else Right(Canonical.sha256(text) -> items)
      }
    }

  private def item(line: String): Either[String, ValidationItem] =
    for
      v <- Try(ujson.read(line)).toEither.left.map(e => s"not JSON: ${e.getMessage}")
      o <- v.objOpt.toRight("not a JSON object")
      id <- o.get("id").flatMap(_.strOpt).toRight("id must be a string")
      conv <- o.get("conversation").flatMap(_.arrOpt).filter(a => a.nonEmpty && a.forall(_.objOpt.isDefined))
        .map(_.toVector.map(x => ujson.Obj.from(x.obj))).toRight("conversation must be a non-empty list of messages")
      expected <- o.get("expected").flatMap(_.strOpt).filter(Labels.contains).toRight("expected must be safe or unsafe")
      category <- o.get("category") match
        case None | Some(ujson.Null) => Right(None)
        case Some(ujson.Str(s))      => Right(Some(s))
        case Some(_)                 => Left("category must be a string")
    yield ValidationItem(id, conv, expected, category)

  /** The human review of the labels, for exactly this file content. */
  def attested(cfg: GatewayConfig, digest: String): Either[String, Unit] =
    cfg.attestations.get(TemplateId, digest).map(_ => ()).toRight(
      s"no attestation of $TemplateId at version $digest in attestations.yaml: a human must review the labels of this " +
        "exact file first (an edit to the file needs a new attestation)")

  /** τ an item is judged against: the enterprise threshold of the classifier constraint reading the item's category
    * (or P(unsafe) when it has none), else tau_max. */
  private def tau(cfg: GatewayConfig, item: ValidationItem): Either[String, Double] =
    cfg.roc.constraints.find(c => c.kind == "classifier" && c.category == item.category)
      .toRight(s"${item.id}: no classifier constraint reads ${item.category.fold("P(unsafe)")(c => s"category $c")}")
      .map(c => cfg.roc.thresholds.find(th => th.property == c.property && th.level == "enterprise").map(_.tau)
        .getOrElse(cfg.roc.tauMax))

  private def score(s: GuardScores, item: ValidationItem): Double =
    item.category.fold(s.unsafe)(k => s.categories.getOrElse(k, s.unsafe))

  /** Scores every item. `project` is the declared project the GIL entries are bound to; its tier is recorded. Any
    * scoring failure ends the run: a rate over the items that happened to score would not measure the classifier. */
  def run(cfg: GatewayConfig, items: Vector[ValidationItem], digest: String, guard: Guard, store: GatewayStore,
          project: String, clock: () => Instant): Either[String, ValidationResult] =
    for
      p <- cfg.governance.projects.get(project).toRight(s"unknown project $project")
      safe = items.count(_.expected == "safe")
      _ <- Either.cond(safe > 0 && safe < items.size, (), "the validation set needs both safe and unsafe items")
      wrong <- items.foldLeft[Either[String, Vector[ValidationItem]]](Right(Vector.empty)) { (acc, item) =>
        acc.flatMap { wrong =>
          val iid = InteractionId.generate()
          val md = Map("project" -> project, "principal" -> Principal, "tier" -> p.tier, "template_id" -> TemplateId,
            "template_version" -> digest, "model" -> cfg.roc.classifier.model, "validation_item" -> item.id)
          for
            _ <- store.register(GilEntry(iid, clock(), md))
            s <- guard.score(item.conversation, iid.value).left.map(e => s"${item.id}: classifier: $e")
            t <- tau(cfg, item)
          yield if (score(s, item) >= t) != (item.expected == "unsafe") then wrong :+ item else wrong
        }
      }
    yield
      val fp = wrong.count(_.expected == "safe")
      val fn = wrong.count(_.expected == "unsafe")
      ValidationResult(cfg.roc.classifier.id, clock(), fp.toDouble / safe, fn.toDouble / (items.size - safe), items.size,
        wrong.map(_.id))

  def write(dataDir: Path, r: ValidationResult): Either[String, Path] =
    Try {
      val out = dataDir.resolve("classifier-validation.json")
      Files.writeString(out, ujson.write(r.json, indent = 2), UTF_8)
      out
    }.toEither.left.map(e => s"cannot write to $dataDir: ${e.getMessage}")
