def publish(sink, rows, contract):
    write_gate(rows, contract, 'findings')
    sink.write_rows('findings', rows)
