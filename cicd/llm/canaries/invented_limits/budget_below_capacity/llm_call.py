# Canary: an evidence budget below the model's capacity (spec Gate 1, flag 6).
def ask(client, prompt):
    return client.complete(prompt, max_tokens=2000)
