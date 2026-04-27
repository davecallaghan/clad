package clad.core

opaque type PropertyId = String

object PropertyId:
  def apply(value: String): Either[InvalidProperty, PropertyId] =
    if isValid(value) then Right(value) else Left(InvalidProperty(value))

  def unsafe(value: String): PropertyId = value

  private def isValid(s: String): Boolean =
    s.nonEmpty && s.matches("[a-z][a-z0-9_]*")

  extension (p: PropertyId)
    def value: String = p

case class InvalidProperty(value: String)
