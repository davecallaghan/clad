package clad.api

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import clad.config.*
import clad.runtime.*
import java.nio.file.Paths
import java.time.Instant

object GovernanceServer extends IOApp.Simple:
  def run: IO[Unit] =
    val configPath = Paths.get(sys.env.getOrElse("CLAD_CONFIG_PATH", "./governance.json"))
    val host = Host.fromString(sys.env.getOrElse("CLAD_HOST", "0.0.0.0")).getOrElse(host"0.0.0.0")
    val port = Port.fromInt(sys.env.getOrElse("CLAD_PORT", "8080").toInt).getOrElse(port"8080")

    for
      config <- IO.fromEither(ConfigLoader.loadFromFile(configPath).left.map(errs => RuntimeException(s"Config: ${errs.mkString(", ")}")))
      engine <- IO.fromEither(ConfigLoader.buildEngine(config).left.map(errs => RuntimeException(s"Engine: ${errs.mkString(", ")}")))
      engineRef <- Ref[IO].of(engine)
      configRef <- Ref[IO].of(config)
      routes = GovernanceRoutes(engineRef, configRef, configPath, Instant.now())
      _ <- IO.println(s"Clad Governance API on $host:$port")
      _ <- EmberServerBuilder.default[IO].withHost(host).withPort(port)
        .withHttpApp(Router("/" -> routes.routes).orNotFound).build.useForever
    yield ()
