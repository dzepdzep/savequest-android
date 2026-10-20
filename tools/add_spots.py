"""Add region, RankedBoost links and visible item balls to catalog.json.
Run AFTER build_catalog.py:  python tools/add_spots.py
Input tools/spots.json comes from the same pinned pret/pokegold map scripts (itemball / hiddenitem),
plus RankedBoost page slugs. TMs/HMs stay in the TM/HM tab (they are tracked from the Bag).
"""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
CAT=ROOT/'app/src/main/assets/catalog.json'
cat=json.loads(CAT.read_text(encoding='utf8'))
PIN=cat['sourceCommit']
RB='https://rankedboost.com/pokemon/location/gold-silver/'
spots=json.loads((ROOT/'tools/spots.json').read_text(encoding='utf8'))
tasks=cat['tasks'];by_flag={t['flags'][0]:t for t in tasks if t['id'].startswith('item:') and t.get('flags')}
from urllib.parse import quote
def link(s):
    if not s['rb']:return ''
    base=RB+s['rb']+'/#pokemon-location-items'
    snippet=' '.join((s['rbText'] or '').split(' (')[0].split(' ')[:5])
    if len(snippet)<8:snippet=s['item']
    # Chrome highlights the matching text on the RankedBoost page; other browsers just scroll to the Items section.
    return base+':~:text='+quote(snippet,safe='').replace('-','%2D')
added=0;enriched=0
for s in spots:
    extra={'region':s['region'],'area':s['area']+(' '+s['floor'] if s['floor'] else ''),'rankedboost':link(s),'sprite':s['sprite'],'spot':s['rbText'] or ''}
    t=by_flag.get(s['flag'])
    place=f"{'Hidden item (needs Itemfinder)' if s['kind']=='hidden' else 'Item ball'} in the {s['where']} part of {extra['area']}, tile {s['x']},{s['y']}."
    if t:
        t.update(extra);t['detail']=place+' Completion uses its original event flag.';enriched+=1
    elif s['cat']=='item':
        tasks.append({'id':'item:'+s['event'],'title':s['item'],'category':'Items','location':extra['area'],'detail':place+' Completion uses its original event flag.','guide':f"https://github.com/pret/pokegold/blob/{PIN}/maps/{s['map']}.asm",'flags':[s['flag']],**extra});added+=1
CAT.write_text(json.dumps(cat,ensure_ascii=False,indent=2),encoding='utf8')
print(f'{enriched} hidden items enriched, {added} item balls added, {len(tasks)} tasks')
