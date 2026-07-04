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

pandoc "${FILES[@]}" \
  --lua-filter scripts/mermaid-filter.lua \
  --css scripts/epub.css \
  -o "$OUT" \
  --metadata title="$TITLE" \
  --metadata author="$AUTHOR" \
  --metadata rights="Code: MIT; Research & Docs: CC BY 4.0" \
  --toc --toc-depth=2

echo "Built $OUT"
