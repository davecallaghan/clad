package clad.gateway.roc

import clad.gateway.config.*
import com.networknt.schema.{InputFormat, JsonSchemaFactory, SpecVersion}
import java.math.RoundingMode
import scala.util.Try

/** What a checker sees: the model's output, the request it answered and the evidence it was given.
  * `evidence` is the rows (numbers_grounded) or the pages text (extraction_grounded) the answer must rest on;
  * `responseSchema` is the request's `response_format.json_schema.schema`. */
final case class OutputContext(content: String, toolCalls: Vector[ujson.Obj], evidence: Option[ujson.Value],
                               requestTools: Vector[String], responseSchema: Option[ujson.Value], templateId: String)

/** The deterministic output checkers (spec 8.2). Each is a pure function of the output context.
  *
  * `check` returns `Right(true)` when the property is PRESENT. For obligations (numbers_grounded, extraction_grounded,
  * schema_valid, tool_calls_valid, route_valid) present means the obligation holds; for prohibitions (contains_pii,
  * guarantee_language) it means the bad thing was found. An unknown property is a `Left`. Where there is nothing to
  * check against (no evidence, no schema) the obligation is treated as satisfied, and `applicable` says so, so the
  * caller can record "not applicable" in the entry. */
object Checkers:
  private val Guarantee = Vector("""guaranteed? returns?""", """risk[- ]free""", """can'?t lose""", """no risk""")
    .map(r => java.util.regex.Pattern.compile(r, java.util.regex.Pattern.CASE_INSENSITIVE))
  private val Hundred = BigDecimal(100)
  private val Token = """(\d{1,3}(?:,\d{3})+|\d+)(\.\d+)?(\s*%)?""".r

  /** A figure as written: its value, the decimals shown and whether it was a percentage. */
  private final case class Figure(value: BigDecimal, decimals: Int, percent: Boolean)

  private def figures(text: String): Vector[Figure] =
    Token.findAllMatchIn(text).map { m =>
      val frac = Option(m.group(2)).map(_.drop(1)).getOrElse("")
      Figure(BigDecimal(m.group(1).replace(",", "") + (if frac.isEmpty then "" else "." + frac)), frac.length, m.group(3) != null)
    }.toVector

  private def numeric(n: Double): Figure =
    val bd = BigDecimal.decimal(n).bigDecimal.stripTrailingZeros
    Figure(BigDecimal(bd), math.max(bd.scale, 0), false)

  /** Every number in a JSON value: JSON numbers as they are, numbers inside strings by the text rules. */
  private def jsonFigures(v: ujson.Value): Vector[Figure] = v match
    case ujson.Num(n) => Vector(numeric(n))
    case ujson.Str(s) => figures(s)
    case ujson.Arr(a) => a.toVector.flatMap(jsonFigures)
    case o: ujson.Obj => o.value.values.toVector.flatMap(jsonFigures)
    case _            => Vector.empty

  private def matches(f: Figure, row: BigDecimal): Boolean =
    def rounded(v: BigDecimal, d: Int) = BigDecimal(v.bigDecimal.setScale(d, RoundingMode.HALF_UP))
    if f.percent then rounded(row, f.decimals) == f.value || rounded(row, f.decimals + 2) == f.value / Hundred
    else rounded(row, f.decimals) == f.value

  private def ignored(f: Figure, runtime: Runtime): Boolean =
    f.decimals == 0 && !f.percent && f.value.abs < BigDecimal(runtime.groundingIgnoreBelow)

  private def grounded(claims: Vector[Figure], evidence: ujson.Value, runtime: Runtime): Boolean =
    val rows = jsonFigures(evidence).map(_.value)
    claims.filterNot(ignored(_, runtime)).forall(f => rows.exists(matches(f, _)))

  def applicable(property: String, ctx: OutputContext): Boolean = property match
    case "numbers_grounded" | "extraction_grounded" => ctx.evidence.isDefined
    case "schema_valid"                             => ctx.responseSchema.isDefined
    case _                                          => true

  private def parse(s: String): Option[ujson.Value] = Try(ujson.read(s)).toOption

  private def schemaValid(content: String, schema: ujson.Value): Either[String, Boolean] =
    Try {
      val compiled = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(ujson.write(schema))
      parse(content).isDefined && compiled.validate(content, InputFormat.JSON).isEmpty
    }.toEither.left.map(e => s"schema_valid: ${e.getMessage}")

  private def callName(c: ujson.Obj): Option[String] =
    val fn = c.value.get("function").collect { case o: ujson.Obj => o }.getOrElse(c)
    fn.value.get("name").collect { case ujson.Str(s) => s }

  private def callArguments(c: ujson.Obj): Option[ujson.Value] =
    val fn = c.value.get("function").collect { case o: ujson.Obj => o }.getOrElse(c)
    fn.value.get("arguments")

  private def argumentsObject(a: Option[ujson.Value]): Boolean = a match
    case Some(_: ujson.Obj)  => true
    case Some(ujson.Str(s)) => parse(s).exists(_.isInstanceOf[ujson.Obj])
    case _                   => false

  private def toolCallsValid(ctx: OutputContext): Boolean =
    ctx.toolCalls.forall(c => callName(c).exists(ctx.requestTools.contains) && argumentsObject(callArguments(c)))

  private def routeChoice(content: String): Option[String] =
    parse(content).flatMap { v =>
      Try(v("answers")("intent")("choice")).toOption.collect { case ujson.Str(s) => s }
    }

  def check(property: String, ctx: OutputContext, runtime: Runtime, roc: RocConfig, gov: Governance): Either[String, Boolean] =
    property match
      case "numbers_grounded" =>
        Right(ctx.evidence.forall(ev => grounded(figures(ctx.content.replace("$", "")), ev, runtime)))
      case "extraction_grounded" =>
        Right(ctx.evidence.forall(ev => parse(ctx.content).exists(j => grounded(jsonFigures(j), ev, runtime))))
      case "schema_valid" =>
        ctx.responseSchema.fold[Either[String, Boolean]](Right(true))(schemaValid(ctx.content, _))
      case "tool_calls_valid" => Right(toolCallsValid(ctx))
      case "contains_pii" =>
        gov.checkers.get("contains_pii").toRight("contains_pii: no checker is defined in constraints.yaml").flatMap { ch =>
          Try(ch.patterns.map(java.util.regex.Pattern.compile(_)).exists(_.matcher(ctx.content).find())).toEither.left
            .map(e => s"contains_pii: ${e.getMessage}")
        }
      case "guarantee_language" => Right(Guarantee.exists(_.matcher(ctx.content).find()))
      case "route_valid"        => Right(routeChoice(ctx.content).exists(roc.routerIntents.contains))
      case other                => Left(s"no deterministic checker for property $other")
