"""Catalog regressions for original Silver facts and evidence mapping."""
import json,unittest
from pathlib import Path
CATALOG=json.loads((Path(__file__).resolve().parents[1]/'app/src/main/assets/catalog.json').read_text(encoding='utf8'))
TASKS={t['id']:t for t in CATALOG['tasks']}
class CatalogTest(unittest.TestCase):
    def test_unique_stable_ids(self):self.assertEqual(len(TASKS),len(CATALOG['tasks']))
    def test_original_falkner_team_not_remake(self):self.assertEqual(TASKS['trainer:EVENT_BEAT_FALKNER']['detail'],'Lv 7 Pidgey / Lv 9 Pidgeotto')
    def test_specific_trainer_not_first_party_of_class(self):self.assertEqual(TASKS['trainer:EVENT_BEAT_YOUNGSTER_JOEY']['detail'],'Lv 4 Rattata')
    def test_tm_hm_bag_layout(self):self.assertEqual(sorted(t['bagIndex'] for t in TASKS.values() if 'bagIndex' in t),list(range(57)))
    def test_all_original_dex_bits(self):self.assertEqual(sorted(t['dexNumber'] for t in TASKS.values() if 'dexNumber' in t),list(range(1,252)))
    def test_exact_source_urls(self):
        for t in TASKS.values():
            if t.get('guide'):self.assertIn(CATALOG['sourceCommit'],t['guide'])
    def test_receipt_for_consumed_rollout(self):self.assertTrue(TASKS['TM04']['flags'])
    def test_sunny_day_receipt_and_radio_tower_source(self):
        self.assertTrue(TASKS['TM11']['flags'])
        self.assertTrue(TASKS['TM11']['guide'].endswith('/maps/RadioTower3F.asm'))
    def test_sandstorm_threshold_not_maximum(self):self.assertIn('at least 150',TASKS['TM37']['location'])
    def test_sleep_talk_warehouse(self):self.assertIn('Underground Warehouse',TASKS['TM35']['location'])
    def test_item_spots_have_flags_regions_and_links(self):
        spots=[t for t in TASKS.values() if t.get('region') and t['id'].startswith('item:')]
        self.assertGreaterEqual(len(spots),200)
        for t in spots:
            self.assertTrue(t['flags']);self.assertIn(t['region'],('johto','kanto'))
            if t.get('rankedboost'):self.assertTrue(t['rankedboost'].startswith('https://rankedboost.com/pokemon/location/gold-silver/'))
    def test_heracross_only_from_rare_headbutt_trees(self):
        w=TASKS['dex:214']['wild'];self.assertTrue(w);self.assertTrue(all('RARE trees only' in x for x in w))
    def test_wild_lines_are_well_formed(self):
        for t in TASKS.values():
            for line in t.get('wild',[]):self.assertEqual(len(line.split('\t')),4)
    def test_items_are_split_by_kind_and_region(self):
        items=[t for t in TASKS.values() if t['category']=='Items']
        self.assertEqual({t['kind'] for t in items},{'gift','ball','hidden','shop'})
        self.assertTrue(all(t['region'] in('johto','kanto') for t in items if t['kind']!='shop'))
    def test_tm_hm_have_regions_and_places(self):
        tms=[t for t in TASKS.values() if t['category']=='TM/HM']
        self.assertEqual(len(tms),57);self.assertTrue(all(t['wild'] for t in tms))
        self.assertIn('kanto',TASKS['TM03']['region']);self.assertIn('johto',TASKS['TM01']['region'])
    def test_shops_and_buyable_tms(self):
        shops=[t for t in TASKS.values() if t.get('kind')=='shop' and t['category']=='Items']
        self.assertGreaterEqual(len(shops),40)
        self.assertTrue(all(t['wild'] and not t['flags'] for t in shops))
        self.assertEqual(TASKS['TM48']['kind'],'shop');self.assertEqual(TASKS['TM04']['kind'],'found')
        self.assertTrue(all(t.get('kind') in('shop','prize','found') for t in TASKS.values() if t['category']=='TM/HM'))
    def test_every_item_has_how_to_get(self):
        items=[t for t in TASKS.values() if t['category']=='Items']
        self.assertTrue(all(t.get('howto','').count('\n')>=2 for t in items))
        self.assertTrue(all(t['howto'].startswith('Where:') for t in items))
    def test_weekday_gifts_name_their_day(self):
        for ev,day in (('SHARP_BEAK_FROM_MONICA','MONDAY'),('PINK_BOW_FROM_TUSCANY','TUESDAY'),('BLACKBELT_FROM_WESLEY','WEDNESDAY'),('HARD_STONE_FROM_ARTHUR','THURSDAY'),('POISON_BARB_FROM_FRIEDA','FRIDAY'),('SPELL_TAG_FROM_SANTOS','SATURDAY'),('MAGNET_FROM_SUNNY','SUNDAY')):
            self.assertIn(day,TASKS['npc:EVENT_GOT_'+ev]['howto'])
    def test_story_gated_gifts(self):
        self.assertIn('ALL 16 badges',TASKS['npc:EVENT_GOT_HP_UP_FROM_VERMILION_GUY']['howto'])
        self.assertIn('Plain Badge',TASKS['npc:EVENT_GOT_SQUIRTBOTTLE']['howto'])
        self.assertIn('Cut',TASKS['npc:EVENT_GOT_CHARCOAL_IN_CHARCOAL_KILN']['howto'])
        self.assertIn('party',TASKS['npc:EVENT_GOT_EEVEE']['howto'])
        self.assertEqual(TASKS['npc:EVENT_GOT_RAINBOW_WING']['flags'],[120]);self.assertEqual(TASKS['npc:EVENT_GOT_SILVER_WING']['flags'],[121])
    def test_hidden_items_need_itemfinder_and_balls_do_not(self):
        for t in TASKS.values():
            if t['category']=='Items' and t['kind']=='hidden':self.assertIn('Itemfinder',t['howto'])
            if t['category']=='Items' and t['kind']=='ball':self.assertNotIn('Itemfinder',t['howto'])
    def test_hm_requirements_are_listed(self):
        self.assertIn('Rock Smash',TASKS['item:EVENT_BURNED_TOWER_1F_X_SPEED']['howto'])
    def test_gift_flags_are_unique(self):
        flags=[t['flags'][0] for t in TASKS.values() if t['category']=='Items' and t['kind'] in('gift','ball','hidden')]
        self.assertEqual(len(flags),len(set(flags)))
    def test_rankedboost_userscript_is_current_and_passes_its_own_tests(self):
        import importlib.util,shutil,subprocess
        spec=importlib.util.spec_from_file_location('bru',Path(__file__).with_name('build_rankedboost_userscript.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
        text,flags,pages=m.build()
        for f in (m.OUT,m.APP):self.assertEqual(f.read_text(encoding='utf8'),text,str(f)+' is stale: run python tools/build_rankedboost_userscript.py')
        self.assertGreaterEqual(flags,190)
        if shutil.which('node'):
            r=subprocess.run(['node',str(Path(__file__).with_name('rankedboost')/'test_userscript.js')],capture_output=True,text=True)
            self.assertEqual(r.returncode,0,r.stdout+r.stderr)
if __name__=='__main__':unittest.main()
