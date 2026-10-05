"""Bad: a conventional-looking cap chosen by the author. The job succeeds and silently drops every
document after the fortieth (constraint-reversion, "Bound reversion")."""

max_documents = 50


def summarise(documents):
    return documents[:40]
