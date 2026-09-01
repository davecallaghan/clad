-- Strip ```mermaid``` code blocks from the EPUB/print build.
--
-- Diagrams are authored as inline mermaid blocks so GitHub renders them
-- natively. The EPUB deliberately carries no images (rasterized diagrams read
-- poorly on reflowable e-reader pages and caused blank-page runs), so we drop
-- the blocks here. The surrounding prose already describes each diagram.

function CodeBlock(el)
  if el.classes:includes("mermaid") then
    return {} -- remove the block entirely
  end
end
