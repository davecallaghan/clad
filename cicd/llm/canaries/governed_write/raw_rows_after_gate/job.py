# Canary: the unchecked rows written after the gate (v2).
def f(sink, rows, c):
    checked = write_gate(rows, c, 'findings')
    sink.write_rows('findings', rows)
    return checked
