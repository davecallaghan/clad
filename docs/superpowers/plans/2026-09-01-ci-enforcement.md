# CI Enforcement Implementation Plan (Plan 1 of 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the claims this project makes about itself into things a build either verifies or fails on.

**Architecture:** Four workflows replace the single one that deploys the landing page. Each is independently useful and independently reviewable: the application suite, the book build, the Lean proofs, and a job whose only purpose is to check the README's factual assertions. Task 1 also moves the Lean model into this repository, which collapses the three-way conformance problem to a single checkout.

**Tech Stack:** GitHub Actions (`ubuntu-latest`), `actions/setup-java@v4` (Temurin 11, sbt cached), `leanprover/lean-action@v1` with the mathlib cache, TeX Live via `apt` with the sixteen packages `book/main.tex` loads, `bash` for the check scripts.

**Spec:** `docs/superpowers/specs/2026-09-01-repo-structure-and-publishing-design.md` — this plan implements §4, plus the Lean relocation decided on 2026-09-01.

## Global Constraints

- **Runner:** `ubuntu-latest` on every job.
- **Java 11 / Temurin**, matching the local toolchain (`openjdk 11.0.30`). Scala is 3.3.8.
- **Match the existing workflow's conventions**, set by `.github/workflows/deploy-landing.yml`: a header comment stating any one-time setup, and *skip rather than fail* when an unconfigured prerequisite is missing. Never make a job fail for a reason the repository owner has not been told how to fix.
- **`paths:` filters on every workflow.** A book change must not run the Scala suite.
- **No secrets.** The Lean relocation exists to remove the only need for one.
- **TeX Live packages required by `book/main.tex`:** amsmath, amssymb, amsthm, array, booktabs, float, fontenc, geometry, graphicx, hyperref, lmodern, longtable, natbib, placeins, tikz, titlesec, xcolor.
- Commit after each task with the message given in that task's final step.

## A distinction this plan depends on

Grepping for `sorry` does **not** establish that proofs are machine-checked. A Lean file
with a type error contains no `sorry` and also does not compile. "69 machine-checked
theorems with zero `sorry`" is warranted only by a successful `lake build`.

Because the model depends on mathlib and eight transitive packages, a full build is
minutes even with the mathlib cache — too slow for every push. So the checks are split:

| check | cost | runs | establishes |
|---|---|---|---|
| no `sorry`, theorem count | seconds | every push | necessary conditions |
| `lake build` succeeds | minutes | nightly + `lean/**` changes | **that the proofs check** |

The README claim is licensed by the nightly job, not by the cheap one. Task 4 states this
in the README rather than leaving it implied.

---

### Task 1: Move the Lean model into this repository

The model is in a private repository under a different GitHub account
(`davecallaghan/aigos`, branch `main`, 28 `.lean` files). A public workflow here cannot
read it without a personal access token. Moving it in removes the secret, the SHA pin,
and one of the three artifacts that can drift.

**Files:**
- Create: `lean/` — copied from `~/prompt_approach/lean` (source is 116K; `.lake/` build output is excluded)
- Modify: `.gitignore`
- Modify: nothing — see Step 3

**Interfaces:**
- Consumes: nothing.
- Produces: `lean/` at the repository root, containing `Clad.lean`, `Clad/*.lean` (23 files), `lakefile.lean`, `lake-manifest.json`, `lean-toolchain`. **No source change is needed**: `DifferentialTestSpec`'s existing default, `../lean/.lake/build/bin/clad-difftest`, already resolves correctly once the directory exists — see Step 3.

- [ ] **Step 1: Copy the sources, excluding build output**

```bash
cd /Users/david.callaghan/clad
rsync -a --exclude '.lake/' --exclude '.git/' ~/prompt_approach/lean/ lean/
find lean -name '*.lean' | wc -l    # expect 24
test -f lean/lean-toolchain && cat lean/lean-toolchain   # expect leanprover/lean4:v4.30.0-rc2
```

- [ ] **Step 2: Ignore Lean build output**

Append to `.gitignore`:

```
# Lean build output and mathlib cache
lean/.lake/
```

