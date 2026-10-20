"""Add a plain-language "how to get it" description (field `howto`, lines separated by \\n) to every Items entry.

Run LAST:  python tools/add_spots.py && python tools/add_atlas.py && python tools/add_howto.py   (idempotent)

Facts come from the same pinned pret/pokegold map scripts (weekday, badge, story-event and party-space checks),
plus the RankedBoost spot text (for HMs such as Surf / Rock Smash). No game text or art is copied.
Also: adds the NPC gifts the first pass missed (Bill's grandpa stones, Elm's gifts, Metal Coat, Miracle Seed,
Soft Sand, King's Rock, Mystery Egg, Randy's HP Up, Rainbow Wing), fixes ugly names and splits mixed entries.
"""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
CAT=ROOT/'app/src/main/assets/catalog.json'
cat=json.loads(CAT.read_text(encoding='utf8'));PIN=cat['sourceCommit'];tasks=cat['tasks']
RB='https://rankedboost.com/pokemon/location/gold-silver/'
REG={'johto':'Johto','kanto':'Kanto'}
by_id={t['id']:t for t in tasks}

# ---- names -----------------------------------------------------------------------------------------------------
TITLE={'Elixer':'Elixir','Max Elixer':'Max Elixir','Pp Up':'PP Up','Parlyz Heal':'Paralyze Heal','X Defend':'X Defense','X Sp Atk':'X Special','Hp Up':'HP Up'}
for t in tasks:
    if t['category']=='Items' and t.get('kind') in('ball','hidden'):t['title']=TITLE.get(t['title'],t['title'])

# ---- gifts ---------------------------------------------------------------------------------------------------------
# Lines start with a label: Where / When / Needs / Do / Note.
G={}
def gift(ev,title,loc,region,slug,stem,flag,*steps):G[ev]=dict(title=title,loc=loc,region=region,slug=slug,stem=stem,flag=flag,steps=list(steps))
DAYNOTE='Note: the weekday comes from the game clock (the real-time clock in the cartridge). The giver only stands there on that weekday; you can only get it once.'
gift('EVENT_GOT_GOOD_ROD','Good Rod','Olivine Good Rod House','johto','olivine-city','OlivineGoodRodHouse',24,
 'Where: Olivine City. Enter the Good Rod House and talk to the Fishing Guru.','When: any day, any time.','Needs: nothing (no badge, no other item).','Do: answer YES when he offers it. If you say NO he offers again.')
gift('EVENT_GOT_OLD_ROD','Old Rod','Route 32 Pokémon Center','johto','route-32','Route32Pokecenter1F',23,
 'Where: the Pokémon Center on Route 32 (south of Violet City). Talk to the Fishing Guru.','When: any day, any time.','Needs: nothing.','Do: answer YES.')
gift('EVENT_GOT_SUPER_ROD','Super Rod','Route 12 Super Rod House','kanto','route-12','Route12SuperRodHouse',25,
 'Where: Kanto, the Super Rod House on Route 12. Talk to the Fishing Guru.','When: any day, any time.','Needs: access to Kanto (all 8 Johto badges).','Do: answer YES.')
gift('EVENT_GOT_QUICK_CLAW','Quick Claw','National Park','johto','national-park','NationalPark',87,
 'Where: National Park (north of Goldenrod). Talk to the Teacher standing in the south part of the park (tile 27,40).','When: any day. (On Tue/Thu/Sat the park runs the Bug-Catching Contest – if you cannot find her, come back on another day.)','Needs: a free Bag slot.','Do: just talk to her.')
gift('EVENT_GOT_PINK_BOW_FROM_MARY','Pink Bow (DJ Mary)','Radio Tower 4F','johto','radio-tower','RadioTower4F',72,
 'Where: Goldenrod Radio Tower, 4th floor. Talk to DJ Mary.','When: only after the Team Rocket takeover of the Radio Tower is finished (you beat the Rocket Executive upstairs and the Director is freed).','Needs: a free Bag slot.','Do: talk to DJ Mary again after the takeover.')
