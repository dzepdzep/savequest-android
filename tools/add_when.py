"""Adds structured time limits to every task in app/src/main/assets/catalog.json so the app can filter by weekday / time of day.

  days  : which game-clock weekdays the thing is available on, e.g. ["Sat"]   (empty = every day)
  times : time-of-day windows named by the wild/event data, e.g. ["night"]     (empty = no time named)
          Pokemon: every window any of its wild rows names (a Pokemon with a night-only row is found under "night").

Idempotent; run after add_howto.py:   python tools/add_when.py
Game clock windows (pret/pokegold): morning 04:00-09:59, day 10:00-17:59, night 18:00-03:59.
"""
import json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];CAT=ROOT/'app/src/main/assets/catalog.json'
DAYS=['Mon','Tue','Wed','Thu','Fri','Sat','Sun']
FULL={d.lower():DAYS[i] for i,d in enumerate(['monday','tuesday','wednesday','thursday','friday','saturday','sunday'])}
def days_of(t):
    out=[]
    m=re.search(r'When: ([A-Z]+)DAY only',t.get('howto',''))
    if m:out.append(FULL[m.group(1).lower()+'day'])
    blob=t.get('location','')+' '+' '.join(w.split('\t')[2] for w in t.get('wild',[]) if w.count('\t')>=2)
    for k,v in FULL.items():
        if re.search(r'\b'+k+r'\b',blob,re.I) and v not in out:out.append(v)
    if 'Tue/Thu/Sat' in t.get('howto','') or 'Bug Contest' in t.get('location',''):
        for v in('Tue','Thu','Sat'):
            if v not in out:out.append(v)
    return sorted(out,key=DAYS.index)
def times_of(t):
    out=set()
    for w in t.get('wild',[]):
        parts=w.split('\t')
        if len(parts)<3:continue
        method=parts[2]
        if method.startswith('Timed event'):
            for x in re.findall(r'\b(morning|day|night)\b',method.split('–',1)[-1],re.I):out.add(x.lower())
            continue
        for p in method.split(' · '):
            if re.fullmatch(r'(morning|day|night)(, (morning|day|night))*',p):out.update(p.split(', '))
    return [x for x in ('morning','day','night') if x in out]
def main():
    cat=json.loads(CAT.read_text(encoding='utf8'));nd=nt=0
    for t in cat['tasks']:
        t.pop('days',None);t.pop('times',None)
        d=days_of(t);tm=times_of(t)
        if d:t['days']=d;nd+=1
        if tm:t['times']=tm;nt+=1
    CAT.write_text(json.dumps(cat,ensure_ascii=False,indent=2),encoding='utf8')
    print(f'{nd} tasks limited to weekdays, {nt} tasks name a time of day')
if __name__=='__main__':main()
