def publish(df):
    df.write.mode('append').insertInto('main.diligence.findings')
