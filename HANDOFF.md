# SoundGen: project handoff

This document is for whoever picks the project up next (you or a new Claude session on your PC). It covers what SoundGen is, what has been built, what is verified, and what comes next.

Status markers:
- **Verified**: proven by reading the Meowtils 2.0.1 jar, or by this project's automated tests.
- **Inferred**: a reasoned conclusion that hasn't been tested in game.
- **Open**: unknown; needs the user or an in-game test.

---

## 1. What SoundGen is

A **Meowtils extension** for Minecraft 1.8.9 (Lunar or Forge). It turns what the player's character does in game into mouse and keyboard sounds, for ASMR recordings:
- **right click** → drag-click sounds;
- **left click** → butterfly-click sounds;
- **W A S D, Space, Shift, Sprint** → key press and release sounds.

The key idea: it reacts to what the *game* does, not only to the physical input. Holding right click with fast place still produces drag-click sound, even though a raw-input CPS counter (like Lunar's) shows 0.

The output can go to a virtual audio cable, so it can be recorded in OBS next to the user's mic (a Maono G1 Neo) or mixed into the mic signal.

---

## 2. How it plugs in (verified from the Meowtils 2.0.1 jar)

- **Extensions are separate files.** Meowtils loads every `*.meowtils` file in `<game dir>/meowtils/extensions/`. The file is a jar whose `META-INF/meowtils.extension` contains `main=<class>`, and Meowtils calls that class's static `init()`. **Meowtils itself is never modified.**
- **Settings API.** An extension subclasses `wtf.tatp.meowtils.extension.Extension`. Settings are `@Config` fields bound with `check` / `slider` / `mode` (dropdown) / `text` / `button` / `expand`.
- **Events.** Handlers are `@EventTarget` methods. Meowtils' `EventManager` only scans the registered object's own declared methods, so the handlers must live in `SoundGen.java` itself, not in a parent class.
- **Events SoundGen uses, and where Meowtils fires them** (from its mixin code):

| Event | Fired at |
|---|---|
| `MouseInputEvent` | start of the game's own `clickMouse` / `rightClickMouse`, so fast-place repeats are seen |
| `PlayerInteractEvent` RIGHT_CLICK_BLOCK | start of `PlayerControllerMP.onPlayerRightClick` (each placement attempt) |
| `AttackEntityEvent` | start of `PlayerControllerMP.attackEntity` |
| `SendPacketEvent` | `NetworkManager.sendPacket` |
| `ClientTickEvent` PRE/POST | start and end of `runTick` |
| `RenderTickEvent` PRE | start of each frame |

- **Name translation.** Minecraft names are translated as the extension loads: on Lunar, Meowtils' `MappingClassLoader` converts Forge runtime ("SRG") names to readable ("MCP") names using its bundled `mappings/*.csv`.
  - So a correct extension contains **SRG names**, which is also what Forge's own build step ("reobf") produces.
  - Our build does this step itself (`buildtools/Reobf.java` + `buildtools/mappings.txt`).
  - A check confirmed that all 73 Minecraft references in the built file translate back correctly through Meowtils' own table.

---

## 3. What has been built

### Engine (`src/main/java/.../engine`, no Minecraft code)

- **Thread design.** The audio runs on its own thread and only ever writes to one output device. The game thread just drops small commands into a fixed-size queue: nothing is allocated, no locks are shared with the audio thread, and the game never does audio work.
- **Mixing.** Up to 48 sounds at once (the oldest one-shot is dropped first when full). Sounds are scheduled to the exact audio frame from game timestamps. Output is 48 kHz, 16-bit stereo, with a selectable device and buffer size.
- **Loading.** All sounds are decoded into memory up front. The WAV reader is hand-written so it handles float and "extensible" WAVs, which Java 8's built-in reader rejects. Other sample rates are converted to 48 kHz.
- **Auto Trim.** Cuts the silence before each hit so timing stays tight.
- **Seamless loops.** The end of each drag-loop take is crossfaded into its start.
- **Variations:**
  - Shuffle order (every take plays once before any repeats) or Random.
  - Match Levels evens loudness between takes, by at most ±12 dB.
  - Random volume, pitch and timing variation per hit.
- **Placeholders.** Synthesised stand-in sounds fill every slot until recordings exist.
- **Measured cost:** about 0.6% of one CPU core at an extreme ~3,000 clicks/s (offline benchmark).

### Detection (`input/` and `SoundGen.java`)

- **Every action has several "read methods",** chosen per action with a dropdown. All methods are watched and counted at the same time; only the selected one makes sound. The **Debug Readout** prints each method's count every second, so you can compare them live.

| Action | Methods |
|---|---|
| Right click | Game Click, Placement, Packet, Keybind, Raw Mouse |
| Left click | Game Click, Attack, Packet, Keybind, Raw Mouse |
| W/A/S/D | Keybind, Movement, Raw Key |
| Space | Keybind, Movement, Raw Key, Physics (jump take-off; skips knockback) |
| Shift | Keybind, Movement, Player (visibly crouching), Raw Key, Packet |
| Sprint | Keybind, Player, Raw Key, Packet |

- **Key releases:** Real (when the key goes up), Synthetic (a set time after the press) or Off. **Min Hold** stops 1-tick taps from playing press and release on top of each other.
- **Click timing:**
  - **Spread** (default): clicks handled in the same 50 ms game tick are spaced out over the next tick, so they play about 50 ms late.
  - **Instant:** no delay, but those clicks stack.
- **Drag style:** **Clicks** (one sample per click) or **Loop** (a continuous rattle that fades out when clicking stops, plus an optional end click).

### Tools

- **`soundtool`** (`soundtool.bat` / `.sh`, runs from the built `.meowtils` file with plain Java):
  - `split`: cuts a long take into numbered variations. Re-recordings are numbered after existing files and never overwrite them; key press/release takes are paired by timing.
  - `report`: pack health per slot.
  - `preview`: renders a slot through the real engine to a WAV.
  - `init`: creates a pack's folders.
- **`build.bat` / `build.sh`:** one-command build. Needs only a JDK 17+; no Gradle and no Forge setup. It produces the `.meowtils` file, a drag-in folder and a zip.
- **GitHub Actions** (`.github/workflows/build.yml`): builds and tests every push. When `VERSION` holds a version with no release yet, it publishes a GitHub Release with the `.meowtils` file and the drag-in zip attached.

### Documentation

- `docs/GUIDE.md`: step-by-step for the user, from install through recording, splitting, tuning and mic routing.
- `README.md`: overview.
- `CHANGELOG.md`: becomes the release notes.
- `CLAUDE.md`: working rules for Claude in this repo.

---

## 4. Verification status

| Item | Status |
|---|---|
| Meowtils extension API, events and loading paths | **Verified** (read from the 2.0.1 jar) |
| Extension compiles against the real Meowtils API | **Verified** |
| Engine timing, loops, voice limit, WAV formats, trim, splitter, pairing, shuffle | **Verified** (self-test, 29 checks) |
| SRG name translation of the built file | **Verified** (round-trip through Meowtils' own table) |
| The extension loads and runs inside Lunar | **Open**: never run in game yet |
| Lunar's game directory is `%APPDATA%\.minecraft` | **Verified** on the user's PC: `C:\Users\Adam\AppData\Roaming\.minecraft\meowtils\` holds meowtils.json, meowtils.log, extensions\ |
| User runs Meowtils 2.0.1 on Lunar via `%APPDATA%\lunarclient\Meowtils-Lunar\` (Meowtils.jar + lunar-agent.jar) | **Verified** (user screenshot; jar size matches 2.0.1). That folder is the mod loader, not where extensions go |
| Meowtils' hooks behave the same on Lunar as on Forge | **Inferred** (Meowtils advertises Lunar support) |
| Audio latency on the user's Windows drivers | **Open** |
| Physics jump detection on slime blocks, ladders and some knockback | **Inferred**: may misfire |
| Behaviour if Meowtils hot-reloads extensions without calling `onDisable` | **Open**: the old audio thread might keep the device; workaround is to restart the game |

---

## 5. Decisions and why

- **Detection hooks the game's own click code, not raw input or packets.** That's where fast place and similar modules are visible. Packets and raw input are still offered as alternatives for comparison.
- **Every read method is always counted.** The user wanted to pick the right method for each action by testing, and seeing the counts side by side makes that possible.
- **Spread timing is the default.** The game handles clicks in 50 ms steps, so clicks from the same step all happen at one instant and would otherwise play stacked together.
- **Own build instead of ForgeGradle.** ForgeGradle 2.1 needs Java 8 and old Forge download servers. The one-command build needs only a JDK, and runs the same on the user's PC and on GitHub.
  - Trade-off: every new Minecraft field or method used in code must be added to `buildtools/mappings.txt` (and to `buildtools/stubs`). The build fails loudly if one is missing.
- **Built files live on GitHub Releases, not in git.** Binaries in the repository bloat it. Releases also give each version a clean download page.

---

## 6. Roadmap

### Next: first in-game run (needs the user)

1. Install the drag-in from the latest release (`docs/GUIDE.md` Part 1).
2. Confirm SoundGen appears in Meowtils and the chat lines show up. If not, get `meowtils/meowtils.log` lines and fix.
3. Work through GUIDE Part 3 with the Debug Readout: fast place, butterfly, legit scaffold, sprint reset, jumping and knockback. Paste the readout lines back.
4. Choose default read methods from those results and change the defaults in `SoundGen.java`.

### Then

5. **Recording pack v1** (GUIDE Parts 4–5): butterfly 40+, drag 40+, drag_loop 6+, keys 20+ each.
6. **Timing tuning:** Spread vs Instant, jitter amounts, loop hold timeout, synthetic release time.
7. **Routing:** VB-Cable → OBS separate track, with a −50 ms sync offset when using Spread timing.
8. **Open question from the user:** what "click hit" means (hit select? block-hitting?). It decides whether cancelled clicks should make sound.

### Ideas, not started

- **Speed-matched drag loops:** tag takes with their speed in the file name (`cps18_take1.wav`) and pick the take closest to the current right-click rate.
- **Separate hit sound:** a `butterfly_hit` slot for clicks that land on an entity.
- **Per-key volume and stereo position** (W left of centre, D right, and so on).
- **On-screen debug panel** instead of chat lines.
- **Clean shutdown on reload:** detect Meowtils' extension reload and close the old audio thread.
- **Soft and hard layers:** choose softer or harder takes based on the time between clicks.

---

## 7. Where everything is

```
VERSION                     version number; bump it to publish a release
CHANGELOG.md                release notes, one "## <version>" section each
build.bat / build.sh        one-command build -> build/libs, build/dropin, build/*.zip
soundtool.bat / .sh         sound tool (after building)
buildtools/Build.java       the build itself
buildtools/Reobf.java       Minecraft name translation step
buildtools/mappings.txt     readable -> Forge runtime names (with sources)
buildtools/stubs/           compile-only Minecraft class stand-ins
src/main/java/meowtils/extension/soundgen/
  Main.java                 entry point (registers the module)
  SoundGen.java             settings, events, read methods (the only Minecraft-facing code)
  engine/                   audio engine, sample loading, WAV decoding, preview
  input/                    click/key channels, timing
  tools/                    soundtool, splitter, WAV writer
src/main/resources/META-INF/meowtils.extension   main=meowtils.extension.soundgen.Main
src/test/java/.../EngineSelfTest.java            offline self-test (runs in every build)
.github/workflows/build.yml                      CI build + automatic releases
docs/GUIDE.md                                    user guide
```
