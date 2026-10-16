package clad.conformance

/** One verdict per requirement. NotRun (a required input is missing) and Inexpressible (the
  * property cannot be decided from this kind of evidence) are not passes. */
enum Verdict:
  case Satisfied, Violated, NotRun, Inexpressible
