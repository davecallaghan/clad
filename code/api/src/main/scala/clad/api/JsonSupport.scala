package clad.api

import cats.effect.IO
import org.http4s.*
import org.http4s.headers.*
import upickle.default.*

object JsonSupport:
  given [A](using rw: Reader[A]): EntityDecoder[IO, A] =
    EntityDecoder.text[IO].map(json => read[A](json))

  given [A](using rw: Writer[A]): EntityEncoder[IO, A] =
    EntityEncoder.stringEncoder[IO]
      .contramap[A](a => write(a))
      .withContentType(`Content-Type`(MediaType.application.json))
