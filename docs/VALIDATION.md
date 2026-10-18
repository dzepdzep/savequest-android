# Milestone validation — 18 October 2026

Validated locally using JDK 17, Gradle 8.13 and SDK 36:

- 10 JVM Silver parser tests: current bag vs consumed receipt; learned move is not proof; caught bit boundaries; zero-filled/corrupt/truncated/oversized file rejection; known RTC footers; trainer identity; backup recovery without mutation; invalid party count.
- 4 JVM evidence tests: manual preservation, historical distinction, clearing a manual check without erasing save proof, and unknown absence.
- 7 Python catalog checks: unique IDs, original Falkner team, individual Joey team, all 57 bag positions, 251 caught positions, pinned source URLs, Rollout receipt mapping.
- 3 Android tests on Retroid Pocket Classic / Android 14: Room deduplication and profile separation; repository import/refresh/rollback/export/restore including malformed backup and newer manual preservation; Compose offline trainer search and original Falkner team display.
- Debug APK assembly and Android lint. Lint passes with warnings (older pinned dependencies/target API, backup configuration and conservative application-context singleton warning); this is not a warning-free production release.

Initial UI test failures were corrected: the handheld display was asleep, then the assertion ambiguously matched the search text as well as the trainer title. The final test scrolls to and checks the team entry. The screen was changed to one scrolling list to accommodate small handheld displays.

Not yet validated: a user-supplied real save, every game event/receipt branch, all walkthrough links, real emulator writes through each Android document provider, background timing under battery restrictions, old app migration, accessibility matrix or release-signed upgrade path. Synthetic fixtures must not be mistaken for real-save coverage. CI runs JVM/catalog tests, build and lint; device tests were run locally.
