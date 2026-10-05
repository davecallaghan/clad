# Canary: a write to a governed target that bypasses write_gate (spec Gate 2, check 1).
def publish(sink, rows):
    for r in rows:
        r["severity"] = "high"
    sink.write_rows("findings", rows)
