package clad.gateway

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import clad.audit.ChainAnchorStore
import clad.gateway.config.{Governance, GatewayConfig}
import clad.gateway.`export`.BundleExport
import clad.gateway.epg.{Epg, StartupCheck}
import clad.gateway.http.{Clients, Routes}
import clad.gateway.keys.Keys
import clad.gateway.pipeline.Gateway
import clad.gateway.store.{Migrate, PgStore}
import clad.gateway.upstream.{HttpPassthrough, HttpUpstream}
import com.comcast.ip4s.{Host, Port}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import java.nio.file.{Files, Path}
import java.time.Instant
import java.time.format.DateTimeParseException

/** The `clad-gateway` command line. Every value comes from the environment (`run` takes it as a map so tests need no
  * process environment); a missing variable or argument exits 2 naming it. No message here contains a secret. */
object Main:
  private final class Usage(message: String) extends Exception(message)

  private val KeyRoles = Seq("components", "supervisor")
  private val Commands = "serve | migrate | keys | export --since <t> --until <t> --out <dir> [--probe-since <t> --probe-until <t>] | approval-digest | check"

  def main(args: Array[String]): Unit = sys.exit(run(args.toSeq, sys.env))

  def run(args: Seq[String], env: Map[String, String]): Int =
    try args match
      case Seq("serve")           => serve(Env(env))
      case Seq("migrate")         => migrate(Env(env))
      case Seq("keys")            => generateKeys(Env(env))
      case Seq("export", rest*)   => exportBundle(Env(env), flags(rest))
      case Seq("approval-digest") => approvalDigest(Env(env))
      case Seq("check")           => check(Env(env))
      case _                      => throw Usage(s"usage: clad-gateway <$Commands>")
    catch case u: Usage =>
      Console.err.println(s"clad-gateway: ${u.getMessage}")
      2

  private final class Env(vars: Map[String, String]):
    def req(name: String): String = vars.get(name).filter(_.nonEmpty).getOrElse(throw Usage(s"$name is not set"))
    def opt(name: String): Option[String] = vars.get(name).filter(_.nonEmpty)
    def path(name: String): Path = Path.of(req(name))

  private def fail(message: String): Int =
    Console.err.println(s"clad-gateway: $message")
    1

  private def flags(args: Seq[String]): Map[String, String] =
    args.grouped(2).foldLeft(Map.empty[String, String]) {
      case (m, Seq(k, v)) if k.startsWith("--") => m + (k -> v)
      case (_, other)                           => throw Usage(s"export: expected --flag value pairs, got ${other.mkString(" ")}")
    }

  private def pairs(spec: String, what: String): Map[String, String] =
    spec.split(",").toSeq.map(_.trim).filter(_.nonEmpty).map { e =>
      e.split("=", 2) match
        case Array(k, v) if k.trim.nonEmpty && v.trim.nonEmpty => k.trim -> v.trim
        case _ => throw Usage(s"$what: every entry must be name=value")
    }.toMap

  private def loadConfig(env: Env): Either[String, GatewayConfig] = GatewayConfig.load(env.path("CLAD_GATEWAY_CONFIG_DIR"))

  private def report(cfg: GatewayConfig, epg: Epg): Boolean =
    val r = StartupCheck.run(cfg, epg)
    r.warnings.foreach(w => println(s"CLAD_GATEWAY_WARN $w"))
    r.errors.foreach(e => Console.err.println(s"CLAD_GATEWAY_ERROR $e"))
    r.errors.isEmpty

  private def check(env: Env): Int =
    val outcome = for
      cfg <- loadConfig(env)
      epg <- Epg.build(cfg)
    yield report(cfg, epg)
    outcome.fold(fail, ok => if ok then 0 else 1)

  private def approvalDigest(env: Env): Int =
    Governance.load(env.path("CLAD_GATEWAY_CONFIG_DIR").resolve("constraints.yaml")).fold(fail, g =>
      println(s"constraints_digest: ${g.approvalDigest}")
      println(g.approvalPending.fold("approval: approved")(why => s"approval: pending ($why)"))
      0)

  private def migrate(env: Env): Int =
    Migrate.run(env.req("CLAD_GATEWAY_ADMIN_URL"), env.req("CLAD_GATEWAY_WRITER_PASSWORD"), env.req("CLAD_GATEWAY_READER_PASSWORD"))
      .fold(fail, _ => { println("migrated"); 0 })

  private def generateKeys(env: Env): Int =
    val dir = env.path("CLAD_GATEWAY_KEYS_DIR")
    val results = KeyRoles.map(role => role -> Keys.generate(dir, role))
    results.collectFirst { case (role, Left(e)) => s"$role: $e" } match
      case Some(e) => fail(e)
      case None =>
        results.foreach {
          case (role, Right(true))  => println(s"generated $role key in $dir")
          case (role, Right(false)) => println(s"kept existing $role key in $dir")
          case (_, Left(_))         => ()
        }
        println(s"Back up $dir now: the private keys cannot be recovered, and records they signed stay verifiable only with the matching public keys.")
        0

  private def instant(flags: Map[String, String], name: String): Instant =
    val text = flags.getOrElse(name, throw Usage(s"export: $name is required"))
    try Instant.parse(text)
    catch case _: DateTimeParseException => throw Usage(s"export: $name is not an ISO-8601 instant (for example 2026-01-01T00:00:00Z)")

  private def exportBundle(env: Env, f: Map[String, String]): Int =
    val since = instant(f, "--since")
    val until = instant(f, "--until")
    val out = Path.of(f.getOrElse("--out", throw Usage("export: --out is required")))
    val probe = (f.contains("--probe-since"), f.contains("--probe-until")) match
      case (true, true)   => Some((instant(f, "--probe-since"), instant(f, "--probe-until")))
      case (false, false) => None
      case _              => throw Usage("export: --probe-since and --probe-until go together")
    val readerUrl = env.req("CLAD_GATEWAY_READER_URL")
    val egress = env.path("CLAD_GATEWAY_EGRESS_LOG")
    val listeners = pairs(env.req("CLAD_GATEWAY_LISTENERS"), "CLAD_GATEWAY_LISTENERS")
    val cfgDir = env.path("CLAD_GATEWAY_CONFIG_DIR")
    val keysDir = env.path("CLAD_GATEWAY_KEYS_DIR")
    val data = env.path("CLAD_GATEWAY_DATA_DIR")
    val app = env.req("CLAD_GATEWAY_APP")
    val written = for
      gov <- Governance.load(cfgDir.resolve("constraints.yaml"))
      bundle <- BundleExport.write(out, since, until, probe, readerUrl, Some(egress), listeners, data.resolve("anchors"), keysDir, gov, app)
    yield bundle
    written.fold(fail, p => { println(s"bundle written to $p"); 0 })

  private def serve(env: Env): Int =
    val dir = env.path("CLAD_GATEWAY_CONFIG_DIR")
    val keysDir = env.path("CLAD_GATEWAY_KEYS_DIR")
    val data = env.path("CLAD_GATEWAY_DATA_DIR")
    val writerUrl = env.req("CLAD_GATEWAY_WRITER_URL")
    val upstreamUrls = pairs(env.req("CLAD_GATEWAY_UPSTREAMS"), "CLAD_GATEWAY_UPSTREAMS")
    val upstreamKeys = upstreamUrls.map((name, url) => name -> (url, env.req(s"CLAD_GATEWAY_KEY_${name.toUpperCase}")))
    val chat = upstreamKeys.getOrElse("openai", throw Usage("CLAD_GATEWAY_UPSTREAMS must name an upstream called openai (the chat upstream)"))
    val tokens = env.req("CLAD_GATEWAY_TOKENS")
    val adminToken = env.req("CLAD_GATEWAY_ADMIN_TOKEN")
    val port = Port.fromInt(env.req("CLAD_GATEWAY_PORT").toIntOption.getOrElse(throw Usage("CLAD_GATEWAY_PORT is not a number")))
      .getOrElse(throw Usage("CLAD_GATEWAY_PORT is not a valid port"))
    val host = env.opt("CLAD_GATEWAY_HOST").map(h => Host.fromString(h).getOrElse(throw Usage("CLAD_GATEWAY_HOST is not a valid host")))
    val built = for
      cfg <- GatewayConfig.load(dir)
      epg <- Epg.build(cfg)
      _ <- Either.cond(report(cfg, epg), (), "the startup check found errors; the gateway does not start")
      comp <- Keys.load(keysDir, "components")
      sup <- Keys.load(keysDir, "supervisor")
      clients <- Clients.parse(tokens, adminToken)
      anchors <- createDir(data.resolve("anchors"))
    yield
      val store = PgStore(writerUrl, cfg.runtime.chainLockKey, ChainAnchorStore(anchors), comp)
      val gateway = Gateway(cfg, epg, store, comp, sup, HttpUpstream(chat._1, chat._2, cfg.runtime),
        HttpPassthrough(upstreamKeys, cfg.runtime), data.resolve("containment.json"), () => Instant.now())
      Routes(gateway, clients).routes
    built.fold(fail, routes =>
      val base = EmberServerBuilder.default[IO].withPort(port)
      val server = host.fold(base)(base.withHost(_)).withHttpApp(Router("/" -> routes).orNotFound)
      println(s"clad-gateway listening on port $port")
      server.build.useForever.unsafeRunSync()
      0)

  private def createDir(p: Path): Either[String, Path] =
    try Right(Files.createDirectories(p))
    catch case e: java.io.IOException => Left(s"cannot create $p: ${e.getMessage}")
