package clad.gateway.config

import java.nio.file.Path

/** Turns any read or shape failure of a config file into a Left carrying the path and the message. */
private[config] object Loading:
  def guard[A](path: Path)(body: => Either[String, A]): Either[String, A] =
    try body
    catch
      case e: java.io.IOException => Left(s"$path: ${e.getMessage}")
      case e: RuntimeException    => Left(s"$path: ${e.getClass.getSimpleName}: ${e.getMessage}")
