set -e
cd /Users/david.callaghan/clad
python3 - <<'PY'
import subprocess, re
# Every markdown-derived file is regenerated from source; the two hand-written
# additions (meta-framework's S15, mdr's tikz figure) are spliced back in.
jobs = [('meta-framework','framework','book/chapters/meta-framework.tex'),
        ('wp1-enterprise-prompt-governance','prompt','book/chapters/wp1-prompt-governance.tex'),
        ('wp2-runtime-output-controls','controls','book/chapters/wp2-output-controls.tex'),
        ('sa-monitoring-detection-response','monitoring','book/chapters/mdr-monitoring.tex'),
        ('appendix-a-formal-model','formal-model','book/appendices/appendix-formal-model.tex'),
        ('appendix-b-worked-examples','clad-examples','book/appendices/appendix-clad-examples.tex'),
        ('appendix-c-templates','templates','book/appendices/appendix-templates.tex'),
        ('regulatory-mapping-appendix','regmap','book/appendices/appendix-regmap.tex'),
        ('appendix-theorems','theorems','book/appendices/appendix-theorems.tex'),
        ('glossary','glossary','book/appendices/appendix-glossary.tex')]
fig = re.search(r'\\begin\{figure\}\[ht\].*?\\end\{figure\}',
                open('book/chapters/mdr-monitoring.tex').read(), re.S).group(0)
s15 = open('book/chapters/_evidence-surface.frag.tex').read()
for src, lbl, out in jobs:
    r = subprocess.run(['python3','tools/md2tex.py',f'research/{src}.md',lbl],
                       capture_output=True, text=True)
    assert r.returncode == 0, f'{src}: {r.stderr[-400:]}'
    t = r.stdout
    if src == 'meta-framework':
        t = t.rstrip() + '\n\n' + s15
    if src == 'sa-monitoring-detection-response':
        mk = [l for l in t.split('\n') if l.startswith('% DIAGRAM')][0]
        t = t.replace(mk, fig)
    open(out,'w').write(t)
print('  9 files regenerated; both hand-written additions preserved')
PY
cd book
for i in 1 2 3; do latexmk -pdf -interaction=nonstopmode -outdir=build main.tex >/dev/null 2>&1 || true; done
L=build/main.log
printf '  errors=%s  overfull=%s  undefined=%s  pages=%s  worst=%spt\n' \
  "$(grep -a -cE '^!' $L)" "$(grep -a -c Overfull $L)" "$(grep -a -c 'undefined' $L)" \
  "$(grep -a -oE '\([0-9]+ pages' $L|tail -1|tr -d '(')" \
  "$(grep -a -oE 'Overfull \\hbox \([0-9.]+pt' $L | grep -oE '[0-9.]+' | sort -rn | head -1)"
grep -a -oE 'Overfull \\hbox \([0-9.]+pt' $L | grep -oE '[0-9.]+' | awk '{if($1>50)a++; else if($1>10)b++; else c++} END{printf "  >50pt:%d  10-50pt:%d  <10pt:%d\n", a+0, b+0, c+0}'
