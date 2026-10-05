# Installing the LLM gate guidance into a Databricks Asset Bundle repository

This template carries the guidance layer for the gates in `cicd/llm/`: a `CLAUDE.md`, the six
skills with their executable examples, and the Claude Code hooks. Guidance is the cheap first layer
and is expected to leak; the hooks run the real gates while the agent edits, and CI stays the final
authority. The gates themselves must be installed first.

## Placeholders

| Placeholder | Replace with |
|---|---|
| `{{PROJECT_NAME}}` | the repository's name, used as the `CLAUDE.md` title |
| `{{PROJECT_DESCRIPTION}}` | one or two sentences: what the bundle builds and for whom |

`instantiate.py` refuses to write any file that still contains a `{{PLACEHOLDER}}`.

## Steps

1. **Copy the kit.** Into the bundle repository's root, copy `cicd/lib/verdict.py` and the whole of
   `cicd/llm/` (gates, `core.py`, `governed.py`, `safeguard.py`, `preflight.*`, `hooks/`,
   `examples.py`, `mutation_check.py`, `freeze.py`, `canaries/`, `tests/`, `config.toml`,
   `rules.toml`, `contracts/`, `requirements.txt`), and `.github/workflows/llm-gates.yml`.
2. **Install the tools** used by the hooks and CI: `python -m pip install -r cicd/llm/requirements.txt`.
   If that interpreter is not the default `python3`, set `CICD_LLM_PYTHON` for Claude Code.
3. **Describe the governed values.** Replace `cicd/llm/contracts/governed_values.toml` with the
   bundle's own write targets (table names), their governed fields, domains, allowed origins and
   ungraded marker.
4. **Point the gates at the bundle.** In `cicd/llm/config.toml`:
   - `gates.invented_limits.include` already covers `databricks.yml` and `resources/**/*.yml`;
     add notebook source globs if the bundle keeps code elsewhere;
   - `gates.governed_write.writer_functions`: the calls that write to tables (`saveAsTable`,
     `insertInto` and any in-house writer);
   - `gates.behavioural_harness.entry`, `target` and `records`: the pipeline function that maps
     model output to stored rows, and fixture records for it. Remove the kit's
     `reference_pipeline.py` and `reference_records.json` once the entry points at real code;
   - leave every value you do not know unset; its sub-check reports NOT_RUN until someone supplies
     the value with a `# source:`.
5. **Instantiate the guidance:**

       python -m cicd.llm.templates.instantiate --target . \
           --project-name "<name>" --project-description "<what the bundle does>"

   It writes `CLAUDE.md` (never over an existing one), copies the skills to `.claude/skills/`, and
   merges the hooks into `.claude/settings.json`, printing the settings diff. Review that diff.
6. **Re-freeze and prove it.** Run `python -m pytest cicd/llm/tests`, `python cicd/llm/mutation_check.py`
   and `python -m cicd.llm.gates.liveness`, then `python -m cicd.llm.freeze`. The guidance tests
   (`cicd/llm/tests/test_guidance.py`) run every skill example through the real gates against the
   bundle's config.
7. **Make CI binding.** Add `llm-gates` to the default branch's required status checks: a gate that
   runs and reports but binds nothing is the third way controls died (constraint-reversion,
   "Group 3: wired, running, and binding nothing").