- [ ] **Step 3: Confirm the existing path is already correct — do not change it**

`DifferentialTestSpec.scala:11-14` defaults to `../lean/.lake/build/bin/clad-difftest`.
That was previously diagnosed as a wrong path; it is not. `Test / fork` is `false`, so
tests run in the sbt JVM whose working directory is where sbt was launched — `code/`.
From there, `../lean` is the repository root's `lean/`.

`scripts/run-differential-test.sh` confirms the same layout independently: it builds in
`$REPO_ROOT/lean` and runs sbt from `$REPO_ROOT/code`.

So the differential test was never mis-pathed. It cancelled because `clad/lean/` did not
exist — the model was in a different repository. Step 1 fixes that outright.

```bash
cd /Users/david.callaghan/clad
grep -n 'leanExePath' code/difftest/src/test/scala/clad/difftest/DifferentialTestSpec.scala
cd code && sbt -batch "show difftest/Test/fork" 2>&1 | grep -E '^\[info\] (true|false)'; cd ..
```

Expected: the default path is `../lean/.lake/build/bin/clad-difftest`, and `fork` is
`false`. Make no edit to the file.

- [ ] **Step 4: Verify the path now resolves to a real directory**

```bash
  python3 -c "import os; print(os.path.abspath('../lean'), os.path.isdir('../lean'))" &&   cd ..
```

Expected: `/Users/david.callaghan/clad/lean True`. The binary itself does not exist until
`lake build` runs; only the directory must be present.

- [ ] **Step 5: Confirm the suite still compiles and the difftest still cancels**

Run: `cd code && sbt -batch "difftest/test"; cd ..`
Expected: PASS with `canceled 2`. The cancellation is still wrong, and Task 3 makes it a
failure — this step only confirms the move broke nothing.

- [ ] **Step 6: Commit**

```bash
git add lean .gitignore
git commit -m "feat(lean): move the formal model into this repository

The model was in a private repository under a different GitHub account, so a
public workflow here could not read it without a personal access token. Moving
it in removes the secret, removes the pinned SHA, and reduces the artifacts that
can drift from three to two.

24 .lean files, 69 distinct theorems and lemmas, zero sorry. Build output is
gitignored; the model depends on mathlib and eight transitive packages.

The differential test needed no change: its existing relative path was correct,
and it cancelled only because the directory was absent. Diagnosing it as a wrong
path was a mistake; the defect was a missing checkout."
```

---

### Task 2: `app.yml` — the application suite, failing on cancelled tests

The suite currently passes with `canceled 2` in the same run as `All tests passed`. That
is how the differential test has been inert since April without anyone noticing. One
configuration line closes it.

**Files:**
- Create: `.github/workflows/app.yml`
- Create: `tools/ci/assert-no-cancelled-tests.sh`

**Interfaces:**
- Consumes: nothing.
- Produces: `tools/ci/assert-no-cancelled-tests.sh <logfile>` — exits non-zero if the log records any cancelled test. Reused by Task 3.

- [ ] **Step 1: Write the assertion script**

Create `tools/ci/assert-no-cancelled-tests.sh`:

```bash
#!/usr/bin/env bash
# Fail if any test was cancelled.
#
# ScalaTest's `assume` cancels a test rather than failing it, and sbt then prints
# "All tests passed" in the same run as "canceled 2". The differential test
# against the Lean model was inert from April to September because of this.
# A skipped check is not a passing check.
set -euo pipefail

log="${1:?usage: assert-no-cancelled-tests.sh <sbt-log-file>}"

cancelled=$(grep -oE 'canceled [0-9]+' "$log" | grep -oE '[0-9]+' | paste -sd+ - | bc)
cancelled=${cancelled:-0}

if [ "$cancelled" -gt 0 ]; then
  echo "FAIL: $cancelled test(s) cancelled. A cancelled test is not a passing test."
  echo
  grep -nE 'canceled [1-9]|CANCELED|!!! CANCELED' "$log" | head -20
  exit 1
fi
echo "OK: no cancelled tests."
```

- [ ] **Step 2: Make it executable and verify it catches the current state**

