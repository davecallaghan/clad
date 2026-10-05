"""Good: the batch size is read from config, where it carries a `# source:`; nothing is chosen here.

It is a splitter (raising it changes the number of calls, not what is seen), and the loop still
processes every row: the last slice takes whatever remains.
"""


def batches(rows, settings):
    size = settings["ingest"]["batch_size"]
    for start in range(0, len(rows), size):
        yield rows[start:start + size]
