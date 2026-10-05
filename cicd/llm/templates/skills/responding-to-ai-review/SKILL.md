---
name: responding-to-ai-review
description: Use when acting on findings from cross-architecture AI code review (two reviewer models, review comments on a PR, findings JSON) - deciding which findings to fix, rewriting code in response to review, or when the same rule has been flagged repeatedly.
---

# Responding to AI review

## The failure it prevents

Acting on hallucinated or uncorroborated findings, and arguing with a reviewer through repeated
rewrites. Reviewer output quality was the binding problem: the pipeline has a whole stage that
discards findings whose cited file, line or rule quotation does not exist, and only findings the
other reviewer corroborates are reported. An escalation ladder declares human intervention on the
third repeat of a rule within a PR, because the model did not self-correct on that class
(constraint-reversion, "Cross-architecture review, reported as an observation").

Checked by: `review_findings`.

## Procedure

1. Run `python -m cicd.llm.gates.review_findings --findings-dir <dir> --state <ladder.json>`.
2. Fix only the findings it reports: those that survived citation validation (file exists at the
   reviewed commit, line in range, quote on that line, rule ID and quoted rule text in
   `cicd/llm/rules.toml`) **and** were confirmed by the other reviewer.
3. Do not act on dropped or uncorroborated findings, and do not argue a finding through repeated
   rewrites.
4. If the output contains `ESCALATE_TO_HUMAN` for a rule (its third repeat in this PR), **stop**
   and hand the PR to a human. Do not attempt a fourth fix.
5. If config declares the reviewers independent, check the reported `family_overlap`: a reviewer
   sharing the author's model family is not independent ("The gate is half same-family").

## Examples (executable: the guidance tests run them through the real gate)

- [review_findings__good__uncorroborated_finding.json](examples/review_findings__good__uncorroborated_finding.json): nothing to act on.
- [review_findings__good__hallucinated_citation_dropped.json](examples/review_findings__good__hallucinated_citation_dropped.json): the quote is not on the cited line, so it is dropped.
- [review_findings__bad__corroborated_finding.json](examples/review_findings__bad__corroborated_finding.json): a valid, corroborated finding that must be fixed.

## Does not apply when

- A finding was dropped by validation or was not corroborated: do not act on it.
- There is no review input (`--findings-dir` absent): the gate is NOT_RUN, which is not a pass and
  not a review.
