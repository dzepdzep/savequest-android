"""Builds app/src/main/assets/advisor.json - the data behind the Team tab ("Advisor").

Everything numeric comes from the pinned pret/pokegold checkout (types, type chart, moves, HM learnsets, the boss teams and the
event-flag numbers); the written tips are short notes paraphrased from Serebii, NationalDex and Bulbapedia (credited in the file).

  python tools/build_advisor.py <path to a pret/pokegold checkout at the commit named in catalog.json>
"""
import json,re,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/main/assets/advisor.json'
SRC_SEREBII='https://www.serebii.net/gs/gyms.shtml'
SRC_NATDEX_E4='https://nationaldex.io/guide/pokemon-gold-silver-crystal/elite-four'
SRC_NATDEX_RED='https://nationaldex.io/guide/pokemon-gold-silver-crystal/red-battle'
SRC_BULBA='https://bulbapedia.bulbagarden.net/wiki/Save_data_structure_(Generation_II)'

# id, pret group, title, where, kind, region, badge bit (or -1), recommended level, tips, source
# Tips are short notes paraphrased from the pro sites above. The matchup maths in the app is computed from the real teams.
STAGES=[
 ('falkner','FalknerGroup','Falkner','Violet City Gym','gym','johto',0,9,'Flying team. Electric and Rock moves hit it hard. Cyndaquil or Totodile win easily; Chikorita needs a backup such as Geodude. Watch out for Mud-Slap lowering your accuracy.',SRC_SEREBII),
 ('bugsy','BugsyGroup','Bugsy','Azalea Town Gym','gym','johto',1,16,"Bug team. Scyther's Fury Cutter gets stronger every turn, so take it out fast. Fire, Flying and Rock moves are the answers; Thundershock helps against Scyther too.",SRC_SEREBII),
 ('whitney','WhitneyGroup','Whitney','Goldenrod City Gym','gym','johto',2,20,"Miltank is the wall of the early game: Rollout, Attract and Milk Drink. Fighting moves are the clean answer (Machop, Mankey). A Pokémon that resists Rollout helps; Attract can stop a male starter, so bring a spare.",SRC_SEREBII),
 ('morty','MortyGroup','Morty','Ecruteak City Gym','gym','johto',3,25,"Ghost team. Normal and Fighting moves cannot touch Ghosts. Psychic, Dark and Ground moves work, and Flying types like Noctowl or Togetic handle the Poison half well.",SRC_SEREBII),
 ('chuck','ChuckGroup','Chuck','Cianwood City Gym','gym','johto',5,30,"Fighting team. Psychic, Flying and Ghost types do well; Xatu is a strong pick. Poliwrath's DynamicPunch always confuses, so keep a Pokémon in reserve.",SRC_SEREBII),
 ('jasmine','JasmineGroup','Jasmine','Olivine City Gym','gym','johto',4,35,"Steel team: two Magnemite and a Steelix. Fire and Fighting moves are the answer; Ground also works on Magnemite. Heal Amphy at the lighthouse first (Secret Potion from Cianwood) or she will not battle you.",SRC_SEREBII),
 ('pryce','PryceGroup','Pryce','Mahogany Town Gym','gym','johto',6,31,"Ice team. Fire, Fighting and Rock moves are best. Dewgong and Piloswine use Rest, so attack while they sleep and keep Full Heals for Frozen Pokémon.",SRC_SEREBII),
 ('clair','ClairGroup','Clair','Blackthorn City Gym','gym','johto',7,40,"Three Dragonair and a Kingdra. Ice and Electric moves are the answers; Kingdra is only weak to Dragon. Piloswine and Lanturn are good partners.",SRC_SEREBII),
 ('will','WillGroup','Will','Indigo Plateau (Elite Four 1)','e4','johto',-1,42,'Psychic team. Dark types are the cleanest answer (Umbreon, Houndoom); Ampharos also helps. Jynx, Slowbro and Xatu are the main threats.',SRC_NATDEX_E4),
 ('koga','KogaGroup','Koga','Indigo Plateau (Elite Four 2)','e4','johto',-1,44,'Poison/Bug team (Koga is Elite Four in this game). Psychic and Fire answer most of it. Crobat, Muk and Forretress are the threats.',SRC_NATDEX_E4),
 ('bruno','BrunoGroup','Bruno','Indigo Plateau (Elite Four 3)','e4','johto',-1,46,'Fighting team plus Onix. Psychic and Flying support work well; handle Onix separately with Water or Grass moves. Machamp and Hitmonlee hit hardest.',SRC_NATDEX_E4),
 ('karen','KarenGroup','Karen','Indigo Plateau (Elite Four 4)','e4','johto',-1,47,"Dark team. Do not rely only on Psychic coverage: Fighting, Bug and Water moves help. Houndoom, Umbreon and Gengar are the threats.",SRC_NATDEX_E4),
 ('lance','ChampionGroup','Lance','Indigo Plateau (Champion)','champion','johto',-1,50,'Dragon team. Ice coverage is the cleanest answer to several Dragonite; Electric helps against Gyarados. Lapras, Jynx, Ampharos and Piloswine are good picks.',SRC_NATDEX_E4),
 ('surge','LtSurgeGroup','Lt. Surge','Vermilion City Gym','gym','kanto',2,46,'Electric team. Ground moves are the answer (Nidoking, Steelix, Rhydon). The trash-can switches open the door to him.',SRC_SEREBII),
 ('sabrina','SabrinaGroup','Sabrina','Saffron City Gym','gym','kanto',5,48,"Psychic team. Dark moves and Ghost moves are effective and she has no real answer to them. Walk the teleporter maze to reach her.",SRC_SEREBII),
 ('erika','ErikaGroup','Erika','Celadon City Gym','gym','kanto',3,46,'Grass team that leans on status moves. Fire, Flying and Ice moves are best; bring Full Restores or status healers. Sunny Day boosts your Fire moves.',SRC_SEREBII),
 ('janine','JanineGroup','Janine','Fuchsia City Gym','gym','kanto',4,39,'Poison team. Psychic and Ground moves work well. She hides among look-alike trainers, so check the gym guide for the real one.',SRC_SEREBII),
 ('misty','MistyGroup','Misty','Cerulean City Gym','gym','kanto',1,47,'Water team. Grass and Electric moves are the answers (Meganium, Ampharos, Raichu). Lapras and Starmie carry Ice moves.',SRC_SEREBII),
 ('brock','BrockGroup','Brock','Pewter City Gym','gym','kanto',0,44,'Rock team. Grass, Water and Fighting moves are strong; Meganium is a great fit. Electric moves help against Omastar and Kabutops.',SRC_SEREBII),
 ('blaine','BlaineGroup','Blaine','Seafoam Islands Gym','gym','kanto',6,50,'Fire team. Water moves are best, with Rock and Ground as backup. Bring a Burn Heal.',SRC_SEREBII),
 ('blue','BlueGroup','Blue','Viridian City Gym','gym','kanto',7,58,'Mixed team (the Kanto finale). Bring Electric, Ice, Rock and Water answers; he has no single weakness to exploit, so use several Pokémon.',SRC_SEREBII),
 ('red','RedGroup','Red','Mt. Silver summit','red','kanto',-1,65,"Six strong Pokémon across Electric, Psychic, Normal, Grass, Fire and Water. A level 60-70+ team is the usual target. Ground handles Pikachu, keep a dedicated plan for Snorlax, and carry lots of healing.",SRC_NATDEX_RED),
]
# stages that have no badge: the event flag that proves they were beaten (Red vanishes from Mt. Silver once beaten)
FLAGS={'will':'EVENT_BEAT_ELITE_4_WILL','koga':'EVENT_BEAT_ELITE_4_KOGA','bruno':'EVENT_BEAT_ELITE_4_BRUNO','karen':'EVENT_BEAT_ELITE_4_KAREN','lance':'EVENT_BEAT_CHAMPION_LANCE','red':'EVENT_RED_IN_MT_SILVER'}
JOHTO_BADGES=['Zephyr','Hive','Plain','Fog','Mineral','Storm','Glacier','Rising']
KANTO_BADGES=['Boulder','Cascade','Thunder','Rainbow','Soul','Marsh','Volcano','Earth']
HMS=[('CUT','Cut','Hive'),('FLY','Fly','Storm'),('SURF','Surf','Fog'),('STRENGTH','Strength','Plain'),('FLASH','Flash','Zephyr'),('WHIRLPOOL','Whirlpool','Glacier'),('WATERFALL','Waterfall','Rising')]
TYPE_ORDER=['NORMAL','FIGHTING','FLYING','POISON','GROUND','ROCK','BUG','GHOST','STEEL','FIRE','WATER','GRASS','ELECTRIC','PSYCHIC_TYPE','ICE','DRAGON','DARK']
TYPE_NAMES=['Normal','Fighting','Flying','Poison','Ground','Rock','Bug','Ghost','Steel','Fire','Water','Grass','Electric','Psychic','Ice','Dragon','Dark']

