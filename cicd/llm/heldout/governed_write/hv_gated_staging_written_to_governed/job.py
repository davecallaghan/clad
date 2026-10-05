def publish(df, rows, contract):
    out = write_gate(rows, contract, 'findings_staging')
    df.write.saveAsTable('main.dd.findings')
    return out
