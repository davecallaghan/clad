"""DailyMed: per-label version history and superseded label text.

This is the component openFDA cannot supply. openFDA serves exactly one record
per SPL set_id -- the current version -- so the valid-time and superseded-policy
case families are unbuildable from it alone. DailyMed keeps the history and will
serve the superseded text, which is what makes those families sourceable.
"""
import re, html
from common import get, get_json

HISTORY = "https://dailymed.nlm.nih.gov/dailymed/services/v2/spls/{sid}/history.json"
VERSION = "https://dailymed.nlm.nih.gov/dailymed/lookup.cfm?setid={sid}&version={v}"


def history(set_id):
    """[(version:int, published_date:str)] newest first, or [] if unavailable."""
    d = get_json(HISTORY.format(sid=set_id))
    out = []
    for h in (d.get("data") or {}).get("history", []):
        try:
            out.append((int(h["spl_version"]), h.get("published_date")))
        except (KeyError, TypeError, ValueError):
            continue
    return sorted(out, reverse=True)


def version_text(set_id, version):
    """Plain text of one label version. Returns '' when the page yields nothing."""
    raw = get(VERSION.format(sid=set_id, v=version))
    s = re.sub(r"(?is)<(script|style|head).*?</\1>", " ", raw)
    s = re.sub(r"<[^>]+>", " ", s)
    return " ".join(html.unescape(s).split())


def effective_time(text):
    """The SPL effective time as stamped on a superseded version page, if present.

    Worth extracting separately from DailyMed's published_date: the two are
    different quantities, and their difference is the transaction-time-versus-
    valid-time gap the paper argues is a distinct failure mode.
    """
    m = re.search(r"Effective Time:\s*(\d{8})", text)
    return m.group(1) if m else None
