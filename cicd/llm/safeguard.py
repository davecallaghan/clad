"""The `@safeguard` marker.

Safeguards are identified in code, never by a list (spec Gate 6): a function decorated with
`@safeguard`, or a line carrying a `# safeguard` comment. The decorator changes nothing at
runtime; it exists so `safeguard_removal` can find what must not be deleted, wrapped,
short-circuited or given a default.
"""

from __future__ import annotations


def safeguard(fn):
    fn.__safeguard__ = True
    return fn