```bash
chmod +x tools/ci/assert-no-cancelled-tests.sh
cd code && sbt -batch test > /tmp/sbt-baseline.log 2>&1; cd ..
bash tools/ci/assert-no-cancelled-tests.sh /tmp/sbt-baseline.log
```

Expected: **exit 1**, reporting 2 cancelled tests. That is the defect this workflow
exists to surface, and the script must detect it before the workflow is trusted.

- [ ] **Step 3: Write the workflow**

Create `.github/workflows/app.yml`:

```yaml
# The Scala application: compile, test, and fail on any cancelled test.
#
# A cancelled test is not a passing test. ScalaTest's `assume` cancels, and sbt
# prints "All tests passed" alongside "canceled 2" — which is how the
# differential test against the Lean model stayed inert for four months.
name: app

on:
  push:
    paths:
      - "code/**"
      - ".github/workflows/app.yml"
      - "tools/ci/assert-no-cancelled-tests.sh"
  pull_request:
    paths:
      - "code/**"
  workflow_dispatch:

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "11"
          cache: sbt

      - name: Compile and test
        working-directory: code
        run: sbt -batch test 2>&1 | tee ../sbt-test.log

      - name: Assert no cancelled tests
        run: bash tools/ci/assert-no-cancelled-tests.sh sbt-test.log

      - name: Upload the log
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: sbt-test-log
          path: sbt-test.log
```

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/app.yml tools/ci/assert-no-cancelled-tests.sh
git commit -m "ci: run the Scala suite and fail on cancelled tests

The suite prints 'All tests passed' in the same run as 'canceled 2'. The
differential test against the Lean model has been cancelling rather than running
since April, and nothing surfaced it.

app.yml runs sbt test and then asserts the log records no cancellations. This
workflow is expected to FAIL on its first run — the two cancelled differential
tests are real, and Task 3 fixes the cause."
```

---

### Task 3: `lean.yml` — prove the proofs actually check

Two jobs, split by cost. The cheap one runs on every push to `lean/`; the expensive one
runs nightly and on Lean changes, and is the only thing that licenses the phrase
"machine-checked".

**Files:**
- Create: `.github/workflows/lean.yml`
- Create: `tools/ci/lean-facts.sh`

**Interfaces:**
- Consumes: `lean/` from Task 1.
- Produces: `tools/ci/lean-facts.sh` — prints `theorems=<n>` and `sorry=<n>` to stdout, one per line, and exits non-zero if any `sorry` is present. Task 4 parses its output.

- [ ] **Step 1: Write the facts script**

Create `tools/ci/lean-facts.sh`:

```bash
#!/usr/bin/env bash
# Report the two facts the README asserts about the Lean model, and fail on sorry.
#
# NOTE: absence of `sorry` does NOT mean the proofs check. A file with a type
# error contains no `sorry` and does not compile either. Only `lake build`
# establishes that. This script reports necessary conditions cheaply; the
# lake-build job in lean.yml is what earns the word "machine-checked".
set -euo pipefail

cd "$(dirname "$0")/../../lean"

theorems=$(grep -rhoE '^[[:space:]]*(theorem|lemma) [A-Za-z_][A-Za-z0-9_'"'"']*' \
             --include='*.lean' . | awk '{print $2}' | sort -u | wc -l | tr -d ' ')
sorries=$(grep -rhow 'sorry' --include='*.lean' . | wc -l | tr -d ' ')

echo "theorems=$theorems"
echo "sorry=$sorries"

if [ "$sorries" -gt 0 ]; then
  echo "FAIL: $sorries occurrence(s) of sorry — the model has unfinished proofs." >&2
  grep -rnw 'sorry' --include='*.lean' . >&2
  exit 1
fi
```

- [ ] **Step 2: Run it and record the current facts**

```bash
chmod +x tools/ci/lean-facts.sh
bash tools/ci/lean-facts.sh
```

Expected exactly:

```
theorems=69
sorry=0
```

If the theorem count differs, that is the real number — use it in Task 4 rather than
adjusting the script.

- [ ] **Step 3: Write the workflow**

Create `.github/workflows/lean.yml`:

```yaml
# The formal model: cheap facts on every change, a real proof check nightly.
#
# Absence of `sorry` is necessary but not sufficient: a file with a type error
# has no `sorry` and does not compile. Only the lake-build job below licenses
# the claim that the theorems are machine-checked.
name: lean

