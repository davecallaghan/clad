package clad.gateway.http

import cats.effect.IO
import clad.gateway.pipeline.{Gateway, GatewayResult, GovernedRequest}
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.{Authorization, `Content-Type`}
import org.typelevel.ci.CIString
import scala.util.Try

/** The gateway's HTTP surface (spec section 6). A malformed body is always a 400 governance_rejected, never a 500. */
final class Routes(gateway: Gateway, clients: Clients):
  private val InteractionHeader = CIString("X-Clad-Interaction-Id")
  private val NotChatOptions = Set("model", "messages", "clad")

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "v1" / "health" => json(Status.Ok, ujson.Obj("status" -> "ok"))

    case req @ POST -> Root / "v1" / "chat" / "completions" =>
      withPrefix(req) { prefix =>
        req.as[String].flatMap { text =>
          parseChat(text) match
            case Left(why) => rejected(why)
            case Right(g) if !allowed(prefix, g.principal) => rejected("principal_not_allowed_for_token")
            case Right(g) => IO.blocking(gateway.complete(g)).flatMap(respond)
        }
      }

    case req @ POST -> "v1" /: "passthrough" /: rest if rest.segments.nonEmpty =>
      passthrough(req, rest)

    case req @ POST -> Root / "admin" / "contain" =>
      admin(req) { o =>
        for
          project <- str(o, "project")
          reason <- str(o, "reason")
          _ <- gateway.contain(project, reason)
        yield "contained"
      }

    case req @ POST -> Root / "admin" / "release" =>
      admin(req) { o =>
        for
          project <- str(o, "project")
          _ <- gateway.release(project)
        yield "released"
      }
  }

  private def passthrough(req: Request[IO], rest: Uri.Path): IO[Response[IO]] =
    rest.segments.map(_.decoded()).toList match
      case name :: tail =>
        withPrefix(req) { prefix =>
          def header(h: String) = req.headers.get(CIString(h)).map(_.head.value)
          (header("X-Clad-Template"), header("X-Clad-Principal")) match
            case (Some(template), Some(principal)) =>
              if !allowed(prefix, principal) then rejected("principal_not_allowed_for_token")
              else req.as[String].flatMap { body =>
                IO.blocking(gateway.passthrough(name, tail.mkString("/"), template, principal, header("X-Clad-Session"), body))
                  .flatMap(respondPassthrough)
              }
            case _ => rejected("X-Clad-Template and X-Clad-Principal headers are required")
        }
      case Nil => rejected("no passthrough upstream named")

  // authentication

  private def bearer(req: Request[IO]): Option[String] =
    req.headers.get[Authorization].collect { case Authorization(Credentials.Token(scheme, token)) if scheme.toString.equalsIgnoreCase("Bearer") => token }

  private def withPrefix(req: Request[IO])(f: String => IO[Response[IO]]): IO[Response[IO]] =
    bearer(req).flatMap(clients.prefixFor) match
      case Some(prefix) => f(prefix)
      case None         => unauthorized

  private def allowed(prefix: String, principal: String): Boolean =
    principal == prefix || principal.startsWith(prefix + ":")

  private def admin(req: Request[IO])(f: ujson.Obj => Either[String, String]): IO[Response[IO]] =
    if !bearer(req).exists(clients.isAdmin) then unauthorized
    else req.as[String].flatMap { text =>
      Try(ujson.read(text)).toEither.left.map(_ => "body is not JSON").flatMap {
        case o: ujson.Obj => Right(o)
        case _            => Left("body is not a JSON object")
      }.flatMap(f) match
        case Right(done) => json(Status.Ok, ujson.Obj("status" -> done))
        case Left(why)   => json(Status.BadRequest, error("admin_rejected", "reason" -> why))
    }

  // request parsing

  private def str(o: ujson.Obj, key: String): Either[String, String] = o.value.get(key) match
    case Some(ujson.Str(s)) => Right(s)
    case _                  => Left(s"$key is required and must be a string")

  private def optStr(o: ujson.Obj, key: String): Either[String, Option[String]] = o.value.get(key) match
    case None | Some(ujson.Null) => Right(None)
    case Some(ujson.Str(s))      => Right(Some(s))
    case _                       => Left(s"$key must be a string")

  private def parseChat(text: String): Either[String, GovernedRequest] =
    for
      root <- Try(ujson.read(text)).toEither.left.map(_ => "body is not JSON").flatMap {
        case o: ujson.Obj => Right(o)
        case _            => Left("body is not a JSON object")
      }
      clad <- root.value.get("clad") match
        case Some(o: ujson.Obj) => Right(o)
        case _                  => Left("the clad block is required")
      template <- str(clad, "template_id")
      principal <- str(clad, "principal")
      session <- optStr(clad, "session")
      tier <- optStr(clad, "model_tier")
      vars <- parseVars(clad)
      messages <- root.value.get("messages") match
        case Some(a: ujson.Arr) =>
          a.value.foldLeft[Either[String, Vector[ujson.Obj]]](Right(Vector.empty)) {
            case (acc, m: ujson.Obj) => acc.map(_ :+ m)
            case (_, _)              => Left("every message must be an object")
          }
        case _ => Left("messages is required and must be an array")
      evidence = clad.value.get("evidence").filter(_ != ujson.Null)
    yield GovernedRequest(template, principal, session, messages, vars, evidence, tier,
      ujson.Obj.from(root.value.toSeq.filterNot((k, _) => NotChatOptions.contains(k))))

  private def parseVars(clad: ujson.Obj): Either[String, Map[String, String]] = clad.value.get("vars") match
    case None | Some(ujson.Null) => Right(Map.empty)
    case Some(o: ujson.Obj) =>
      o.value.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) {
        case (acc, (k, ujson.Str(s))) => acc.map(_ + (k -> s))
        case (_, (k, _))              => Left(s"var $k must be a string")
      }
    case _ => Left("vars must be an object")

  // responses

  private def error(kind: String, fields: (String, ujson.Value)*): ujson.Obj =
    ujson.Obj("error" -> ujson.Obj.from(("type" -> ujson.Str(kind)) +: fields))

  private def json(status: Status, body: ujson.Value, extra: Headers = Headers.empty): IO[Response[IO]] =
    IO.pure(Response[IO](status).withEntity(ujson.write(body)).withContentType(`Content-Type`(MediaType.application.json)).putHeaders(extra.headers))

  private def iidHeader(id: String): Headers = Headers(Header.Raw(InteractionHeader, id))

  private def rejected(why: String): IO[Response[IO]] = json(Status.BadRequest, error("governance_rejected", "reason" -> why))
  private def unauthorized: IO[Response[IO]] =
    json(Status.Unauthorized, error("unauthorized", "reason" -> "a valid bearer token is required"))

  private def respond(r: GatewayResult): IO[Response[IO]] = r match
    case GatewayResult.Delivered(id, outcome, response) =>
      val clad = ujson.Obj("interaction_id" -> id, "outcome" -> outcome)
      val body = response match
        case o: ujson.Obj => ujson.Obj.from(o.value.toSeq :+ ("clad" -> clad))
        case other        => ujson.Obj("response" -> other, "clad" -> clad)
      json(Status.Ok, body, iidHeader(id))
    case other => nonDelivered(other)

  private def respondPassthrough(r: GatewayResult): IO[Response[IO]] = r match
    case GatewayResult.Delivered(id, _, response) =>
      val status = response.obj.get("status").map(_.num.toInt).flatMap(s => Status.fromInt(s).toOption)
      status match
        case Some(s) =>
          IO.pure(Response[IO](s).withEntity(response("body").str).withContentType(`Content-Type`(MediaType.application.json))
            .putHeaders(iidHeader(id).headers))
        case None => json(Status.BadGateway, error("unavailable", "interaction_id" -> id, "reason" -> "upstream returned an invalid status"), iidHeader(id))
    case other => nonDelivered(other)

  private def nonDelivered(r: GatewayResult): IO[Response[IO]] = r match
    case GatewayResult.Blocked(id, reason) =>
      json(Status.Forbidden, error("governance_block", "interaction_id" -> id, "reason" -> reason), iidHeader(id))
    case GatewayResult.Rejected(reason) => rejected(reason)
    case GatewayResult.Unavailable(id, reason) =>
      json(Status.ServiceUnavailable, error("unavailable", "reason" -> reason), id.fold(Headers.empty)(iidHeader))
    case GatewayResult.Delivered(id, _, _) => json(Status.InternalServerError, error("unavailable", "reason" -> "unexpected result"))
