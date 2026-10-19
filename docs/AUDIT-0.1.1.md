# 0.1.1 audit — 19 October 2026

## Comparison scope

A user-supplied 32,768-byte original English Silver save was read locally. Both primary and backup markers/checksums were valid. Its SHA-256 was recorded before testing and checked afterward. It was never committed, uploaded to GitHub, packaged in an APK, or edited.

All 725 supported catalog entries matched the existing JohtoAtlas JavaScript parser's current-save evidence. The same baseline was exercised through the Kotlin JVM parser and through Android import, Room storage and export/restore on Android 14 (Retroid Pocket Classic) and Android 16 (emulator). Player-specific results and the searchable full comparison remain local deliverables, not release assets.

This is not just agreement between inherited parsers: the English GS offsets for badges, party, caught/seen, TM/HM bag, event flags and checksums were also checked against [PKHeX's SAV2Offsets](https://github.com/kwsch/PKHeX/blob/641c26ba100225f3605e5a44b61b3b16e15a3774/PKHeX.Core/Saves/Substructures/Gen12/SAV2Offsets.cs). All catalog event indexes were compared with the old independently generated source index. All 47 TM/HM receipt mappings were checked for the corresponding item token in original map scripts at pinned `pret/pokegold` commit `ef0201d8daf47e8b3ea1518eacf890f37d4cd5e8`.

## Fixed

- Missing TM11 Sunny Day receipt (`EVENT_GOT_SUNNY_DAY_FROM_RADIO_TOWER`). A consumed-copy regression now proves receipt detection after its Bag quantity reaches zero; learning the move still does not prove ownership.
- Incorrect maximum-friendship requirement for TM37 Sandstorm: original source requires at least 150. Return/Frustration Sunday thresholds are now explicit (at least 150 / below 50); their shared temporary engine flag is not misused as an individual permanent receipt.
- Ambiguous TM35 location clarified as Goldenrod Underground Warehouse. TM receipt source links now open relevant map scripts rather than only the global constants list.
- Checksummed malformed party lists were accepted. Validate species/list agreement, levels, counts and terminators. Eggs retain their underlying valid species.
- Trainer digits and punctuation rendered as question marks; decode the relevant original character codes while preserving the existing profile-key algorithm.
- Backup restore lost rollback/current selection. v2 exports view state and restores it for profiles without a current selection; merging does not overwrite an existing selection. v1 remains supported.
- Out-of-order duplicate manual records could overwrite newer records during restore; reduce duplicates by latest timestamp before merging.
- Refresh could read the old source while a source switch was being committed. Source identity, URI and current snapshot updates now share the import mutex; refresh reads the linked identity inside it.
- Foreground UI could remain stale after a worker import; listen for completed current-snapshot changes and show worker errors accurately.
- Historical view wording now distinguishes the current save from the newest stored import and makes clear that manual confirmations apply to the profile today.
- Android 16 UI test compatibility: update the test runner and Espresso to versions using supported input APIs. This was a test-harness issue, not a production input failure.

## Tests

- 4 core JVM tests; 14 parser JVM tests; 1 opt-in local real-save test covering all 725 entries, real backup recovery and a consumed-TM mutation of a copy.
- 10 catalog checks.
- 5 instrumentation tests on each of Android 14 and Android 16: Room, repository restore/refresh/deduplication, full real-save catalog round trip, actual Compose manual-toggle persistence, and offline trainer search.
- Debug APK assembly, lint, signature verification, and reproducible catalog generation. Tests run under a separate `.audit` app ID so existing normal app data is not uninstalled or cleared.
- Lint still reports nonfatal dependency/API/style warnings; zero errors is not a guarantee of zero bugs.

To run local private fixture audits, set `SAVEQUEST_AUDIT_SAVE`, `SAVEQUEST_AUDIT_CASES` (TSV: id, category, bagIndex/-1, dexNumber/-1, comma-separated event indexes, expected evidence kind), and optionally `SAVEQUEST_AUDIT_OUTPUT`. Without these variables, the local-only JVM test is skipped. Android local-only tests require opt-in fixture injection into the audit app's private files after installation; they are skipped in ordinary runs. Never place fixtures in Git-tracked resources or APK assets.

## Known gaps and constraints

The legacy generated reference contains 98 visible-item rows, 73 NPC rows and encounters across 114 areas that are not yet exposed by SaveQuest. Party/PC views, complete scripted battles/rematches and detailed walkthrough routing remain unfinished. The 331 trainer event tasks are not an exhaustive trainer-battle completion count; some event bits can be set indirectly by story scripts. Old browser/WebView manual progress is not in SRAM and was not migrated or compared.

Gold/Silver SRAM layouts cannot reliably distinguish edition here; Crystal/Japanese/hacks/savestates remain unsupported. Same trainer ID and name can collide across separate runs. Manual state shown with a historic snapshot is current profile-level manual state, not a historical manual audit log. Actual emulator file replacement, revoked document permissions and background battery scheduling still require a provider/device matrix; file imports and refresh were exercised locally, not a full end-to-end emulator-write campaign.

## Release

`v0.1.1-alpha` is a GitHub **prerelease**. The APK uses the same persistent local debug certificate as the initial alpha to update it without uninstalling user data. It is an evaluation build, not a production-signed release. CI never publishes an independently debug-signed APK as an update. A secure production signing/upgrade policy remains outstanding. This release does not archive or modify JohtoAtlas.
