namespace Clad

-- Risk tiers for output constraints (WP2 §4)
inductive RiskTier where
  | Critical
  | Standard
  | Low
  deriving DecidableEq, Repr

-- Pipeline decisions (WP2 §5)
inductive PipelineDecision where
  | Pass
  | Flag
  | Block
  deriving DecidableEq, Repr

-- Threshold policy: a score threshold that determines flagging
structure ThresholdPolicy where
  flagThreshold : Nat
  riskTier : RiskTier
  deriving DecidableEq

-- Threshold evaluation
def ThresholdPolicy.evaluate (policy : ThresholdPolicy) (score : Nat) : Bool :=
  score >= policy.flagThreshold

-- Pipeline decision logic (from OutputEvaluator.scala)
def computeDecision (hasCriticalFailure hasAnyFailure : Bool) : PipelineDecision :=
  if !hasAnyFailure then .Pass
  else if hasCriticalFailure then .Block
  else .Flag

-- Threshold monotonicity: higher score passes higher threshold → passes lower threshold
theorem threshold_monotone (tLow tHigh score : Nat)
    (h_order : tLow ≤ tHigh) (h_passes : tHigh ≤ score) :
    tLow ≤ score :=
  Nat.le_trans h_order h_passes

-- Evaluation determinism (trivially true — Lean functions are pure)
theorem evaluation_deterministic (policy : ThresholdPolicy) (score : Nat) :
    policy.evaluate score = policy.evaluate score := rfl

-- Critical failure always blocks
theorem critical_failure_blocks :
    computeDecision true true = .Block := rfl

-- No failure always passes
theorem no_failure_passes :
    computeDecision false false = .Pass := rfl

-- Non-critical failure flags
theorem non_critical_failure_flags :
    computeDecision false true = .Flag := rfl

-- Block implies there was a failure
theorem block_implies_failure (crit anyFail : Bool)
    (h : computeDecision crit anyFail = .Block) :
    anyFail = true := by
  simp [computeDecision] at h
  by_cases hf : anyFail <;> simp_all

-- Pass implies no failures
theorem pass_implies_no_failure (crit anyFail : Bool)
    (h : computeDecision crit anyFail = .Pass) :
    anyFail = false := by
  unfold computeDecision at h
  cases anyFail
  · rfl
  · cases crit <;> simp at h

-- Pipeline decision is exhaustive: every combination produces a valid decision
theorem decision_exhaustive (crit anyFail : Bool) :
    computeDecision crit anyFail = .Pass ∨
    computeDecision crit anyFail = .Flag ∨
    computeDecision crit anyFail = .Block := by
  cases crit <;> cases anyFail <;> simp [computeDecision]

end Clad
