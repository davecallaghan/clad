// code/core/src/main/scala/clad/core/Surface.scala
package clad.core

enum Surface:
  case Prompt, Input, Config, Output, Delivery

enum Governability:
  case Full, Partial, External

object Surface:
  val governability: Map[Surface, Governability] = Map(
    Surface.Prompt   -> Governability.Full,
    Surface.Input    -> Governability.Partial,
    Surface.Config   -> Governability.Partial,
    Surface.Output   -> Governability.Partial,
    Surface.Delivery -> Governability.Full
  )