gift('EVENT_GOT_BLACKGLASSES_IN_DARK_CAVE','Blackglasses','Dark Cave (Blackthorn entrance)','johto','dark-cave','DarkCaveBlackthornEntrance',114,
 'Where: Dark Cave, the Blackthorn side (entered from Route 45). The Pharmacist stands near the entrance.','When: any day, any time.','Needs: nothing – but Dark Cave is dark, so use Flash if you want to see.','Do: talk to the Pharmacist.')
gift('EVENT_GOT_ITEMFINDER','Itemfinder','Ecruteak Itemfinder House','johto','ecruteak-city','EcruteakItemfinderHouse',90,
 'Where: Ecruteak City, the Itemfinder House. Talk to the man inside.','When: any day, any time.','Needs: nothing else – the game does not check badges or Pokédex count here.','Do: answer YES to his question. You need this to find the HIDDEN items listed in this app.')
gift('EVENT_GOT_SPELL_TAG_FROM_SANTOS','Spell Tag (Santos)','Blackthorn City','johto','blackthorn-city','BlackthornCity',109,
 'Where: Blackthorn City, Santos stands at tile 22,20.','When: SATURDAY only (game clock).','Needs: nothing.','Do: talk to Santos on a Saturday.',DAYNOTE)
gift('EVENT_GOT_SHARP_BEAK_FROM_MONICA','Sharp Beak (Monica)','Route 40','johto','route-40','Route40',111,
 'Where: Route 40 (the sea route south of Olivine City). Monica walks left/right at about tile 10,6.','When: MONDAY only.','Needs: nothing (you may need Surf to reach Route 40).','Do: talk to Monica on a Monday.',DAYNOTE)
gift('EVENT_GOT_PINK_BOW_FROM_TUSCANY','Pink Bow (Tuscany)','Route 29','johto','route-29','Route29',101,
 'Where: Route 29 (between New Bark Town and Cherrygrove), Tuscany stands near tile 29,12.','When: TUESDAY only.','Needs: nothing.','Do: talk to Tuscany on a Tuesday.',DAYNOTE)
gift('EVENT_GOT_BLACKBELT_FROM_WESLEY','Black Belt (Wesley)','Lake of Rage','johto','lake-of-rage','LakeOfRage',107,
 'Where: Lake of Rage, Wesley stands at tile 4,4 (north-west corner).','When: WEDNESDAY only.','Needs: nothing.','Do: talk to Wesley on a Wednesday.',DAYNOTE)
gift('EVENT_GOT_HARD_STONE_FROM_ARTHUR','Hard Stone (Arthur)','Route 36','johto','route-36','Route36',103,
 'Where: Route 36 (east of Violet City, towards Route 37). Arthur wanders around tile 46,6.','When: THURSDAY only.','Needs: nothing.','Do: talk to Arthur on a Thursday.',DAYNOTE)
gift('EVENT_GOT_POISON_BARB_FROM_FRIEDA','Poison Barb (Frieda)','Route 32','johto','route-32','Route32',99,
 'Where: Route 32 (south part, near the Union Cave end). Frieda stands at tile 12,67.','When: FRIDAY only.','Needs: nothing.','Do: talk to Frieda on a Friday.',DAYNOTE)
gift('EVENT_GOT_MAGNET_FROM_SUNNY','Magnet (Sunny)','Route 37','johto','route-37','Route37',105,
 'Where: Route 37 (north of Ecruteak). Sunny wanders around tile 16,8.','When: SUNDAY only.','Needs: nothing.','Do: talk to Sunny on a Sunday.',DAYNOTE)
gift('EVENT_GOT_CHARCOAL_IN_CHARCOAL_KILN','Charcoal','Charcoal Kiln','johto','azalea-town','CharcoalKiln',94,
 'Where: Charcoal Kiln in Azalea Town. Talk to the Apprentice.','When: any day.','Needs: HM01 Cut (the Charcoal Master gives it in Ilex Forest after you bring back his Farfetch\'d). The Apprentice only gives the Charcoal once you own Cut.','Do: come back to the kiln after getting Cut and talk to the Apprentice.')
