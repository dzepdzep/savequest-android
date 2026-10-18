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
if __name__=='__main__':unittest.main()