on:
  push:
    paths:
      - "lean/**"
      - ".github/workflows/lean.yml"
      - "tools/ci/lean-facts.sh"
  pull_request:
    paths:
      - "lean/**"
  schedule:
    - cron: "37 4 * * *" # nightly, off the hour
  workflow_dispatch:

permissions:
  contents: read

jobs:
  facts:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Theorem count and sorry check
        run: bash tools/ci/lean-facts.sh

  proofs:
    # The expensive half. mathlib plus eight transitive packages, so this runs on
    # Lean changes and nightly rather than on every push to the repository.
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build and check the proofs
        uses: leanprover/lean-action@v1
        with:
          lake-package-directory: lean
          build-args: "--wfail"
          use-mathlib-cache: true

      - name: Confirm the difftest binary was produced
        run: test -x lean/.lake/build/bin/clad-difftest
```

`--wfail` turns Lean warnings into failures, so an `unused variable` or a deprecated
tactic cannot accumulate silently.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/lean.yml tools/ci/lean-facts.sh
git commit -m "ci: check the Lean model, cheaply on push and properly nightly

Two jobs, split by cost. The facts job greps the theorem count and fails on any
sorry, in seconds. The proofs job runs lake build with the mathlib cache and is
the only thing that licenses calling the theorems machine-checked — absence of
sorry does not establish that, since a file with a type error has no sorry and
does not compile either.

--wfail so Lean warnings cannot accumulate unnoticed."
```

---

### Task 4: `claims.yml` — verify what the README asserts

The README states that the framework includes **76** machine-checked theorems (the count
is 69), that the governance logic is "mathematically proven correct", and that it is
"independently verified against the production code" — where the differential test
cancels. A book arguing that assurance claims must be checkable cannot ship a README
whose own claims are unchecked.

**Files:**
- Modify: `README.md:42-58`
- Create: `.github/workflows/claims.yml`
- Create: `tools/ci/check-readme-claims.sh`

**Interfaces:**
- Consumes: `tools/ci/lean-facts.sh` from Task 3.
- Produces: `tools/ci/check-readme-claims.sh` — exits non-zero if the theorem count asserted in `README.md` disagrees with the model, or if the README contains a claim the repository cannot support.

- [ ] **Step 1: Correct the README**

Replace lines 42-44 of `README.md`:

```markdown
**Clad's governance logic is specified in Lean 4 and machine-checked.**

The model contains 69 theorems and lemmas with zero `sorry` — no unfinished
proofs — checked by `lake build` in CI. What this establishes is that the stated
properties hold *of the Lean model*. It is not a proof that the Scala
implementation is correct: that relationship is what the differential test
below is for, and its current status is reported in CI rather than asserted
here.
```

The phrases removed are load-bearing and must not survive: "mathematically proven
correct" (proofs of a model are not correctness of an implementation) and "independently
verified against the production code" (the differential test does not currently run).

- [ ] **Step 2: Correct the differential-testing paragraph**

In the same section, replace the sentence beginning "Following the AWS Cedar pattern"
through "Zero mismatches." with:

```markdown
**Differential testing.** Following the [AWS Cedar](https://www.amazon.science/publications/cedar-a-new-language-for-expressive-fast-safe-and-analyzable-authorization)
pattern, the Lean model includes an executable evaluator (`clad-difftest`) that is
compared against the Scala engine on 1,000 generated constraint hierarchies,
detection states and evaluation contexts. Cedar uses this methodology to check
its Rust authorization engine against a Lean specification.
```

The claim of zero mismatches is removed until the test runs in CI. It was true when
measured in April and has not been measured since.

- [ ] **Step 3: Write the claims script**

Create `tools/ci/check-readme-claims.sh`:

