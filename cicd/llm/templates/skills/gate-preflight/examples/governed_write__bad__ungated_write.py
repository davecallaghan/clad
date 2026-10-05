"""Bad: every surviving row is stamped "high" and written straight to the governed table
(constraint-reversion, "Grade reversion": seventy-five red flags, uniformly high)."""


def publish(sink, rows):
    flagged = [r for r in rows if r["severity"] == "high"]
    for r in flagged:
        r["severity"] = "high"
    sink.write_rows("findings", flagged)
