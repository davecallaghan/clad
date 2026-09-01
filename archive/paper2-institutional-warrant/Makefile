# arXiv preprint build.  Recipe lines use real tabs.
#
# This directory is self-contained.  Papers 1-3 share no build files, so this
# Makefile is duplicated in each of them by design; a change here is not
# picked up by the others.
MAIN    := main
OUT     := build
TARBALL := arxiv-$(MAIN).tar.gz

.PHONY: all watch arxiv check clean

all:
	latexmk -pdf -outdir=$(OUT) $(MAIN).tex

# Continuous rebuild; LaTeX Workshop's build-on-save covers the same ground.
watch:
	latexmk -pdf -pvc -outdir=$(OUT) $(MAIN).tex

# Assemble the upload.  arXiv needs the source, the .bbl named to match the
# main .tex, and figures already in pdf/png/jpg.  Hidden files are stripped
# (they get deleted on announcement) and COPYFILE_DISABLE keeps macOS from
# smuggling ._AppleDouble entries into the tarball.
#
# The .bbl copy is conditional: this draft cites nothing yet, so bibtex
# produces no .bbl and an unconditional cp would fail the build.
arxiv: all
	@if [ -f $(OUT)/$(MAIN).bbl ]; then \
	    cp $(OUT)/$(MAIN).bbl ./$(MAIN).bbl; \
	    echo "included $(MAIN).bbl"; \
	else \
	    echo "NOTE: no .bbl (nothing cited yet) -- add one before submitting"; \
	fi
	COPYFILE_DISABLE=1 tar --exclude='.*' --exclude='*.tar.gz' -czf $(TARBALL) \
	    $(MAIN).tex macros.tex $(wildcard $(MAIN).bbl) $(wildcard *.sty) \
	    sections figs
	@echo "--- $(TARBALL) contents ---"
	@tar -tzf $(TARBALL)
	@echo "--- size ---"
	@du -h $(TARBALL)

# Type 3 (bitmap) fonts render badly on arXiv; everything should be Type 1 or
# TrueType and embedded.  pdffonts needs poppler: brew install poppler
check: all
	@command -v pdffonts >/dev/null && pdffonts $(OUT)/$(MAIN).pdf \
	    || echo "pdffonts not found (brew install poppler)"
	@echo "--- overfull boxes (MUST be 0: text in the margin) ---"
	@grep -c 'Overfull' $(OUT)/$(MAIN).log || true
	@echo "--- underfull boxes (loose lines; should be 0) ---"
	@grep -c 'Underfull' $(OUT)/$(MAIN).log || true

clean:
	latexmk -C -outdir=$(OUT) $(MAIN).tex
	rm -f $(MAIN).bbl $(TARBALL)
