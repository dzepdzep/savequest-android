"""Generate factual data only from the pinned original Gold/Silver disassembly.
Usage: python tools/build_catalog.py /path/to/pokegold
No ROM, artwork or verbatim game dialogue is bundled.
"""
from pathlib import Path
import json,re,subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
PIN='ef0201d8daf47e8b3ea1518eacf890f37d4cd5e8'
src=Path(sys.argv[1]); assert subprocess.check_output(['git','-C',str(src),'rev-parse','HEAD'],text=True).strip()==PIN
def read(path):return (src/path).read_text(encoding='utf8')
def url(path):return f'https://github.com/pret/pokegold/blob/{PIN}/{path}'
def pretty(s):return s.replace('_',' ').title()
flags={};n=0
for line in read('constants/event_flags.asm').splitlines():
    t=line.split(';')[0].strip()
    def num(s):return int(s.replace('$','0x'),0)
    m=re.match(r'const_def(?:\s+(\S+))?$',t)
    if m:n=num(m[1]) if m[1] else 0;continue
    m=re.match(r'const_next\s+(\S+)',t)
    if m:n=num(m[1]);continue
    m=re.match(r'const_skip(?:\s+(\S+))?$',t)
    if m:n+=num(m[1]) if m[1] else 1;continue
    m=re.match(r'const\s+(EVENT_\w+)',t)
    if m:flags[m[1]]=n;n+=1
assert flags and max(flags.values())<2000
# Trainer constant -> class and 1-based party index.
ids={};cls=None;n=1
for line in read('constants/trainer_constants.asm').splitlines():
    m=re.match(r'\s*trainerclass\s+(\w+)',line)
    if m:cls=m[1];n=1
    m=re.match(r'\s*const\s+(\w+)',line)
    if m and cls:ids[(cls,m[1])]=n;n+=1
groups=re.findall(r'^\s*dw\s+(\w+)Group',read('data/trainers/party_pointers.asm'),re.M)
classes=re.findall(r'^\s*trainerclass\s+(\w+)',read('constants/trainer_constants.asm'),re.M)[1:]
assert len(groups)==len(classes),(len(groups),len(classes))
groupClass=dict(zip(groups,classes));parties={};group=None;index=0;party=None
for line in read('data/trainers/parties.asm').splitlines():
    t=line.split(';')[0].strip()
    m=re.match(r'(\w+)Group:',t)
    if m:group=groupClass[m[1]];index=0;continue
    m=re.match(r'db\s+"([^"@]*)@",\s*(TRAINERTYPE_\w+)',t)
    if m:index+=1;party={'name':m[1],'type':m[2],'team':[]};parties[(group,index)]=party;continue
    if t.startswith('db -1'):party=None;continue
    m=re.match(r'db\s+(\d+)\s*,\s*(\w+)(.*)',t)
    if m and party:party['team'].append({'level':int(m[1]),'species':m[2],'extra':m[3].lstrip(', ')})
tasks=[];trainers=[];maps={}
for file in sorted((src/'maps').glob('*.asm')):
    text=file.read_text(encoding='utf8');path='maps/'+file.name
    loc=re.sub(r'([a-z])([A-Z0-9])',r'\1 \2',file.stem)
    maps[file.stem]={'location':loc,'source':url(path)}
    for m in re.finditer(r'^\s*trainer\s+(\w+),\s*(\w+),\s*(EVENT_\w+)',text,re.M):
        cls,name,flag=m.groups();idx=ids[(cls,name)];party=parties[(cls,idx)]
        assert party['team'] and flag in flags
        detail=' / '.join(f"Lv {p['level']} {pretty(p['species'])}"+(f" ({pretty(p['extra'])})" if p['extra'] else '') for p in party['team'])
        task={'id':'trainer:'+flag,'title':pretty(cls)+' '+party['name'].title(),'category':'Trainers','location':loc,'detail':detail,'guide':url(path),'flags':[flags[flag]],'teamSource':url('data/trainers/parties.asm')}
        trainers.append(task)
    # Scripted leaders use beaten events rather than trainer object macros.
    for m in re.finditer(r'^\s*loadtrainer\s+(\w+),\s*(\w+)',text,re.M):
        cls,name=m.groups();flag='EVENT_BEAT_'+cls
        if flag not in flags or (cls,name) not in ids:continue
        party=parties[(cls,ids[(cls,name)])]
        task={'id':'trainer:'+flag,'title':pretty(cls),'category':'Trainers','location':loc,'detail':' / '.join(f"Lv {p['level']} {pretty(p['species'])}" for p in party['team']),'guide':url(path),'flags':[flags[flag]],'teamSource':url('data/trainers/parties.asm')}
        trainers.append(task)
    for m in re.finditer(r'^\s*hiddenitem\s+(\w+),\s*(EVENT_\w+)',text,re.M):
        item,flag=m.groups();tasks.append({'id':'item:'+flag,'title':pretty(item),'category':'Items','location':loc,'guide':url(path),'flags':[flags[flag]],'detail':'Hidden item; completion uses its original event flag.'})
# Exact duplicate event IDs can appear via multiple script paths; preserve one canonical entry.
trainers=list({t['id']:t for t in trainers}.values());tasks+=trainers
tm=json.loads((ROOT/'tools/tmhm.json').read_text(encoding='utf8'))
for i,item in enumerate(tm['items']):
    receipt=tm['receipts'].get(item['id'],[])
    for flag in receipt:
        assert flag in flags
        assert any(flag in p.read_text(encoding='utf8') for p in (src/'maps').glob('*.asm'))
    receiptSources=[url('maps/'+p.name) for p in sorted((src/'maps').glob('*.asm')) if any(f in p.read_text(encoding='utf8') for f in receipt)]
    tasks.append({'id':item['id'],'title':item['id']+' · '+item['move'],'category':'TM/HM','bagIndex':i,'flags':[flags[f] for f in receipt], 'location':' / '.join(m['where'] for m in item['methods']), 'detail':'Receipt flags can confirm a TM after it is consumed. Learned moves alone never prove TM ownership.', 'guide':receiptSources[0] if receiptSources else url('constants/item_constants.asm'), 'receiptSources':receiptSources})
for i,name in enumerate(json.loads((ROOT/'tools/species.json').read_text(encoding='utf8')),1):tasks.append({'id':f'dex:{i}','title':f'{i:03} · {name}','category':'Pokédex','dexNumber':i,'detail':'Caught flag, not current party/PC ownership.'})
unique={}
for task in tasks:
    if task['id'] in unique:
        unique[task['id']]['location']+=' / '+task.get('location','')
    else:unique[task['id']]=task
tasks=list(unique.values())
assert len(trainers)>250 and len({t['id'] for t in tasks})==len(tasks)
assert next(t for t in trainers if t['id']=='trainer:EVENT_BEAT_FALKNER')['detail']=='Lv 7 Pidgey / Lv 9 Pidgeotto'
output=ROOT/'app/src/main/assets/catalog.json';output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps({'sourceCommit':PIN,'tasks':tasks,'maps':maps},ensure_ascii=False,indent=2),encoding='utf8')
print(f'{len(tasks)} tasks; {len(trainers)} event-mapped trainers; {len(parties)} party variants parsed; {len(maps)} source maps')
