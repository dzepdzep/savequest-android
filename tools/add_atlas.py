"""Add JohtoAtlas-derived data to catalog.json: wild locations per Pokemon (grass, surf, Headbutt, Rock Smash),
NPC gifts, Johto/Kanto regions and RankedBoost area links for trainers.
Run AFTER build_catalog.py and add_spots.py:  python tools/add_atlas.py
Input tools/atlas_ref.json holds facts only (no art, no dialogue)."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
CAT=ROOT/'app/src/main/assets/catalog.json'
cat=json.loads(CAT.read_text(encoding='utf8'));PIN=cat['sourceCommit'];tasks=cat['tasks']
ref=json.loads((ROOT/'tools/atlas_ref.json').read_text(encoding='utf8'))
species=json.loads((ROOT/'tools/species.json').read_text(encoding='utf8'))
IDX='https://rankedboost.com/pokemon/location/gold-silver/'
AREAS=[('TEAM_ROCKET_BASE','team-rocket-hq'),('TEAM_ROCKET_HQ','team-rocket-hq'),('ROCKET_HIDEOUT','team-rocket-hq'),('BURNED_TOWER','burned-tower'),('SPROUT_TOWER','sprout-tower'),('TIN_TOWER','tin-tower'),('WHIRL_ISLAND','whirl-islands'),('MOUNT_MORTAR','mt-mortar'),('MT_MORTAR','mt-mortar'),('SILVER_CAVE','mt-silver'),('MOUNT_SILVER','mt-silver'),('MT_SILVER','mt-silver'),('MOUNT_MOON','mt-moon'),('MT_MOON','mt-moon'),('ICE_PATH','ice-path'),('DARK_CAVE','dark-cave'),('DRAGONS_DEN','dragons-den'),('RUINS_OF_ALPH','ruins-of-alph'),('UNION_CAVE','union-cave'),('SLOWPOKE_WELL','slowpoke-well'),('ILEX_FOREST','ilex-forest'),('NATIONAL_PARK','national-park'),('ROCK_TUNNEL','rock-tunnel'),('DIGLETTS_CAVE','digletts-cave'),('TOHJO_FALLS','tohjo-falls'),('VICTORY_ROAD','victory-road'),('LAKE_OF_RAGE','lake-of-rage'),('RADIO_TOWER','radio-tower'),('POWER_PLANT','power-plant'),('OLIVINE_LIGHTHOUSE','olivine-city'),('VERMILION_PORT','vermilion-city'),('FAST_SHIP','vermilion-city'),('SILPH_CO','saffron-city'),('INDIGO_PLATEAU','indigo-plateau'),('SEAFOAM_ISLAND','seafoam-islands'),('OLIVINE_CITY','olivine-city'),('MAHOGANY_TOWN','mahogany-town'),('NEW_BARK_TOWN','new-bark-town'),('CHERRYGROVE_CITY','cherrygrove-city'),('VIOLET_CITY','violet-city'),('AZALEA_TOWN','azalea-town'),('GOLDENROD_CITY','goldenrod-city'),('ECRUTEAK_CITY','ecruteak-city'),('CIANWOOD_CITY','cianwood-city'),('BLACKTHORN_CITY','blackthorn-city'),('PALLET_TOWN','pallet-town'),('VIRIDIAN_CITY','viridian-city'),('PEWTER_CITY','pewter-city'),('CERULEAN_CITY','cerulean-city'),('VERMILION_CITY','vermilion-city'),('LAVENDER_TOWN','lavender-town'),('CELADON_CITY','celadon-city'),('SAFFRON_CITY','saffron-city'),('FUCHSIA_CITY','fuchsia-city'),('CINNABAR_ISLAND','cinnabar-island'),('VIOLET_GYM','violet-city'),('AZALEA_GYM','azalea-town'),('GOLDENROD_GYM','goldenrod-city'),('ECRUTEAK_GYM','ecruteak-city'),('CIANWOOD_GYM','cianwood-city'),('OLIVINE_GYM','olivine-city'),('MAHOGANY_GYM','mahogany-town'),('BLACKTHORN_GYM','blackthorn-city'),('PEWTER_GYM','pewter-city'),('CERULEAN_GYM','cerulean-city'),('VERMILION_GYM','vermilion-city'),('CELADON_GYM','celadon-city'),('FUCHSIA_GYM','fuchsia-city'),('SAFFRON_GYM','saffron-city'),('VIRIDIAN_GYM','viridian-city')]
def guide(key):
    m=re.match(r'^ROUTE_(\d+)(?:_|$)',key)
    if m and 1<=int(m[1])<=46:return f'{IDX}route-{int(m[1])}/'
    for p,s in AREAS:
        if key==p or key.startswith(p+'_'):return f'{IDX}{s}/'
    return IDX
stem_of={s.upper():s for s in cat['maps']}                # SPROUTTOWER2F -> SproutTower2F
def stem(key):return stem_of.get(key.replace('_',''))
def where(key):
    s=stem(key);return cat['maps'][s]['location'] if s else key.replace('_',' ').title()
def region(key):return ref['regions'].get(key,'')
TIME={'morn':'morning','day':'day','nite':'night'}
def rng(l):return f'Lv {min(l)}' if min(l)==max(l) else f'Lv {min(l)}-{max(l)}'
wild={i:[] for i in range(1,252)}
def add(n,key,how):wild[n].append({'area':where(key),'region':region(key),'how':how,'url':guide(key)})
for key,e in ref['encounters'].items():
    if 'grass' in e:
        per={}
        for t,l in e['grass'].items():
            for sid,lv in l:per.setdefault(sid,{}).setdefault(t,[]).append(lv)
        for sid,ts in per.items():
            groups={}
            for t,lv in ts.items():groups.setdefault(rng(lv),[]).append(t)
            for r,t in groups.items():
                label='any time' if set(t)==set(TIME) else ', '.join(TIME[x] for x in TIME if x in t)
                add(sid,key,f'Grass · {label} · {r}')
    if 'water' in e:
        per={}
        for sid,lv in e['water']:per.setdefault(sid,[]).append(lv)
        for sid,lv in per.items():add(sid,key,f'Surf · {rng(lv)}')
name_to_id={re.sub(r'[^A-Z0-9]','',n.upper().replace('♀','F').replace('♂','M')):i for i,n in enumerate(species,1)}
sets=ref['treemons']['sets']
for key,setname in ((k,v.capitalize()) for k,v in ref['treemons']['maps'].items()):
    if setname in('Forest','Canyon'):
        com={};rar={}
        for part,acc in(('common',com),('rare',rar)):
            for sp,lv in sets[setname][part]:acc[sp]=acc.get(sp,0)+1
        for sp in set(com)|set(rar):
            n=name_to_id[sp];lv=sets[setname]['common'][0][1]
            pct_rare=sum(1 for s,_ in sets[setname]['rare'] if s==sp)  # table slots are weighted below
            # exact weights from the data tables: first slot 50, then 15,15,10,5,5 (same layout for every set)
            w=[50,15,15,10,5,5];pr=sum(w[i] for i,(s,_) in enumerate(sets[setname]['rare']) if s==sp);pc=sum(w[i] for i,(s,_) in enumerate(sets[setname]['common']) if s==sp)
            if pc and pr:add(n,key,f'Headbutt · any tree ({pc}%), rare trees ({pr}%) · Lv {lv}')
            elif pc:add(n,key,f'Headbutt · ordinary trees ({pc}%) · Lv {lv}')
            else:add(n,key,f'Headbutt · RARE trees only ({pr}% of rare-tree battles) · Lv {lv}')
    elif setname=='Rock':
        for sp,lv in sets['Rock']['common']:add(name_to_id[sp],key,f'Rock Smash · Lv {lv}')
for t in tasks:
    if t['category']=='Pokédex':
        n=t['dexNumber'];w=wild[n]
        # merge duplicate lines
        seen=set();w=[x for x in w if not (str(x) in seen or seen.add(str(x)))]
        regs=sorted({x['region'] for x in w if x['region']})
        t['wild']=[f"{x['area']}\t{x['region']}\t{x['how']}\t{x['url']}" for x in w]
        t['region']=','.join(regs);t['rankedboost']=w[0]['url'] if w else 'https://rankedboost.com/pokemon/pokedex/gold-silver/'
        t['detail']='Caught flag, not current party/PC ownership. '+('Wild locations below (grass, surf, Headbutt, Rock Smash). Fishing, gifts and trades are not listed.' if w else 'No wild grass/surf/Headbutt location found; likely a gift, trade, fishing or evolution.')
# trainers: region + RankedBoost area link
by_stem={s:k for k,s in ((k,stem(k)) for k in set(ref['regions'])|set(ref['encounters']))if s}
for t in tasks:
    if t['category']=='Trainers' and t.get('guide'):
        s=t['guide'].rsplit('/',1)[-1].removesuffix('.asm')
        key=next((k for k in ref['regions'] if stem(k)==s),None)
        if key:t['region']=region(key);t['area']=where(key);t['rankedboost']=guide(key)
# NPC gifts (TM/HM gifts already live in the TM/HM tab)
have={t['id'] for t in tasks};added=0
for n in ref['npcs']:
    if n['title'] in('Cyndaquil','Totodile','Chikorita'):continue   # only one starter is ever obtained
    tid='npc:'+n['event']
    if tid in have or not stem(n['area']):continue
    tasks.append({'id':tid,'title':('Evolution stones' if n['title'].startswith('Everstone') else n['title'])+' (gift)','category':'Items','location':where(n['area']),'detail':f"Reward from {n['giver']}. Completion uses its original event flag; some gifts need a story condition first.",'guide':f"https://github.com/pret/pokegold/blob/{PIN}/maps/{stem(n['area'])}.asm",'flags':[n['flag']],'region':region(n['area']),'area':where(n['area']),'rankedboost':guide(n['area']),'sprite':'','spot':''});added+=1
CAT.write_text(json.dumps(cat,ensure_ascii=False,indent=2),encoding='utf8')
print(f'wild lines {sum(len(v) for v in wild.values())}, species with wild: {sum(1 for v in wild.values() if v)}/251, npc gifts added {added}, tasks {len(tasks)}')
