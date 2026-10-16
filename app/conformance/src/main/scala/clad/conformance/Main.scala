package clad.conformance

import clad.conformance.probes.GhostProbe
import java.nio.file.{Files, Path}

object Main:
  private def arg(args: Array[String], name: String): Option[String] =
    args.sliding(2).collectFirst { case Array(`name`, v) => v }

  def main(args: Array[String]): Unit =
    val code = args.headOption match
      case Some("run") => runCmd(args)
      case Some("probe") => probeCmd(args)
      case _ =>
        System.err.println("usage: run --bundle <dir> --out <dir> [--probe-config <file>] [--probe-results <dir>]\n       probe --probe-config <file> --out <dir>")
        2
    sys.exit(code)

  private def runCmd(args: Array[String]): Int =
    (arg(args, "--bundle"), arg(args, "--out")) match
      case (Some(bundle), Some(out)) =>
        val probe = arg(args, "--probe-config").map(p => ProbeConfig.read(Path.of(p)))
        probe match
          case Some(Left(err)) => System.err.println(err); 2
          case _ =>
            BundleReader.read(Path.of(bundle)) match
              case Left(err) => System.err.println(err); 2
              case Right(b) =>
                val rs = Conformance.evaluate(b, probe.flatMap(_.toOption), arg(args, "--probe-results").map(Path.of(_)))
                val o = Path.of(out)
                Files.createDirectories(o)
                Files.writeString(o.resolve("scorecard.json"), Scorecard.json(b, Path.of(bundle), rs).render(indent = 2))
                Files.writeString(o.resolve("scorecard.md"), Scorecard.markdown(b, rs))
                println(Scorecard.markdown(b, rs))
                Scorecard.exitCode(rs)
      case _ => System.err.println("run needs --bundle and --out"); 2

  private def probeCmd(args: Array[String]): Int =
    (arg(args, "--probe-config"), arg(args, "--out")) match
      case (Some(cfg), Some(out)) =>
        ProbeConfig.read(Path.of(cfg)).flatMap(c => c.ghost.toRight("probe config has no ghost section"))
          .flatMap(g => GhostProbe.send(g, Path.of(out))) match
          case Left(err) => System.err.println(err); 2
          case Right(()) => println(s"wrote ${Path.of(out).resolve("probe_ghost.json")}"); 0
      case _ => System.err.println("probe needs --probe-config and --out"); 2
