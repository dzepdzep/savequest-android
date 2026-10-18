# JohtoAtlas preservation — 18 October 2026

- Inspected private `dzepdzep/JohtoAtlas` default branch at `a4dcf81afb7e0a25fc5aa2dc77768c35291e4f8e` (TM v0.9 preview).
- Mirrored all 51 advertised references including branches, tags and PR references; created `JohtoAtlas-before-savequest.bundle`. `git bundle verify` confirmed complete history. This includes prior experiments and source/data builders, not just main.
- Added and pushed tag `savequest-preservation-2026-10-18` at that exact commit. No source branch was altered, deleted or archived.
- Preserved TM/HM acquisition notes and receipt mapping as inputs in the new repository. All old source remains in the full bundle and old GitHub repository.
- Git tracked no player battery saves. Git backup does **not** include Issues, release assets, Actions secrets, signing keys, browser storage or device-local progress.
- The accessible Retroid Pocket Classic did not report an installed `johto` package; another connected device was unauthorized. No private app storage or user saves were extracted, changed or deleted.

## Device progress before any eventual archive

Keep the old app installed. Preserve emulator battery files separately. Existing app manual progress is WebView local storage (`johtoAtlasManual`, `johtoAtlasTMHMOwnersV1`), and browser versions have their own storage per origin. TM ownership mixes observations and manual entries: migrate it as legacy confirmation, not verified automatic evidence. Original progress must be exported from the actual device/browser before migration can be called complete.

No archive is appropriate until real progress is backed up, migration is verified, SaveQuest's coverage is adequate, and the user accepts replacement. A repository archive would not preserve device progress.

Restore source backup with `git clone JohtoAtlas-before-savequest.bundle JohtoAtlas-restored`. Keep the bundle private. Hash and backup manifest are supplied beside the APK in the local deliverables.
