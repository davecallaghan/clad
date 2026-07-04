#!/usr/bin/env bash
# Build the Trust by Design EPUB, rasterizing inline ```mermaid``` diagrams.
#
# Requirements:
#   - pandoc        (brew install pandoc)
#   - mmdc          (brew install mermaid-cli)
#   - a Chromium-based browser for mmdc (Google Chrome, Chromium, or Edge)
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

# Locate a browser for mermaid-cli and generate its puppeteer config.
BROWSER=""
for candidate in \
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  "/Applications/Chromium.app/Contents/MacOS/Chromium" \
  "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge" \
  "$(command -v google-chrome || true)" \
  "$(command -v chromium || true)"; do
  if [[ -n "$candidate" && -x "$candidate" ]]; then BROWSER="$candidate"; break; fi
done

if [[ -z "$BROWSER" ]]; then
  echo "ERROR: no Chromium-based browser found for mermaid-cli." >&2
  echo "Install Google Chrome, or run: npx puppeteer browsers install chrome-headless-shell" >&2
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
