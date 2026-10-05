def store(sink, rows, contract):
    sink.write_rows('findings', write_gate(rows, contract, 'findings').accepted)