gift('EVENT_GOT_SQUIRTBOTTLE','Squirtbottle','Goldenrod Flower Shop','johto','goldenrod-city','GoldenrodFlowerShop',92,
 'Where: Goldenrod City, the Flower Shop. Talk to the Teacher.','When: any day.','Needs: the Plain Badge (beat Whitney).','Do: talk to the Teacher with the badge. Without it she just chats.')
gift('EVENT_GOT_BICYCLE','Bicycle','Goldenrod Bike Shop','johto','goldenrod-city','GoldenrodBikeShop',91,
 'Where: Goldenrod City, the Bike Shop. Talk to the Clerk.','When: any day.','Needs: nothing in this version – he gives it away free.','Do: answer YES.')
gift('EVENT_GOT_EEVEE','Eevee (gift Pokémon)','Bill\'s Family\'s House (Ecruteak)','johto','ecruteak-city','BillsFamilysHouse',79,
 'Where: Ecruteak City, Bill\'s family house. Talk to Bill.','When: any day.','Needs: room in your party (fewer than 6 Pokémon with you). This is a Pokémon (Lv 20), not an item.','Do: answer YES.')
gift('EVENT_GOT_SECRETPOTION_FROM_PHARMACY','SecretPotion','Cianwood Pharmacy','johto','cianwood-city','CianwoodPharmacy',35,
 'Where: Cianwood City, Pharmacy. Talk to the Pharmacist.','When: after Jasmine has told you about Amphy\'s sickness at the top of the Olivine Lighthouse.','Needs: Surf to reach Cianwood, and having talked to Jasmine in the Lighthouse.','Do: go to the Pharmacy and talk to the Pharmacist. (It is the item you give Jasmine for Amphy.)')
gift('EVENT_GOT_MYSTIC_WATER_IN_CHERRYGROVE','Mystic Water','Cherrygrove City','johto','cherrygrove-city','CherrygroveCity',77,
 'Where: Cherrygrove City, the man near tile 7,12.','When: any day.','Needs: nothing.','Do: talk to him.')
gift('EVENT_GOT_BERRY_FROM_ROUTE_30_HOUSE','Berry','Route 30 Berry House','johto','route-30','Route30BerryHouse',39,
 'Where: the house on Route 30 (north of Cherrygrove). Talk to the man inside.','When: any day.','Needs: a free Bag slot.','Do: talk to him.')
gift('EVENT_GOT_KENYA','Spearow "Kenya" (gift Pokémon)','Route 35 – Goldenrod Gate','johto','route-35','Route35GoldenrodGate',80,
 'Where: the gate house between Goldenrod and Route 35. Talk to Randy.','When: any day.','Needs: room in your party (fewer than 6 Pokémon with you).','Do: answer YES; you get a Lv 10 Spearow holding a Flower Mail. Take it to the sleepy man on Route 31 (he gives TM50 Nightmare), then return to Randy for the HP Up.')
gift('EVENT_GOT_HP_UP_FROM_RANDY','HP Up (Kenya quest)','Route 35 – Goldenrod Gate','johto','route-35','Route35GoldenrodGate',82,
 'Where: the gate house between Goldenrod and Route 35. Talk to Randy.','When: after you delivered Kenya the Spearow to the sleepy man on Route 31.','Needs: Kenya must still hold the Flower Mail when you hand him over.','Do: talk to Randy once the delivery is done.')
gift('EVENT_GOT_EVERSTONE_FROM_ELM','Everstone (Prof. Elm)','Elm\'s Lab','johto','new-bark-town','ElmsLab',86,
 'Where: Prof. Elm\'s Lab in New Bark Town.','When: after you show him the hatched Togepi (or Togetic).','Needs: the Togepi Egg from Elm\'s Aide at Violet City\'s Pokémon Center, hatched.','Do: visit Elm with Togepi/Togetic in your party, or answer his phone call, then talk to him.')
