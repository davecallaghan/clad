package clad.core

enum Level:
  case Enterprise, Department, Project

object Level:
  // Enterprise (ordinal 0) < Department (ordinal 1) < Project (ordinal 2) in the Ordering,
  // matching the formal model's ≻ (Enterprise governs over the rest).
  given Ordering[Level] = Ordering.by((l: Level) => l.ordinal)

  extension (l: Level)
    def strictlyGoverns(other: Level): Boolean =
      summon[Ordering[Level]].lt(l, other)
    def governs(other: Level): Boolean =
      l == other || l.strictlyGoverns(other)
