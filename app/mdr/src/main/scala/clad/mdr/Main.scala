package clad.mdr

import clad.gateway.config.{Governance, Templates}
import clad.gateway.`export`.BundleExport
import clad.mdr.detectors.*
import java.nio.file.Files
import java.time.{Duration, Instant}

/** The `clad-mdr` command line. Every value comes from the environment (see MdrEnv); MDR holds no signing key and no
  * provider key, and connects as clad_gov_reader. A missing variable exits 2 naming it. */
object Main:
  private val Commands = "run | once | check"

  def main(args: Array[String]): Unit = sys.exit(run(args.toSeq, sys.env))

  def run(args: Seq[String], env: Map[String, String]): Int =
    (args, MdrEnv.from(env)) match
      case (_, Left(e))              => usage(e)
      case (Seq("run"), Right(e))    => loop(e)
      case (Seq("once"), Right(e))   => once(e)
      case (Seq("check"), Right(e))  => check(e)
      case _                         => usage(s"usage: clad-mdr <$Commands>")

  private def usage(message: String): Int =
    Console.err.println(s"clad-mdr: $message")
    2

  private def fail(message: String): Int =
    Console.err.println(s"clad-mdr: $message")
    1

  private def config(e: MdrEnv): Either[String, (MdrConfig, Governance)] =
    for
      cfg <- MdrConfig.load(e.configDir.resolve("runtime.yaml"))
      gov <- Governance.load(e.configDir.resolve("constraints.yaml"))
    yield (cfg, gov)

  /** The cycle with every detector. models.yaml is read each cycle, so an approval change needs no restart. */
  def cycle(e: MdrEnv, cfg: MdrConfig, gov: Governance, clock: () => Instant): Cycle =
    val source = PgSource(e.dbUrl, e.egressLog, e.listeners)
    val anchors = e.gatewayDataDir.resolve("anchors")
    val exportWindow = (from: Instant, until: Instant, out: java.nio.file.Path) =>
      BundleExport.write(out, from, until, None, e.dbUrl, Some(e.egressLog), e.listeners, anchors, e.keysDir, gov, e.app).map(_ => ())
    val incidents = Incidents(e.dbUrl, exportWindow, e.dataDir.resolve("incidents"))
    val webhook = e.webhookUrl.map(Webhook(_, Duration.ofSeconds(cfg.webhookTimeoutSeconds)))
    val detectors = Vector(Ghosts(cfg), ChainHealth(cfg, source, e.keysDir, anchors), InputAbuse(cfg),
      ConfigDrift(cfg, () => Templates.loadModels(e.configDir.resolve("models.yaml"), gov)), BlockRate(cfg),
      ReviewAge(cfg, e.configDir), Flags(), ComponentHealth())
    Cycle(source, detectors, AlertWriter(e.dbUrl, cfg, webhook, incidents), Cycle.pgHeartbeat(e.dbUrl), cfg, clock)

  private def report(r: CycleReport): Unit =
    println(s"CLAD_MDR_CYCLE window=${r.window.start}..${r.window.end} alerts=${r.alertsWritten} " +
      s"failed_detectors=${r.detectorFailures.mkString(",")} redelivered=${r.redelivered}")

  private def once(e: MdrEnv): Int =
    config(e).flatMap((cfg, gov) => cycle(e, cfg, gov, () => Instant.now()).once()).fold(fail, r => { report(r); 0 })

  /** Runs a cycle every `interval_seconds`. A failed cycle is logged and the next one runs; only a configuration that
    * does not load at start stops the worker. */
  private def loop(e: MdrEnv): Int =
    config(e) match
      case Left(err) => fail(err)
      case Right((cfg, gov)) =>
        val c = cycle(e, cfg, gov, () => Instant.now())
        val interval = Duration.ofSeconds(cfg.intervalSeconds)
        while true do
          c.once().fold(err => Console.err.println(s"CLAD_MDR_CYCLE_FAILED $err"), report)
          Thread.sleep(interval.toMillis)
        0

  /** Configuration loads, the store answers as the reader, the egress log is readable, and lockers can be written. */
  private def check(e: MdrEnv): Int =
    val problems = Vector(
      config(e).left.toOption,
      PgSource(e.dbUrl, e.egressLog, e.listeners).lastHeartbeat.left.toOption.map(err => s"store: $err"),
      Option.when(!Files.isReadable(e.egressLog))(s"egress log ${e.egressLog} is not readable"),
      Option.when(!Files.isWritable(e.dataDir))(s"data dir ${e.dataDir} is not writable (incident lockers)")
    ).flatten
    problems.foreach(p => Console.err.println(s"clad-mdr check: $p"))
    if problems.isEmpty then
      println("clad-mdr check: ok")
      0
    else 1
