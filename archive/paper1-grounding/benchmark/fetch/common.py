"""Shared HTTP helpers for the benchmark fetchers.

Politeness matters here: openFDA allows 240 requests/minute and 1000/day without
an API key, and DailyMed publishes no documented limit. Both are public services
funded by taxpayers, so the default is to go slowly and cache everything. No
fetcher in this directory ever hits the network twice for the same URL.
"""
import json, os, time, urllib.request, urllib.parse, hashlib, pathlib

RAW = pathlib.Path(__file__).resolve().parent.parent / "raw"
RAW.mkdir(exist_ok=True)
UA = "grounding-benchmark/0.1 (academic; contact mr.david.callaghan@gmail.com)"
_last = [0.0]
MIN_INTERVAL = 0.35          # ~170 req/min, comfortably under openFDA's 240


def _throttle():
    gap = time.time() - _last[0]
    if gap < MIN_INTERVAL:
        time.sleep(MIN_INTERVAL - gap)
    _last[0] = time.time()


def get(url, *, binary=False, cache=True):
    """GET with on-disk cache. Returns bytes if binary else str."""
    key = hashlib.sha256(url.encode()).hexdigest()[:20]
    path = RAW / (key + (".bin" if binary else ".txt"))
    if cache and path.exists():
        return path.read_bytes() if binary else path.read_text(encoding="utf-8")
    _throttle()
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    if cache:
        path.write_bytes(data)
        (RAW / (key + ".url")).write_text(url, encoding="utf-8")
    return data if binary else data.decode("utf-8", errors="replace")


def get_json(url):
    return json.loads(get(url))


def fda(endpoint, **params):
    """openFDA query. Caller supplies search/limit/count as keyword args."""
    q = urllib.parse.urlencode(params, quote_via=urllib.parse.quote)
    return get_json(f"https://api.fda.gov/drug/{endpoint}.json?{q}")