```bash
#!/usr/bin/env bash
# Verify the factual claims README.md makes about this repository.
#
# The book this repository accompanies argues that assurance claims must be
# checkable at the point of assertion. This script applies that to the README.
set -euo pipefail

cd "$(dirname "$0")/../.."

fail=0
note() { echo "FAIL: $*"; fail=1; }

# 1. The asserted theorem count must match the model.
actual=$(bash tools/ci/lean-facts.sh | grep '^theorems=' | cut -d= -f2)
asserted=$(grep -oE 'contains [0-9]+ theorems' README.md | grep -oE '[0-9]+' || true)
if [ -z "$asserted" ]; then
  note "README does not state a theorem count in the expected form ('contains N theorems')."
elif [ "$asserted" != "$actual" ]; then
  note "README asserts $asserted theorems; the model has $actual."
else
  echo "OK: theorem count $actual matches."
fi

# 2. Claims the repository cannot support must not appear.
while IFS='|' read -r phrase reason; do
  if grep -qiF "$phrase" README.md; then
    note "README contains \"$phrase\" — $reason"
  fi
done <<'PHRASES'
mathematically proven correct|proofs of a Lean model do not establish correctness of the Scala implementation
independently verified against the production code|the differential test's status is reported by CI, not asserted here
zero mismatches|only true of a run; assert it in CI output, not in prose
PHRASES

[ "$fail" -eq 0 ] && echo "All README claims check out." || exit 1
```

- [ ] **Step 4: Run it, expecting failures before the edits land**

```bash
chmod +x tools/ci/check-readme-claims.sh
git stash push README.md   # restore the original wording
bash tools/ci/check-readme-claims.sh || echo "(expected: failures)"
git stash pop
bash tools/ci/check-readme-claims.sh
```

Expected: the first run reports the count mismatch and all three forbidden phrases; the
second, after the Step 1-2 edits, prints `All README claims check out.`

- [ ] **Step 5: Write the workflow**

Create `.github/workflows/claims.yml`:

```yaml
# Verify the factual claims README.md makes about this repository.
#
# The book this repository accompanies argues that an assurance claim must be
# checkable at the point it is asserted. This applies that to our own README.
name: claims

on:
  push:
    paths:
      - "README.md"
      - "lean/**"
      - "tools/ci/check-readme-claims.sh"
      - ".github/workflows/claims.yml"
  pull_request:
    paths:
      - "README.md"
      - "lean/**"
  schedule:
    - cron: "23 5 * * *"
  workflow_dispatch:

permissions:
  contents: read

jobs:
  claims:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Check README claims
        run: bash tools/ci/check-readme-claims.sh
```

- [ ] **Step 6: Commit**

```bash
git add README.md .github/workflows/claims.yml tools/ci/check-readme-claims.sh
git commit -m "ci: verify the README's own claims, and correct them

The README asserted 76 machine-checked theorems (the count is 69), that the
governance logic is 'mathematically proven correct', and that it is
'independently verified against the production code' — where the differential
test silently cancels because of a path that does not resolve.

The claims are now accurate: 69 theorems, zero sorry, checked by lake build,
establishing properties of the Lean model and explicitly not correctness of the
Scala. The zero-mismatch claim is removed until the differential test runs in CI.

claims.yml checks the asserted count against the model and fails if any of three
unsupportable phrases reappears. A book arguing that assurance claims must be
checkable cannot ship a README whose claims are not."
```

---

### Task 5: `book.yml` — the manuscript builds clean

**Files:**
- Create: `.github/workflows/book.yml`
- Create: `tools/ci/assert-book-clean.sh`

**Interfaces:**
- Consumes: `tools/rebuild-book.sh`.
- Produces: `tools/ci/assert-book-clean.sh <logfile> <max-overfull-pt>` — exits non-zero on LaTeX errors, undefined references, multiply-defined labels, or an overfull box wider than the threshold.

- [ ] **Step 1: Write the assertion script**

Create `tools/ci/assert-book-clean.sh`:

