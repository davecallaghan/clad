-- Pandoc Lua filter: rasterize ```mermaid``` code blocks to PNG for EPUB/PDF.
-- On GitHub the same fenced blocks render natively; this filter only runs at
-- build time (pandoc), turning each block into an embedded image.
--
-- Requires: mmdc (mermaid-cli). Uses diagrams/puppeteer.json to locate a browser.

local counter = 0

function CodeBlock(el)
  if not el.classes:includes("mermaid") then
    return nil
  end

  counter = counter + 1
  os.execute("mkdir -p build/diagrams")

  local src = "build/diagrams/mermaid-" .. counter .. ".mmd"
  local png = "build/diagrams/mermaid-" .. counter .. ".png"

  local f = io.open(src, "w")
  f:write(el.text)
  f:close()

  local cmd = string.format(
    'mmdc -i %q -o %q -p build/puppeteer.json -b transparent -s 2 >/dev/null 2>&1',
    src, png)
  local ok = os.execute(cmd)

  if not ok then
    io.stderr:write("mermaid-filter: mmdc failed for block " .. counter .. "\n")
    return nil -- leave the code block as-is if rendering fails
  end

  local caption = el.attributes["caption"] or ""
  return pandoc.Para(pandoc.Image(caption, png, caption))
end
