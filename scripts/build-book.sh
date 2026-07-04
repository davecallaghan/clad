#!/usr/bin/env bash
# Build the Trust by Design EPUB.
#
# Requirements:
#   - pandoc        (brew install pandoc)
#
# Diagrams are authored as inline ```mermaid``` fenced blocks in the markdown so
# GitHub renders them natively. The EPUB intentionally carries no images:
# scripts/mermaid-filter.lua strips the mermaid blocks at build time (they read
# poorly on e-reader pages and the surrounding prose already describes them).

set -euo pipefail
cd "$(dirname "$0")/.."

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

# Title/author/rights come from scripts/epub-metadata.xml (via --epub-metadata)
# rather than --metadata title=..., which avoids pandoc emitting a standalone
# title page that Apple Books renders as a blank leading page. The book opens
# directly on the README title instead.
pandoc "${FILES[@]}" \
  --lua-filter scripts/mermaid-filter.lua \
  --css scripts/epub.css \
  --epub-metadata scripts/epub-metadata.xml \
  -o "$OUT" \
  --toc --toc-depth=2

echo "Built $OUT"