gift('EVENT_GOT_MASTER_BALL_FROM_ELM','Master Ball','Elm\'s Lab','johto','new-bark-town','ElmsLab',124,
 'Where: Prof. Elm\'s Lab in New Bark Town. Talk to Elm.','When: after you have the Rising Badge (Clair, Blackthorn City).','Needs: Rising Badge.','Do: walk back to the lab and talk to Elm.')
gift('EVENT_GOT_SS_TICKET_FROM_ELM','S.S. Ticket','Elm\'s Lab','johto','new-bark-town','ElmsLab',36,
 'Where: Prof. Elm\'s Lab in New Bark Town. Talk to Elm.','When: after you beat the Elite Four.','Needs: Elite Four beaten.','Do: talk to Elm. The ticket lets you ride the S.S. Aqua between Olivine and Vermilion.')
gift('EVENT_GOT_MYSTERY_EGG_FROM_MR_POKEMON','Mystery Egg','Mr. Pokémon\'s House','johto','route-30','MrPokemonsHouse',30,
 'Where: Mr. Pokémon\'s House on Route 30.','When: the first time you enter, the story scene plays automatically.','Needs: a free Bag slot (Key Item pocket).','Do: just walk in and watch the scene. This is the item you take to Prof. Elm.')
gift('EVENT_GOT_MIRACLE_SEED_IN_ROUTE_32','Miracle Seed','Route 32','johto','route-32','Route32',93,
 'Where: Route 32, the Cooltrainer (male) who talks about the Sprout Tower area.','When: after you got the Zephyr Badge AND the Togepi Egg from Elm\'s Aide.','Needs: Zephyr Badge (Falkner) and Elm\'s Aide\'s Egg.','Do: talk to him again once you have both.')
gift('EVENT_GOT_SOFT_SAND_FROM_KATE','Soft Sand (Kate)','Route 34','johto','route-34','Route34',112,
 'Where: Route 34, Cooltrainer Kate.','When: after you win the battle with her.','Needs: win the trainer battle.','Do: battle Kate, then talk to her afterwards.')
gift('EVENT_GOT_KINGS_ROCK_IN_SLOWPOKE_WELL','King\'s Rock','Slowpoke Well B2F','johto','slowpoke-well','SlowpokeWellB2F',115,
 'Where: Slowpoke Well B2F (entered from Azalea Town). Talk to the Gym Guide.','When: after Team Rocket\'s grunts at the bottom are beaten.','Needs: nothing else.','Do: talk to the Gym Guide.')
gift('EVENT_GOT_METAL_COAT_FROM_GRANDPA_ON_SS_AQUA','Metal Coat','S.S. Aqua Captain\'s Cabin','johto','vermilion-city','FastShipCabins_SE_SSE_CaptainsCabin',113,
 'Where: the S.S. Aqua (ship between Olivine and Vermilion), the Captain\'s Cabin in the south-east of the ship.','When: during a trip; the old man is looking for his granddaughter.','Needs: the S.S. Ticket (from Prof. Elm after the Elite Four). Find his granddaughter (a little girl hiding on the ship), then talk to the old man.','Do: talk to the old man after finding the girl.')
gift('EVENT_GOT_EVERSTONE_FROM_BILLS_GRANDPA','Everstone (Bill\'s Grandpa)','Bill\'s House (Route 25)','kanto','route-25','BillsHouse',800,
 'Where: Bill\'s seaside house (Route 25, east of Cerulean). Talk to Bill\'s Grandpa.','When: show him Pokémon in this ORDER – each gift unlocks the next: Lickitung, Oddish, Staryu, Growlithe (Gold) / Vulpix (Silver), Pichu.','Needs: that Pokémon in your party. Say YES when he asks, then pick it.','Do: show LICKITUNG first → Everstone.')
gift('EVENT_GOT_LEAF_STONE_FROM_BILLS_GRANDPA','Leaf Stone (Bill\'s Grandpa)','Bill\'s House (Route 25)','kanto','route-25','BillsHouse',801,
 'Where: Bill\'s seaside house (Route 25). Talk to Bill\'s Grandpa.','Needs: you must already have received the Everstone from him (show Lickitung first).','Do: show him an ODDISH.')
