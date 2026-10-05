# Canary: a limit-named parameter with a literal value (spec Gate 1, flag 1).
def summarise(documents, max_documents=25):
    return documents[:max_documents]


MAX_RETRIES = 3
