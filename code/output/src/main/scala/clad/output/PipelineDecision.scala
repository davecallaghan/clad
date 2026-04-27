package clad.output

enum PipelineDecision:
  case Pass
  case Flag(reasons: Vector[OutputEvalResult])
  case Block(reasons: Vector[OutputEvalResult])
