---
name: touching-safeguards
description: Use before editing or deleting code that contains a @safeguard function, a "# safeguard" line, a canary call, an exception handler (try/except), an assertion or a test, before marking a test skip or xfail, and whenever a safeguard or check is blocking you.
---

# Touching safeguards

## The failure it prevents

A safeguard removed or neutered. The AI reviewer caught all three faults that added a prohibited
construct and missed all three that removed or neutered a safeguard: a swallowed exception, a
deleted canary call, a discarded quarantine list. Removal is how a control dies, and review notices
what appears rather than what disappears (constraint-reversion, "Why review does not catch a control
dying"). An automated fix once disabled a guard written to prove guards are not disabled (Group 1,
item 4).

Checked by: `safeguard_removal`, `ruff_profile`.

## Procedure

1. Never delete, wrap, short-circuit or broaden a safeguard or its exception handling to make
   something pass.
2. If a safeguard blocks you, **stop and report it** with the safeguard's name, file and line, and
   what you were trying to do. Do not work around it.
3. `safeguard_removal` (diff against a base) rejects:
   - a deleted call to a `@safeguard` function, a `# safeguard` line, or a call matching `*canary*`;
   - a handler broadened to `Exception`, `BaseException` or a bare `except:`;
   - a handler body reduced to `pass` or `continue`, including logging and then continuing;
   - a new `try` around a safeguard call;
   - a safeguard parameter, or any `contract` parameter, gaining a default;
   - the result of `write_gate` discarded, bound to `_`, or used inline (`write_gate(...).accepted`);
   - a safeguard call made unreachable (`if False:`, or after a `return`/`raise`/`continue`/`break`);
   - a new conditional early exit before a safeguard call (`if FLAG: return rows`);
   - a deleted assertion, or a test newly marked skip or xfail.
4. `ruff_profile` rejects (with `--ignore-noqa`, so a `# noqa` does not hide them): S110
   try-except-pass (including typed handlers), BLE001 blind `except Exception`, E722 bare `except:`,
   PLR2004 magic-value comparisons, C901 complexity above the ratchet in `cicd/llm/config.toml`,
   and (checked by the gate itself, not ruff) `contextlib.suppress(...)`.
5. Catch the specific exception you can handle and re-raise with context; let anything else propagate.
6. Run `cicd/llm/preflight.sh <files>` before you continue.

## Examples (executable: the guidance tests run them through the real gates)

- [safeguard_removal__good__narrowed_handler.json](examples/safeguard_removal__good__narrowed_handler.json) / [safeguard_removal__bad__swallowed_exception.json](examples/safeguard_removal__bad__swallowed_exception.json) / [safeguard_removal__bad__deleted_canary_call.json](examples/safeguard_removal__bad__deleted_canary_call.json) (each is a base/head pair)
- [ruff_profile__good__specific_handler.py](examples/ruff_profile__good__specific_handler.py) / [ruff_profile__bad__swallowed_exception.py](examples/ruff_profile__bad__swallowed_exception.py)

## Does not apply when

- You add a safeguard, an assertion or a canary call.
- You narrow a handler (for example `except Exception` to `except FileNotFoundError`).
- You make an edit that leaves every safeguard call, marker, assertion and handler in place.
- The file is not Python, or is excluded in `cicd/llm/config.toml`.
