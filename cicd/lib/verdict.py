"""Four-valued gate verdicts.

Exists to catch: a check that could not decide being read as a pass. A boolean has no room
for "the property cannot be decided here" or "a required input was missing", so both collapse
into True or False and a silent gate becomes indistinguishable from a clean one
(constraint-escape, "Constraint semantics and significance sets": the verdict type raises on
Boolean coercion, so the inexpressible value cannot silently become a pass or a failure).

Exit codes: 0 SATISFIED, 1 VIOLATED, 2 INEXPRESSIBLE or NOT_RUN.
"""

from __future__ import annotations

import enum


class Verdict(enum.Enum):
    SATISFIED = "SATISFIED"
    VIOLATED = "VIOLATED"
    INEXPRESSIBLE = "INEXPRESSIBLE"  # the property cannot be decided at this point
    NOT_RUN = "NOT_RUN"  # a required input or config value is missing

    def __bool__(self) -> bool:
        raise TypeError(
            f"Verdict.{self.name} has no truth value: compare it to a Verdict member explicitly. "
            "Coercing a four-valued verdict to bool is how an undecided check becomes a pass."
        )

    @property
    def exit_code(self) -> int:
        if self is Verdict.SATISFIED:
            return 0
        if self is Verdict.VIOLATED:
            return 1
        return 2
