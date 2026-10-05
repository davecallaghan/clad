from cicd.llm.safeguard import safeguard


@safeguard
def check_rows(rows):
    if not rows:
        raise ValueError("no rows")
    return rows
