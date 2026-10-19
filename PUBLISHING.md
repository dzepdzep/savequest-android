# Publishing SaveQuest (beginner guide)

One-time setup
1. Install Git: https://git-scm.com/download/win (keep defaults).
2. Optional but easiest: install GitHub CLI (https://cli.github.com), then run `gh auth login` once and follow the browser sign-in. Without it the script opens the GitHub page and you drag the APK in yourself.
3. Install JDK 17 and Android Studio as for normal builds (the script uses `gradlew.bat`).

Every release
1. Double-click `tools\publish.bat`.
2. Type a version higher than the last (e.g. `0.1.2-alpha`) and a short note.
3. It runs the tests, builds the APK with your local debug certificate (so updates keep your data), shows the changed files, and waits for you to type YES.
4. It pushes, tags and creates the GitHub prerelease with the APK attached (or opens the page for you to drag the APK in; it is in the `release` folder).

Why not automatic on GitHub? The project policy is that CI never publishes an APK signed with a throwaway key; the APK has to come from your PC. GitHub still builds and tests every push.

Optional sprites: `py -3 tools\build_sprites.py <pokegold clone>` makes local pictures. They are git-ignored, and the script refuses to publish while they exist.