gift('EVENT_GOT_WATER_STONE_FROM_BILLS_GRANDPA','Water Stone (Bill\'s Grandpa)','Bill\'s House (Route 25)','kanto','route-25','BillsHouse',802,
 'Where: Bill\'s seaside house (Route 25). Talk to Bill\'s Grandpa.','Needs: Everstone and Leaf Stone received first.','Do: show him a STARYU.')
gift('EVENT_GOT_FIRE_STONE_FROM_BILLS_GRANDPA','Fire Stone (Bill\'s Grandpa)','Bill\'s House (Route 25)','kanto','route-25','BillsHouse',803,
 'Where: Bill\'s seaside house (Route 25). Talk to Bill\'s Grandpa.','Needs: Everstone, Leaf Stone and Water Stone received first.','Do: show him a GROWLITHE if you play Gold, or a VULPIX if you play Silver.')
gift('EVENT_GOT_THUNDERSTONE_FROM_BILLS_GRANDPA','Thunderstone (Bill\'s Grandpa)','Bill\'s House (Route 25)','kanto','route-25','BillsHouse',804,
 'Where: Bill\'s seaside house (Route 25). Talk to Bill\'s Grandpa.','Needs: the other four stones received first.','Do: show him a PICHU (hatch one from a Pikachu/Raichu egg).')
gift('EVENT_GOT_HP_UP_FROM_VERMILION_GUY','HP Up (Vermilion)','Vermilion City','kanto','vermilion-city','VermilionCity',226,
 'Where: Vermilion City, the badge-checking man near the Gym (tile 31,12).','When: any day.','Needs: ALL 16 badges (8 Johto + 8 Kanto). With fewer badges he only comments.','Do: talk to him with all 16 badges.')
gift('EVENT_GOT_LOST_ITEM_FROM_FAN_CLUB','Lost Item','Pokémon Fan Club (Vermilion)','kanto','vermilion-city','PokemonFanClub',210,
 'Where: Pokémon Fan Club in Vermilion City. Talk to the man with the Clefairy.','When: after you returned the Machine Part to the Power Plant manager (Route 10) and talked to Copycat in Saffron about her lost item.','Needs: Machine Part returned + Copycat\'s story.','Do: return to the Fan Club and talk to the Clefairy man; he gives the Lost Item. Bring it to Copycat for the Pass.')
gift('EVENT_GOT_PASS_FROM_COPYCAT','Pass (Magnet Train)','Copycat\'s House 2F (Saffron)','kanto','saffron-city','CopycatsHouse2F',209,
 'Where: Copycat\'s House, 2nd floor, Saffron City.','When: after you hand her the Lost Item.','Needs: Lost Item (from the Pokémon Fan Club).','Do: return the Lost Item to Copycat; she gives you the Pass for the Magnet Train.')
gift('EVENT_GOT_SILVER_WING','Silver Wing (Gold version only)','Pewter City','kanto','pewter-city','PewterCity',121,
 'Where: Pewter City, the old man (Gramps) who walks left/right near tile 29,17.','When: any day, once Kanto is open.','Needs: access to Kanto. This is the Gold-version item – Pokémon Silver gives the Rainbow Wing instead (next entry).','Do: talk to him.')
gift('EVENT_GOT_RAINBOW_WING','Rainbow Wing (Silver version only)','Pewter City','kanto','pewter-city','PewterCity',120,
 'Where: Pewter City, the old man (Gramps) who walks left/right near tile 29,17.','When: any day, once Kanto is open.','Needs: access to Kanto. This is the Silver-version item – Pokémon Gold gives the Silver Wing instead.','Do: talk to him.')
gift('EVENT_GOT_NUGGET_FROM_GUY','Nugget','Route 2 Nugget House','kanto','route-2','Route2NuggetHouse',200,
 'Where: the Nugget House on Route 2 (north of Viridian).','When: any day.','Needs: a free Bag slot.','Do: talk to the man inside.')
