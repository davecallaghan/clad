package clad.core

opaque type Domain = String

object Domain:
  def apply(value: String): Domain = value

  extension (d: Domain)
    def value: String = d