```bash
#!/usr/bin/env bash
# Fail on LaTeX errors, dangling references, or a badly overfull box.
#
# grep -a throughout: main.log contains bytes that make grep treat it as binary,
# which silently produces no output and would make every check pass.
set -euo pipefail

log="${1:?usage: assert-book-clean.sh <main.log> <max-overfull-pt>}"
maxpt="${2:-40}"

fail=0
errors=$(grep -a -cE '^!' "$log" || true)
undef=$(grep -a -c 'undefined' "$log" || true)
multi=$(grep -a -c 'multiply defined' "$log" || true)
worst=$(grep -a -oE 'Overfull \\hbox \([0-9.]+pt' "$log" | grep -oE '[0-9.]+' | sort -rn | head -1 || echo 0)
worst=${worst:-0}

echo "errors=$errors undefined=$undef multiply-defined=$multi worst-overfull=${worst}pt"

[ "$errors" -eq 0 ] || { echo "FAIL: $errors LaTeX error(s)"; grep -a -E '^!' -A3 "$log" | head -30; fail=1; }
[ "$undef" -eq 0 ] || { echo "FAIL: $undef undefined reference(s)"; grep -a 'undefined' "$log" | head -10; fail=1; }
[ "$multi" -eq 0 ] || { echo "FAIL: $multi multiply-defined label(s)"; fail=1; }
awk -v w="$worst" -v m="$maxpt" 'BEGIN { exit !(w > m) }' \
  && { echo "FAIL: worst overfull box ${worst}pt exceeds ${maxpt}pt"; fail=1; }

exit "$fail"
```

- [ ] **Step 2: Verify it passes on the current book**

```bash
chmod +x tools/ci/assert-book-clean.sh
bash tools/rebuild-book.sh
bash tools/ci/assert-book-clean.sh book/build/main.log 40
```

Expected: `errors=0 undefined=0 multiply-defined=0 worst-overfull=35.71124pt`, exit 0.
The threshold of 40pt is set just above the current worst box, so any regression fails.

- [ ] **Step 3: Write the workflow**

Create `.github/workflows/book.yml`:

```yaml
# Build the book and fail on LaTeX errors, dangling references, or a badly
# overfull box. The threshold is set just above the current worst box, so
# regressions fail rather than accumulate.
name: book

on:
  push:
    paths:
      - "book/**"
      - "research/**"
      - "tools/md2tex.py"
      - "tools/rebuild-book.sh"
      - "tools/ci/assert-book-clean.sh"
      - ".github/workflows/book.yml"
  pull_request:
    paths:
      - "book/**"
      - "research/**"
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Install TeX Live
        run: |
          sudo apt-get update
          sudo apt-get install -y --no-install-recommends \
            texlive-latex-recommended texlive-latex-extra texlive-fonts-recommended \
            texlive-science texlive-pictures latexmk

      - name: Build the book
        run: bash tools/rebuild-book.sh

      - name: Assert the build is clean
        run: bash tools/ci/assert-book-clean.sh book/build/main.log 40

      - name: Upload the PDF
        uses: actions/upload-artifact@v4
        with:
          name: clad-book-pdf
          path: book/build/main.pdf
```

`texlive-science` provides amsthm; `texlive-pictures` provides tikz; `texlive-latex-extra`
provides booktabs, placeins, titlesec and float.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/book.yml tools/ci/assert-book-clean.sh
git commit -m "ci: build the book and fail on errors, dangling refs, or bad boxes

The manuscript builds clean locally — 0 errors, 0 undefined references, worst
overfull box 35.7pt — and nothing enforced that. The overfull threshold is 40pt,
just above the current worst, so a regression fails rather than accumulating.

The script greps with -a throughout: main.log contains bytes that make grep treat
it as binary, which silently produces no output and would make every check pass."
```

---

## What this plan does not do

- **`conformance.yml` is not here.** The three-way manifest needs the extractors and the
  `@Conforms` annotation from the earlier conformance spec, which is its own plan. Task 1
  removes that plan's hardest constraint by putting the Lean model in this repository.
- **The differential test still cancels.** Task 1 fixes the path; the binary only exists
  after `lake build` runs. Making the difftest a hard requirement belongs with
  `conformance.yml`, once CI can build Lean and Scala in the same job.
- **No directory moves.** `code/` is not renamed to `app/` here — that is plan 2, and
  doing it in the same change as the workflows would make every path in every workflow
  churn twice.