gift('EVENT_GOT_CLEANSE_TAG','Cleanse Tag','Route 5 Cleanse Tag House','kanto','route-5','Route5CleanseTagHouse',219,
 'Where: the house on Route 5 (between Cerulean and Saffron). Talk to the Granny.','When: any day.','Needs: a free Bag slot.','Do: talk to her.')
gift('EVENT_GOT_UP_GRADE','Up-Grade','Silph Co. 1F (Saffron)','kanto','saffron-city','SilphCo1F',222,
 'Where: Silph Co. in Saffron City, 1st floor. Talk to the Officer.','When: any day.','Needs: access to Saffron City (Kanto) and a free Bag slot.','Do: talk to the Officer.')

for ev,g in G.items():
    tid='npc:'+ev
    task={'id':tid,'title':g['title'],'category':'Items','location':g['loc'],
          'detail':'Reward from an NPC. Completion uses its original event flag.',
          'guide':f"https://github.com/pret/pokegold/blob/{PIN}/maps/{g['stem']}.asm",'flags':[g['flag']],
          'region':g['region'],'area':g['loc'],'rankedboost':RB+g['slug']+'/','sprite':'','spot':'','kind':'gift','howto':'\n'.join(g['steps'])}
    if tid in by_id:by_id[tid].update(task)
    else:tasks.append(task);by_id[tid]=task

# ---- item balls and hidden items ------------------------------------------------------------------------------------
HM=re.compile(r'\b(Surf|Cut|Strength|Rock Smash|Flash|Waterfall|Whirlpool)\b')
AREA_NEEDS={'whirl islands':['Surf'],'dark cave':['Flash']}
def needs(t):
    found=[]
    for h in HM.findall(t.get('spot','')):
        if h not in found:found.append(h)
    for k,v in AREA_NEEDS.items():
        if k in t.get('area','').lower():
            for h in v:
                if h not in found:found.append(h)
    return found
for t in tasks:
    if t['category']!='Items' or t.get('kind') not in('ball','hidden'):continue
    m=re.search(r'in the ([\w-]+) part of (.+?), tile (\d+),(\d+)',t['detail'])
    if not m:continue
    where,area,x,y=m.groups();reg=REG.get(t['region'],'')
    lines=[f"Where: {area}{' ('+reg+')' if reg else ''}. {where.capitalize()} part of the map, tile {x},{y} (column, row counted from the top-left corner)."]
    n=needs(t)
    if t['kind']=='hidden':
        lines.append('Needs: Itemfinder'+(', '+', '.join(n) if n else '')+' (the Itemfinder is a gift from the man in Ecruteak City\'s Itemfinder House).')
        lines.append('Do: the item is invisible. Use the Itemfinder from your Bag when you are within a few tiles; when it reacts, stand next to the tile, face it and press A to pick the item up.')
    else:
        lines.append('Needs: '+(', '.join(n) if n else 'no extra HM is listed for this exact spot (you still have to be able to reach the area).'))
        lines.append('Do: walk up to the Poké Ball lying on the ground, face it and press A.')
    lines.append('Note: any day or time; one-time pickup. SaveQuest ticks it after you save in-game.')
    t['howto']='\n'.join(lines)

# ---- shops ------------------------------------------------------------------------------------------------------------
for t in tasks:
    if t['category']=='Items' and t.get('kind')=='shop':
        n=len(t.get('wild',[]))
        t['howto']='\n'.join([f'Where: buy it in any of the {n} shop line(s) listed below (price shown per shop).','Needs: money and a free Bag slot. Some stock only appears after story events (shown in brackets).','Note: buying is not stored in the save file, so tick it yourself.'])

CAT.write_text(json.dumps(cat,ensure_ascii=False,indent=2),encoding='utf8')
print('howto on',sum(1 for t in tasks if t.get('howto')),'of',sum(1 for t in tasks if t['category']=='Items'),'items;',len(G),'gift tasks;',len(tasks),'tasks')
