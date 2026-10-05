def f(s, r, c):
    s.write_rows('t', write_gate(r, c, 't').accepted)
