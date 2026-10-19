# SaveQuest Android — 0.1.1 alpha

Native offline completion tracker, starting with original English Pokémon Silver. This is a tested first milestone, **not a complete Silver module**. Kotlin, Jetpack Compose and Room; Android 8+ (API 26). No WebView, ads, account, telemetry, ROMs or game artwork. The app has no Internet permission; source links open the user's browser.

## Available

- File-picker import of 32 KiB battery SRAM and known 44/48-byte RTC footers. Marker/checksum validation, noncontiguous backup-block recovery, bounded reads and no writes to saves.
- 57 TM/HM bag quantities and source-mapped receipt events; never infer ownership from knowing a move. Earlier observations remain historical evidence, not current inventory.
- 331 source-mapped trainer events with individual original teams/locations, 86 unique hidden-item events, and 251 caught flags. Some trainer flags can also be set by scripts (e.g. gym leader victory), so they mean the event is recorded, not independently proven battle participation.
- Separate per-trainer manual checks, immutable deduplicated snapshots, history selection and JSON export/merge restore. Backup v2 also preserves current-snapshot selections; v1 remains readable. Duplicate backup manual records resolve to the newest timestamp. Failed imports preserve previous data.
- Optional refresh of one user-selected document on resume and through WorkManager (15-minute minimum, OS scheduling may delay). The provider must grant persistent read access; replaced/moved files may require reselection. No access bypass for Android/data or emulator private storage.
- Search/filter, exact pinned source links, and a verified original Gold/Silver walkthrough index. Location-specific walkthrough routing is still pending.

Gold and Silver SRAM layouts are indistinguishable here: the user must select a save from original English Silver. Crystal, Japanese versions, ROM hacks and emulator savestates are unsupported. Integrity checks are not cryptographic proof of game edition. Trainer ID + raw name scopes profiles; two playthroughs sharing both may collide. Separate run identities are planned.

## Build and validate

Use JDK 17 and Android SDK 36; set `JAVA_HOME` and `ANDROID_HOME` locally.

```sh
./gradlew :core:test :games:silver:test :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The checked-in catalog makes builds independent of data-source downloads. To reproduce it:

```sh
git clone https://github.com/pret/pokegold.git ../pokegold
git -C ../pokegold checkout ef0201d8daf47e8b3ea1518eacf890f37d4cd5e8
python tools/build_catalog.py ../pokegold
git diff --exit-code app/src/main/assets/catalog.json
```

`core`: game-neutral task/evidence contracts. `games:silver`: Android-free parser. `storage`: Room schema and DAO. `app`: Compose, document access, backups, refresh scheduling and composition root. Add future games via `GameModule`; UI selection/registry is a later milestone.

## Remaining milestones

1. Preserve and import real JohtoAtlas device progress with an explicit legacy export/mapping. Its global manual flags and combined inferred/manual TM ownership must not be mislabeled as save proof. No legacy device data has been copied yet.
2. Complete Silver coverage: visible items, NPC rewards, all scripted battles/rematches with conditions, encounters (all methods/times/versions), gifts/trades, party/PC, known-move hints and complete acquisition-route audit. Catalog entries are not a percentage of all game completion.
3. Curated verified walkthrough links, provenance per acquisition method, a broader real-save regression corpus, emulator/provider refresh testing and separate playthrough identities. One supplied real save has now passed the full supported catalog comparison on JVM and Android; that is not coverage of every game state.
4. Persistent release signing, update policy, database migration tests, accessibility/large-text review, background error presentation, scoped folder discovery and refined navigation.

The local debug APK is for evaluation. CI does not publish installable APKs signed with disposable keys. Export progress before changing installation/signing identity. Never uninstall JohtoAtlas to install this separate package (`dev.savequest.android`).

See [preservation](docs/PRESERVATION.md), [data provenance](docs/DATA.md), and [validation](docs/VALIDATION.md).

See [the 0.1.1 audit](docs/AUDIT-0.1.1.md) for the real-save comparison methodology, fixes, remaining limitations and release-signing policy. Private player saves are never included in source, CI or APK assets.
