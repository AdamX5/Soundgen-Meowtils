# CLAUDE.md: working rules for this repository

SoundGen is a Meowtils extension (Minecraft 1.8.9, Lunar/Forge). It turns what the player's character does in game into mouse and keyboard ASMR sounds. **Read `HANDOFF.md` first**: it covers what is built, what is verified, and the roadmap. The user guide is `docs/GUIDE.md`.

## Commands

```
build.bat                 # Windows: full build + self-test + package
./build.sh                # macOS/Linux
build.bat --no-test       # faster, skips the self-test
build.bat --install       # build + copy into the live game's meowtils\extensions
soundtool.bat <cmd> ...   # sound tool, after a build
```

The build needs a JDK 17+ and nothing else; it downloads the Meowtils reference jar into `libs/` by itself.

Outputs:
- `build/libs/SoundGen-<version>.meowtils`
- `build/dropin/` (install package: "Drop into extensions folder" + "Drop into meowtils folder")
- `build/SoundGen-<version>-dropin.zip`

## Keeping GitHub up to date (do this as part of normal work)

The user wants GitHub to hold every version of the `.meowtils` file. The CI (`.github/workflows/build.yml`) does the building and publishing; your part is to push regularly.

1. **Commit and push after every change that builds and passes the self-test.** Small, clear commits. Every push is built on GitHub, and the build is kept as a workflow artifact.
2. **Publish a release** when a change is worth installing (a fix confirmed in game, a new feature, a new default):
   - bump `VERSION` (patch for fixes, minor for features: 0.1.0 → 0.1.1 / 0.2.0);
   - add a matching `## <version>` section at the top of `CHANGELOG.md` (it becomes the release notes);
   - commit and push. The workflow creates the GitHub Release `v<version>` with the `.meowtils` file and the install zip.
3. **After pushing,** check the workflow run succeeded. If it failed, fix it before moving on.
4. **Never commit** `build/`, `libs/*.jar`, or recordings (`recordings/`, raw takes). They are git-ignored; keep it that way.
5. **When you finish a work session,** update `HANDOFF.md`: the verification table and the roadmap.

## Code rules

- **Java 8 target** (`--release 8`). No `var`, no Java 9+ APIs.
- **Keep Minecraft out of the engine.** `engine/`, `input/` and `tools/` must not import Minecraft classes, so they stay testable offline and `soundtool` runs without the game. Only `SoundGen.java` touches Minecraft.
- **Every new Minecraft field or method** needs:
  - a stub in `buildtools/stubs/` with the exact real signature;
  - an entry in `buildtools/mappings.txt` with its Forge runtime ("SRG") name.

  How to find SRG names is explained in `mappings.txt`. Prefer names that Meowtils' own compiled code uses. The build fails on any unmapped member, by design.
- **Event handlers** (`@EventTarget`) must be declared in `SoundGen.java` itself: Meowtils only scans the module's own declared methods.
- **Game-thread hot paths** (events, ticks) must not allocate per event or block. Hand work to the engine through `AudioEngine.play`, `loopStart` and `loopStop`.
- **The audio thread** must never lock, allocate, or touch the disk.
- **Tests.** Add or extend checks in `src/test/java/.../EngineSelfTest.java` for any engine, input or tool change. It runs in every build.

## The live game folder (on the user's PC)

```
C:\Users\Adam\AppData\Roaming\.minecraft\meowtils\        (= %APPDATA%\.minecraft\meowtils)
├── extensions\SoundGen-<version>.meowtils   ← installed build (plus the user's OTHER extensions: never delete those)
├── soundgen\packs\<pack>\<slot>\*.wav       ← the user's recordings
├── meowtils.log                              ← read this first when something doesn't work
└── meowtils.json                             ← Meowtils config (SoundGen's settings are saved here)
```

- **Don't develop inside this folder.** The project lives in its own folder (cloned from GitHub). This folder is game data, shared with Meowtils and other extensions. Give Claude access to it with `/add-dir %APPDATA%\.minecraft\meowtils` so it can read `meowtils.log` and the packs.
- **Installing a build:** `build.bat --install` replaces only older `SoundGen-*.meowtils`. The game must be closed while copying, then restarted to load the new version.
- **Hands off** `%APPDATA%\lunarclient\Meowtils-Lunar\`: that's Lunar's loader for the Meowtils mod itself.

## Facts already established (don't re-derive them)

- Extensions go in `<game dir>/meowtils/extensions/*.meowtils`. SoundGen's data goes in `<game dir>/meowtils/soundgen/packs/<pack>/<slot>/`.
- Meowtils event hook points and the name-translation behaviour are listed in `HANDOFF.md`, section 2.
- **The extension runs in Lunar** (confirmed by the user on 0.1.1). The 0.2.0 features are not re-tested yet: see HANDOFF.md, section 6. Ask the user for Debug Readout lines, and read `meowtils.log`, when diagnosing.
- Meowtils buttons use size `5f` (every Meowtils module does). Smaller values render unreadably small text.

## Working with the user

- Plain explanations, step by step, Windows-first (the user plays on Lunar on Windows).
- British English.
- Say clearly what is verified and what is assumed.
