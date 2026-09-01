#!/usr/bin/env bash
# Render the text-based cover (cover/cover.html) to cover/cover.png.
# Run this only when the cover design changes; the resulting PNG is committed and
# consumed by scripts/build-book.sh, so the routine EPUB build needs no browser.
#
# Requires a Chromium-based browser. Provisions a project-local
# chrome-headless-shell if none is found.

set -euo pipefail
cd "$(dirname "$0")/.."

export PUPPETEER_CACHE_DIR="$PWD/build/.chromium"
find_browser() {
  find "$PUPPETEER_CACHE_DIR/chrome-headless-shell" \
    -type f -name chrome-headless-shell 2>/dev/null | head -1
}
BROWSER="$(find_browser)"
if [[ -z "$BROWSER" ]]; then
  echo "Provisioning chrome-headless-shell ..."
  npx --yes puppeteer browsers install chrome-headless-shell >/dev/null
  BROWSER="$(find_browser)"
fi

"$BROWSER" --headless --no-sandbox --hide-scrollbars \
  --force-device-scale-factor=1 --window-size=1600,2400 \
  --default-background-color=fff5f7fa \
  --screenshot="$PWD/cover/cover.png" \
  "file://$PWD/cover/cover.html" >/dev/null 2>&1

echo "Rendered cover/cover.png"
