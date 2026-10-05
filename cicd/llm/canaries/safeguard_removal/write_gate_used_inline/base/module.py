def f(s, r, c):
    out = write_gate(r, c, 't')
    s.write_rows('t', out.accepted)
    s.write_rows('q', out.quarantined)
