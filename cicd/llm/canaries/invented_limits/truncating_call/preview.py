# Canary: .head/.limit/.take with a literal argument (spec Gate 1, flag 4).
def sample(df):
    return df.limit(1000)