def read(p):return Path(p).read_text(encoding='utf8')
def consts(text,start=None):
    """name -> value for a pret 'const' list (handles const_def / const_skip / const_next)."""
    out={};n=0
    for line in text.splitlines():
        s=line.split(';')[0].strip()
        if s.startswith('const_def'):n=0
        elif s.startswith('const_skip'):n+=1
        elif s.startswith('const_next'):n=int(s.split()[1].replace('$','0x'),0)
        else:
            m=re.match(r'const\s+(\w+)',s)
            if m:out[m.group(1)]=n;n+=1
    return out
def build(pg):
    pg=Path(pg)
    species=json.loads((ROOT/'tools/species.json').read_text(encoding='utf8'))
    assert len(species)==251
    tid={t:i for i,t in enumerate(TYPE_ORDER)}
    # base stats: types + HM learnsets
    sp=[None]*251;learn={h[0]:[] for h in HMS}
    for f in sorted((pg/'data/pokemon/base_stats').glob('*.asm')):
        t=read(f);m=re.search(r'db\s+(\w+)\s*;\s*(\d+)',t);dex=int(m.group(2))
        ty=re.search(r'db\s+(\w+),\s*(\w+)\s*;\s*type',t).groups()
        sp[dex-1]=[tid[x] for i,x in enumerate(ty) if x not in ty[:i]]   # a single-type species lists its type twice
        tm=re.search(r'^\s*tmhm\s+([^\n;]*)',t,re.M)
        names={x.strip() for x in tm.group(1).split(',')} if tm else set()
        for h in learn:
            if h in names:learn[h].append(dex)
    assert all(sp),'missing species'
    # type chart (only entries that are not neutral)
    chart=[];sec=read(pg/'data/types/type_matchups.asm')
    mult={'SUPER_EFFECTIVE':20,'NOT_VERY_EFFECTIVE':5,'NO_EFFECT':0}
    for line in sec.splitlines():
        if re.match(r'\s*db\s+-1',line):break
        m=re.match(r'\s*db\s+(\w+),\s*(\w+),\s*(\w+)',line)
        if m and m.group(3) in mult:chart.append([tid[m.group(1)],tid[m.group(2)],mult[m.group(3)]])
    assert 100<len(chart)<130,len(chart)
    # moves
    mv=consts(read(pg/'constants/move_constants.asm'));moves={}
    for line in read(pg/'data/moves/moves.asm').splitlines():
        m=re.match(r'\s*move\s+(\w+),\s*\w+,\s*(\d+),\s*(\w+),',line)
        if m and m.group(1) in mv:moves[mv[m.group(1)]]=[m.group(1).replace('_M','').title().replace('_',' '),int(m.group(2)),tid.get(m.group(3),-1)]
    # boss teams
    parties=read(pg/'data/trainers/parties.asm');sd=dict((n,i+1) for i,n in enumerate([re.sub(r'[^A-Z0-9]','',x.upper()) for x in species]))
    def species_id(const):
        k=re.sub(r'[^A-Z0-9]','',const.replace('_M','M') if const.endswith('_M') else const)
        for i,n in enumerate(species):
            if re.sub(r'[^A-Z0-9]','',n.upper().replace('♀','F').replace('♂','M'))==k:return i+1
        raise KeyError(const)
    # event flags
    ev=consts(read(pg/'constants/event_flags.asm'))
    stages=[]
    for sid,grp,title,where,kind,region,bit,rec,tip,src in STAGES:
        m=re.search(r'^'+grp+r':\n(.*?)(?=^\w+Group:|\Z)',parties,re.M|re.S)
        body=m.group(1);hdr=re.search(r'db\s+"[^"]*",\s*TRAINERTYPE_(\w+)',body).group(1)
        first=body.split('db -1')[0]
        mons=[]
        for line in first.splitlines():
            r=re.match(r'\s*db\s+(\d+),\s*(\w+)((?:,\s*\w+)*)',line)
            if not r:continue
            rest=[x.strip() for x in r.group(3).split(',') if x.strip()]
            item=None
            if hdr.startswith('ITEM'):item=rest.pop(0)
            mm=[mv[x] for x in rest if x in mv and mv[x]!=0] if hdr.endswith('MOVES') else []
            mons.append({'species':species_id(r.group(2)),'level':int(r.group(1)),'moves':mm}|({'item':item} if item else {}))
        assert 2<=len(mons)<=6,(sid,len(mons))
        flag=ev[FLAGS[sid]] if sid in FLAGS else None
        stages.append({'id':sid,'title':title,'where':where,'kind':kind,'region':region,'badge':bit,'recommended':rec,'tips':tip,'source':src,'mons':mons}|({'flag':flag} if flag is not None else {}))
    hm=[{'id':mv[h],'name':n,'badge':b,'learners':learn[h]} for h,n,b in HMS]
    data={'sourceCommit':json.loads((ROOT/'app/src/main/assets/catalog.json').read_text(encoding='utf8'))['sourceCommit'],
          'credits':{'teams, types, chart, moves, learnsets':'pret/pokegold','tips':[SRC_SEREBII,SRC_NATDEX_E4,SRC_NATDEX_RED],'save offsets':SRC_BULBA},
          'typeNames':TYPE_NAMES,'speciesNames':species,'johtoBadges':JOHTO_BADGES,'kantoBadges':KANTO_BADGES,'species':sp,'chart':chart,'moves':{str(k):v for k,v in moves.items()},'hms':hm,'stages':stages}
    OUT.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':')),encoding='utf8')
    print(f'Built {OUT}: {len(stages)} stages, {len(chart)} type rules, {len(moves)} moves, {OUT.stat().st_size} bytes')
if __name__=='__main__':
    if len(sys.argv)<2:sys.exit(__doc__)
    build(sys.argv[1])
