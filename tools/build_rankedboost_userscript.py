"""Build the Save -> RankedBoost script (ported from JohtoAtlas v0.2). It reads your Silver save locally and ticks
matching caught Pokémon + item pickups on rankedboost.com. Output: tools/rankedboost/savequest-rankedboost.user.js
(browser userscript) and app/src/main/assets/rankedboost/savequest-rankedboost.js (injected by the app's RankedBoost screen).

The data (item name slug, event flag, hidden/visible, RankedBoost page) comes from SaveQuest's own catalog.json,
so it always agrees with the app. Run AFTER add_howto.py:   python tools/build_rankedboost_userscript.py
`--check` only verifies that the committed file is up to date (used by test_catalog.py)."""
from pathlib import Path
import json,re,sys
ROOT=Path(__file__).resolve().parents[1]
TPL=ROOT/'tools/rankedboost/savequest-rankedboost.template.js'
OUT=ROOT/'tools/rankedboost/savequest-rankedboost.user.js'
APP=ROOT/'app/src/main/assets/rankedboost/savequest-rankedboost.js'   # the same script, injected by the app's RankedBoost screen
def slug(s):
    s=s.lower().replace('♀','-f').replace('♂','-m').replace('elixer','elixir').replace('poké','poke').replace('é','e')
    return re.sub(r'[^a-z0-9]+','-',s).strip('-')
def build():
    cat=json.loads((ROOT/'app/src/main/assets/catalog.json').read_text(encoding='utf8'))
    species=[slug(n) for n in json.loads((ROOT/'tools/species.json').read_text(encoding='utf8'))]
    assert len(species)==251 and species[0]=='bulbasaur' and species[151]=='chikorita' and species[28]!=species[31]
    maps={};seen=set()
    for t in cat['tasks']:
        if t['category']!='Items' or t.get('kind') not in('ball','hidden') or not t.get('flags'):continue
        m=re.match(r'https://rankedboost\.com/pokemon/location/gold-silver/([^/#]+)/',t.get('rankedboost',''))
        if not m:continue
        e={'s':slug(t['title']),'f':t['flags'][0],'k':'hidden' if t['kind']=='hidden' else 'visible'}
        if (m[1],e['s'],e['f']) in seen:continue
        seen.add((m[1],e['s'],e['f']));maps.setdefault(m[1],[]).append(e)
    data=json.dumps({'species':species,'maps':maps},ensure_ascii=True,separators=(',',':')).replace('<','\\u003c')
    src=TPL.read_text(encoding='utf8');assert src.count('/*__SAVEQUEST_DATA__*/')==1
    return src.replace('/*__SAVEQUEST_DATA__*/',data),sum(map(len,maps.values())),len(maps)
if __name__=='__main__':
    text,n,pages=build()
    if '--check' in sys.argv:
        sys.exit(0 if all(f.exists() and f.read_text(encoding='utf8')==text for f in (OUT,APP)) else 1)
    APP.parent.mkdir(parents=True,exist_ok=True);APP.write_text(text,encoding='utf8');OUT.write_text(text,encoding='utf8');print(f'Built {OUT} and {APP} · 251 species, {n} item flags on {pages} RankedBoost pages, {len(text)} bytes')
