import logging


def check(rows):
    if not verify_totals(rows):
        logging.error('totals failed')
