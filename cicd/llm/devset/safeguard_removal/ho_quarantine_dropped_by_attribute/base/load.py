def store(sink, rows, contract):
    outcome = write_gate(rows, contract, 'findings')
    sink.write_rows('findings', outcome.accepted)
    sink.write_rows('quarantine', outcome.quarantined)
