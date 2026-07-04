#!/usr/bin/env bash
# Build the Trust by Design EPUB, rasterizing inline ```mermaid``` diagrams.
#
# Requirements:
#   - pandoc        (brew install pandoc)
#   - mmdc          (brew install mermaid-cli)
#   - npx           (bundled with Node) — used to provision a headless browser
#
# The build provisions its own project-local chrome-headless-shell (into
# build/.chromium) so it is self-contained and CI-ready — it does not depend on
# a system browser install.
#
# Diagrams are authored as inline ```mermaid``` fenced blocks in the markdown
# (which GitHub renders natively). scripts/mermaid-filter.lua renders them to
# PNG at build time so they embed in the EPUB.

set -euo pipefail
cd "$(dirname "$0")/.."

TITLE="Trust by Design: Governing Enterprise AI with Clad"
AUTHOR="David Callaghan"
OUT="build/trust-by-design.epub"

FILES=(
  README.md
  research/meta-framework.md
  research/wp1-enterprise-prompt-governance.md
  research/wp2-runtime-output-controls.md
  research/sa-monitoring-detection-response.md
  research/regulatory-mapping-appendix.md
)

mkdir -p build

# Provision a project-local headless browser for mermaid-cli (idempotent).
export PUPPETEER_CACHE_DIR="$PWD/build/.chromium"
find_browser() {
  find "$PUPPETEER_CACHE_DIR/chrome-headless-shell" \
    -type f -name chrome-headless-shell 2>/dev/null | head -1
}
BROWSER="$(find_browser)"
if [[ -z "$BROWSER" ]]; then
  echo "Provisioning chrome-headless-shell into $PUPPETEER_CACHE_DIR ..."
  npx --yes puppeteer browsers install chrome-headless-shell >/dev/null
  BROWSER="$(find_browser)"
fi

if [[ -z "$BROWSER" || ! -x "$BROWSER" ]]; then
  echo "ERROR: could not provision a headless browser for mermaid-cli." >&2
  exit 1
fi

cat > build/puppeteer.json <<EOF
{ "executablePath": "$BROWSER", "args": ["--no-sandbox"] }
EOF

echo "Using browser: $BROWSER"
pandoc "${FILES[@]}" \
  --lua-filter scripts/mermaid-filter.lua \
  -o "$OUT" \
  --metadata title="$TITLE" \
  --metadata author="$AUTHOR" \
  --metadata rights="Code: MIT; Research & Docs: CC BY 4.0" \
  --toc --toc-depth=2

echo "Built $OUT"
