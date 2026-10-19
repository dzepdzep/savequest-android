# Data and parser provenance

Original Gold/Silver disassembly: [pret/pokegold at ef0201d8](https://github.com/pret/pokegold/tree/ef0201d8daf47e8b3ea1518eacf890f37d4cd5e8).

`tools/build_catalog.py` checks this exact commit. It joins trainer class order in `party_pointers.asm`, IDs in `trainer_constants.asm`, every party in `parties.asm`, and map trainer declarations. It parses 495 party variants; 331 currently appear in the event-mapped catalog. Unmapped scripted battles and rematches are not claimed as complete. A shared event is one task even if referenced by multiple maps.

Event indexes come from `constants/event_flags.asm`, including `const_def`, `const_next` and `const_skip`. TM receipt names inherited from JohtoAtlas are checked for presence in both constants and map scripts; acquisition prose has not been completely independently audited. No remake/Crystal data is intentionally used.

Parser layout inherited from working JohtoAtlas parser and cross-checked against `ram/sram.asm`, `engine/menus/save.asm` and `constants/misc_constants.asm`. Primary markers at 0x2008/0x2d6b must be 99/127; checksum covers 0x2009–0x2d68. Backup markers at 0x7e38/0x7e6f and three summed ranges validate recovery. Party count, terminator, species agreement and levels are validated. Synthetic regressions and an opt-in real-save audit are documented in [the 0.1.1 audit](AUDIT-0.1.1.md); broader save/provider coverage remains outstanding.

Evidence: Bag > receipt/save flag > manual confirmation > historical observation > unknown. Manual state remains stored independently regardless of display precedence. History uses import timestamps, which are not game chronology. Reimporting a known save selects that stored snapshot without creating duplicates. Future snapshots do not count as evidence when viewing an older one. Rollbacks and forks need explicit run identities in a later milestone.

Entry source URLs point to immutable source files. The global walkthrough button opens [Bulbapedia's original Gold/Silver walkthrough](https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Gold_and_Silver), verified on 18 October 2026; its index explicitly distinguishes original Game Boy Color games from the remakes. Location-specific guide routing remains pending. RankedBoost directory access was attempted but the research fetch exceeded its content-size limit; no broad claim of verified RankedBoost links is made.

Android document permissions: https://developer.android.com/training/data-storage/shared/documents-files

WorkManager schedule limits: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work

The app bundles derived facts and authored code, not disassembly text, ROM bytes, game graphics or dialogue. Pokémon is a trademark of its owners; this is an unofficial project. No upstream license grant is implied.

