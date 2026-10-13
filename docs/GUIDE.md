# SoundGen step-by-step guide

This guide goes from an empty PC to recording, cutting and tuning many sound variations. Part 1 installs a ready build by drag-and-drop; Part 1b is only for working on the code; Parts 2–3 cover first launch and testing; Parts 4–6 cover recording, splitting and tuning.

Confidence markers:
- **(checked)**: confirmed by reading the Meowtils 2.0.1 jar, or by this project's tests.
- **(assumed)**: a reasonable guess that nobody has tested on your machine yet.

---

## 0. How the pieces fit (read once)

**You never edit Meowtils.** Meowtils is the mod you already run. It loads *extensions*: separate files ending in `.meowtils`, placed in a folder it watches. **(checked)**

```
<game dir>/
└── meowtils/
    ├── meowtils.json          ← Meowtils' own config (proves you found the right folder)
    ├── extensions/
    │   └── SoundGen-0.1.0.meowtils   ← what this project builds (you copy it here)
    └── soundgen/
        └── packs/
            └── default/       ← your recordings: one folder per sound slot
                ├── butterfly/  butterfly_001.wav, butterfly_002.wav, …
                ├── drag/
                ├── drag_loop/
                ├── key_down/   key_up/   space_down/ …
                └── README.txt
```

You never need to download or touch a Meowtils jar for the build either: the build fetches its own copy into `libs/` as a **compile reference only**. It is never changed and never shipped. **(checked)**

There are three separate things you will work with:

| Thing | Where it lives | Changed by |
|---|---|---|
| The code | the project folder on your PC ↔ GitHub | you and Claude, committed and pushed |
| The built extension | GitHub **Releases** (automatic), or `build\libs\` after a local build | the build |
| The sounds | `<game dir>\meowtils\soundgen\packs\<pack>\` | you, via recording and the sound tool |

---

## Part 1. Get it running (no building needed)

### 1.1 Download

1. Open the repository's **Releases** page on GitHub: `https://github.com/AdamX5/Soundgen-Meowtils/releases`.
2. From the newest release, download **`SoundGen-<version>-dropin.zip`**.
3. Unzip it anywhere. Inside are two folders, named after where their contents go, plus `HOW-TO-INSTALL.txt`:

```
SoundGen-<version>-dropin
├── Drop into extensions folder
│   └── SoundGen-<version>.meowtils
├── Drop into meowtils folder
│   └── soundgen\packs\default\…   (one empty folder per sound slot)
└── HOW-TO-INSTALL.txt
```

### 1.2 Install

Your **meowtils folder** is `%APPDATA%\.minecraft\meowtils` (paste that into the Explorer address bar). It contains `meowtils.json` and an `extensions` folder. **(checked on the user's PC)**

1. Close Minecraft/Lunar.
2. From **"Drop into extensions folder"**: drag `SoundGen-<version>.meowtils` into `meowtils\extensions`. Delete any older `SoundGen-*.meowtils` there: keep only one version.
3. From **"Drop into meowtils folder"**: drag the `soundgen` folder into `meowtils`, and merge if Windows asks.
   - These are the folders you'll record into.
   - This step is optional on a first install: SoundGen creates the same folders the first time it's enabled.
   - It never overwrites recordings; the folders only contain a README.

Result:

```
.minecraft\meowtils
├── extensions\SoundGen-<version>.meowtils
└── soundgen\packs\default\…
```

> **Not this folder:** `%APPDATA%\lunarclient\Meowtils-Lunar\` (contains `Meowtils.jar`, `lunar-agent.jar`, `agent-mods.json`). That is where Lunar loads the Meowtils mod itself from. SoundGen does not go there. **(checked on the user's PC)**

Then continue with **Part 2.2**.

---

## Part 1b. Building yourself (only for working on the code)

### Tools

1. **Git** from git-scm.com, default options.
2. **JDK 21** (Eclipse Temurin 21 from adoptium.net, choose *JDK*, enable "Set JAVA_HOME" and "Add to PATH"). Any JDK 17+ works; the build still targets Java 8 so it runs on Forge and Lunar. **(checked)**
3. **Audacity** for recording, and optionally **VB-Audio Cable** (Part 7).

Check in a new terminal: `java -version` shows 17 or newer.

### Project folder

Put the project in a normal folder, **not** inside the game directory, for example:

```
cd %USERPROFILE%\Documents
mkdir Projects
cd Projects
git clone https://github.com/AdamX5/Soundgen-Meowtils.git
cd Soundgen-Meowtils
```

### Build

```
build.bat
```

- It downloads the Meowtils reference jar on first run, compiles, runs the self-test, translates Minecraft names for the game, and packages.
- Output: `build\libs\SoundGen-<version>.meowtils`, the install package `build\dropin\` (the same two folders as the release zip), and `build\SoundGen-<version>-dropin.zip`.
- Install exactly as in 1.2, using the folders in `build\dropin\`.
- `build.bat --no-test` skips the self-test for a faster build.
- `build.bat --install` also copies the new `.meowtils` straight into `%APPDATA%\.minecraft\meowtils\extensions`, replacing only older SoundGen files (other extensions are never touched). Close the game first, then restart it afterwards. A different game folder can be set with the environment variable `SOUNDGEN_GAME_DIR`.

You don't need to publish anything by hand: pushing to GitHub builds automatically, and bumping `VERSION` publishes a new release (see `HANDOFF.md`).

---

## Part 2. First launch

### 2.1 Install

See Part 1.2 (release download) or Part 1b (own build).

### 2.2 Start

Start Lunar and join a world or server.

**After every update:** close the game, replace the file (delete the old version), then start again. Meowtils has an "extensions reloaded" feature **(checked)**, but SoundGen keeps an audio thread and the audio device open. A full restart guarantees the old copy has let go of the device.

### 2.3 First run checklist

1. Open the Meowtils GUI and find the **SoundGen** module. Enable it.
2. Chat should show two lines:
   - `output: system default (20 ms buffer)`
   - `pack 'default': 0 recordings loaded, … slots on placeholders`
3. Open **Sounds → Test Sounds**. You should hear synthetic clicks, then a "space" press and release. They are placeholders and are meant to sound basic.
4. The folders `<game dir>\meowtils\soundgen\packs\default\…` now exist.

If chat shows `audio failed to open`, see Troubleshooting.

---

## Part 3. Testing the read methods (before recording anything)

The goal is to decide, for each action, which read method matches what you want, using the placeholder sounds.

1. Turn on **Debug Readout**. Every second, chat shows what each method saw, with the selected one underlined:

   ```
   [SG] R/s game 20 place 20 pkt 20 key 1 raw 1 | L/s game 9 atk 3 pkt 9 key 9 raw 9
   [SG] Shift key 2 move 2 player 2 raw 2 pkt 2 | Space key 1 move 1 raw 1 phys 1
   ```

2. Run each scenario and note which method's numbers match what you want to hear:

| Scenario | What you'd expect | What to check |
|---|---|---|
| Normal right-click drag-clicking on blocks | game ≈ place ≈ pkt ≈ your CPS | Is `raw` close too? If yes, any method works. |
| Holding right click with fast place | game/place/pkt ≈ 20, raw/key ≈ 0 | This is the gap you described. Pick **Game Click** or **Placement**. |
| Butterfly clicking at air | game ≈ pkt ≈ raw, atk = 0 | Attack only counts hits on players or mobs. |
| Butterfly clicking a player | atk ≤ game | Pick **Game Click** to hear every click, or **Attack** to hear hits only. |
| Legit scaffold | Shift: `player`/`pkt` toggle at each edge | Does `raw` stay 0 because a module is crouching for you? Then pick **Player** or **Packet**. |
| Sprint reset (W-tap) | W `move` edges match the taps | Test W read = **Movement** vs **Keybind**. |
| Jumping, and taking knockback | `phys` counts jumps only | If knockback triggers it, use **Movement** or **Keybind** for Space. |

3. For keys, try each **Release** mode (Real, Synthetic, Off) and listen.
4. Try **Timing → Click Timing**: **Spread** spaces out clicks evenly but plays 50 ms late; **Instant** has no delay but clicks in the same tick stack.

Write down your choices; they are saved by Meowtils' config. Send me anything that looks wrong (paste the debug lines). That is how we tune the detection.

---

## Part 4. Recording variations

### 4.1 Why many variations matter

Hearing the same recording twice in a row is the fastest way for ASMR to sound fake. SoundGen plays a pack in **Shuffle** order by default: every variation in a folder plays once, in random order, before any repeats. More variations mean longer before anything repeats.

`soundtool report` flags any slot below these targets:

| Slot | Minimum to aim for | Notes |
|---|---|---|
| `butterfly` | 40+ (60–100 is great) | the one you'll hear most |
| `drag` | 40+ | only used when Right Style = Clicks |
| `drag_loop` | 6+ takes, 2–5 s each | used when Right Style = Loop or Auto |
| `drag_end` | 8+ | the final clack when a drag stops (optional) |
| `right_click` | 20+ | single right clicks for Right Style = Auto (falls back to `drag`) |
| `key_down` / `key_up` | 20+ each | **all normal-size (1u) keys**: letters, numbers, Esc, F-keys, arrows, Page Up/Down, Ctrl/Win/Alt |
| `tab_down` / `tab_up` | 12+ each | **Tab and Backslash** (1.5u) |
| `shift_down` / `shift_up` | 12+ each | **both Shifts and Caps Lock**, in game and when typing |
| `backspace_down` / `backspace_up` | 12+ each | **Backspace and Enter** (2u) |
| `space_down` / `space_up` | 12+ each | **the space bar**: the most distinct key |
| `other_*`, `w_*`, `sprint_*` … | optional | per-key overrides; empty folders fall back step by step (`tab_down` → `other_down` → `key_down`) |

Fn can't have a sound: the keyboard handles it internally and never reports it to the PC.

**What to record first** (the minimum for a full pack): `butterfly`, `right_click`, `drag_loop`, `drag_end`, `key_down`/`key_up`, `space_*`, `shift_*`, `tab_*`, `backspace_*`. Everything else falls back to these.

### 4.2 Mic and room setup (Maono G1 Neo)

1. **Fix the mic position.** Aim it at the mouse or keyboard from 10–20 cm. Tape a mark on the desk so re-records match.
2. **Turn off processing.** That includes Windows "audio enhancements", NVIDIA Broadcast or Krisp noise suppression, and any noise gate. Noise suppression eats quiet clicks.
3. **Set the gain.** The loudest hit should peak around **−12 to −6 dB** on Audacity's meter. Never let it reach 0: clipped hits sound harsh, and the tool flags them.
4. **Keep the room quiet.** Fans low, no music, phone away. The splitter needs about 10 dB between a click and the background.

### 4.3 Audacity settings

- Recording device: **Maono G1 Neo**
- Recording channels: **1 (Mono)**
- Project Rate (bottom left): **48000 Hz**
- Edit → Preferences → Quality → Default Sample Format: **32-bit float**
- Export: File → Export → Export as WAV, encoding **32-bit float** or **24-bit PCM**

### 4.4 How to record each slot

Record **long takes with many hits**, not one file per click; the sound tool cuts them up for you (Part 5). Keep raw takes in a folder **outside** the pack, for example `Documents\SoundGen-recordings\2026-10-13\`.

**Butterfly (`butterfly`)**
- Record takes of about 30 s. Butterfly normally, but let each click finish ringing before the next one if you can. The splitter can handle up to about 22 CPS, but separated hits make cleaner variations.
- Do 3–4 takes, varying finger pressure a little between them. Each take gives roughly 30–80 usable hits.

**Drag, per-click (`drag`)**
- Real drag clicks are a continuous rattle, so single hits are hard to isolate. Record slower, gentler drags and let the tool split them (`--gap 20`).
- If they come out too smeared, rely on Loop style instead.

**Drag loop (`drag_loop`)**
- Record continuous drag-click runs of 3–6 s at different speeds: slow (about 12 CPS), medium (about 18) and fast (about 25+).
- **Don't split these.** In Audacity, cut off the start and end (the finger landing and lifting), then export each run as its own file straight into `drag_loop/`. The loop point is crossfaded automatically.

**Single right clicks (`right_click`)**
- Record normal, separate right clicks (30–60 of them), as for butterfly. Used when Right Style = Auto for slow clicks and in inventories.

**Drag end (`drag_end`)**
- Record 10–20 "finish a drag and lift off" moments. Split them with a high `--max` such as 300.

**Keys (`key_down` + `key_up`, `space_*`, `shift_*`, `sprint_*`, `w_*` …)**
- Rhythm matters, because the tool pairs each press with its release by timing:
  - press, **hold about 0.15–0.25 s**, release,
  - **pause at least 0.6 s**,
  - repeat 20–30 times per take.
- One take per key size, split into the matching folders:
  - a mix of normal keys (W A S D, numbers, Esc, letters) → `key_down` / `key_up`;
  - the space bar → `space_*`;
  - Shift and Caps Lock (mix them) → `shift_*`;
  - Tab and Backslash → `tab_*`;
  - Backspace and Enter → `backspace_*`.
- Releases are often much quieter than presses. If the tool reports skipped hits, raise the mic gain a little or use `--threshold 40`.

---

## Part 5. Turning takes into variations (`soundtool`)

`soundtool` is a small command-line program built into the same `.meowtils` file. Run it from the project folder **after building** (Part 1b):

```
soundtool.bat <command> …
```

To save typing, put the pack path in a variable:

```
set PACK=%APPDATA%\.minecraft\meowtils\soundgen\packs\default
```

### 5.1 Create a pack (if the game hasn't already)

```
soundtool.bat init "%PACK%"
```

### 5.2 Split a take

**Always do a dry run first.** It lists what would be cut without writing anything:

```
soundtool.bat split take_butterfly_1.wav "%PACK%\butterfly" --gap 25 --dry-run
```

Check the hit count matches roughly what you played. Then run it for real (same command without `--dry-run`):

```
soundtool.bat split take_butterfly_1.wav "%PACK%\butterfly" --gap 25
```

**Keys** (press and release are paired by timing):

```
soundtool.bat split take_wasd_1.wav "%PACK%\key_down" --alternate "%PACK%\key_up"
soundtool.bat split take_space_1.wav "%PACK%\space_down" --alternate "%PACK%\space_up"
soundtool.bat split take_shift_1.wav "%PACK%\shift_down" --alternate "%PACK%\shift_up"
```

**A whole folder of takes at once:**

```
soundtool.bat split "Documents\SoundGen-recordings\butterfly" "%PACK%\butterfly" --gap 25
```

**Re-records add up and never overwrite.** New files continue numbering after the highest existing one (`butterfly_041.wav`, `butterfly_042.wav`, …).

Split options:

| Option | Default | When to change it |
|---|---|---|
| `--gap <ms>` | 40 | lower (20–25) for fast butterfly or drag takes; higher if one click is being cut into two |
| `--threshold <dB>` | 30 | higher (40) to catch quiet releases; lower (20) if background noise is being cut as hits |
| `--max <ms>` | 400 | longest hit kept |
| `--alternate <folder>` | — | press/release takes |
| `--pair-window <ms>` | 400 | longest press-to-release time; your pause between presses must be longer than this |
| `--name <prefix>` | folder name | custom file names |
| `--dry-run` | — | preview only |

### 5.3 Clean up

1. Listen through new files quickly (Windows Explorer preview or Audacity). Delete double hits, coughs, and anything marked `CLIPPED`.
2. Deleting files never breaks anything. The numbering just continues from the highest remaining number.

### 5.4 Check the pack

```
soundtool.bat report "%PACK%"
```

Example output:

```
slot         files      rate peak dB (range)     length ms   lead ms  notes
butterfly       64     48000        -14..-7        18..41         3  level spread 7 dB (Match Levels evens it)
key_down        22     48000        -20..-15       30..55         3  ok
key_up          21     48000        -28..-21       25..40         3  ok
drag_loop        3     48000         -9..-8      2400..4100       0  aim for 6+
w_down           0         -               -             -         -  uses key_down
...
```

### 5.5 Audition without the game

```
soundtool.bat preview "%PACK%" butterfly butterfly_test.wav --cps 15 --seconds 8
```

This renders the slot through the real engine (same shuffle order, same volume and pitch variation) into a WAV you can play. It's the fastest way to hear whether a set sounds repetitive or uneven. Try `--cps 8` and `--cps 22`. For `drag_loop`, it plays one continuous loop.

---

## Part 6. Load and tune in game

1. **Sounds → Reload Sounds.** Chat reports how many recordings loaded and lists any files it couldn't read.
2. Settings that shape how the variations sound:

| Setting | What it does | Start at |
|---|---|---|
| Sound Pack | which `packs/<name>` folder to use; type a name, then Reload | `default` |
| Variation Order | Shuffle (all play before repeats) / Random | Shuffle |
| Match Levels | evens out loudness between re-records (max ±12 dB) | on |
| Auto Trim | removes silence before each hit so timing stays tight | on |
| Volume Variation | random ± loudness per hit | 10 % |
| Pitch Variation | random ± speed and pitch per hit | 3 % |
| Click Jitter / Key Jitter | random timing offset per hit | 3 / 6 ms |
| Left Choke / Right Choke | a new click fades out the previous click's tail, so fast clicking stays crisp | on |
| Choke Fade | how quickly that tail is faded | 3 ms |
| Right Style | Clicks / Loop / **Auto** (single clicks when slow, drag rattle once fast) | Auto |
| Drag Threshold | CPS at which Auto switches to the rattle | 8 |
| Other Keys | sounds for every other key; Where = Everywhere (incl. chat) or In Game | on, Everywhere |

**Multiple packs.** To compare two mice or two keyboards, make `packs/mouseA` and `packs/mouseB` (`soundtool init`), fill both, and switch with **Sound Pack** + **Reload Sounds**.

---

## Part 7. Getting the sound into recordings or your mic

Software can't mix into the G1 Neo's own signal; you route SoundGen to a virtual cable instead.

1. Install VB-Audio Cable and restart the PC.
2. In game: **Output → List Devices**, type part of the cable's name (for example `CABLE Input`) into **Output Device**, then **Apply Output**.
3. **For recording:** in OBS add an *Audio Input Capture* of **CABLE Output** as its own track, next to the G1 Neo track. Spread timing makes clicks 50 ms late, so set OBS → Advanced Audio Properties → **Sync Offset −50 ms** on that source, or shift the track in your editor.
4. **For live use:** in Voicemeeter, combine the G1 Neo and the cable into one virtual mic.
5. **To hear it yourself:** Windows Sound settings → Recording → CABLE Output → Properties → Listen → "Listen to this device".

Lower **Buffer** until you hear crackles, then raise it by about 5 ms.

---

## Part 8. Day-to-day loop

```
record takes → split (dry run, then real) → delete bad hits → report → preview
     ↑                                                                  │
     └──────────── Reload Sounds in game, listen, adjust ←──────────────┘
```

Git:
- **Code changes:** commit and push as usual.
- **Raw takes:** keep them out of the repo (`recordings/` is git-ignored if you keep them inside the project).
- **Finished packs:** worth backing up. A 100-file pack is a few MB. Commit a copy under `packs/` in the repo, or keep it on cloud storage. Decide before the pack grows large.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `java -version` fails or shows 1.8 | Install a JDK 17+ (Part 1b) and open a new terminal. |
| Build: `no Java compiler found` | You have a JRE, not a JDK. Install the JDK. |
| Build: `Reobf: no SRG mapping for …` | New code uses a Minecraft member that isn't in `buildtools/mappings.txt` yet; add it (instructions in that file). |
| SoundGen doesn't appear in Meowtils | Check the file is in `meowtils\extensions\` and ends in `.meowtils`, then look in `meowtils\meowtils.log` for "Failed to load extension" and send me the lines. |
| `audio failed to open` | The device name in **Output Device** is wrong, or the device is in exclusive use. Clear the field to use the default, then Apply Output. |
| `no device matching '…'` | Press **List Devices** and type a part of a name exactly as listed. |
| Crackling | Raise **Buffer** (try 30–40 ms). |
| Sounds late | Lower **Buffer**; use **Instant** timing, or offset in OBS. |
| `output clipped` in debug | Lower **Master Volume** or the per-action volumes. |
| Splitter finds too many hits | Raise `--gap`, or lower `--threshold` (for example 20). |
| Splitter misses hits | Lower `--gap`, or raise `--threshold` (for example 40). |
| Many `(skipped)` on key takes | Pause longer between presses, or raise `--pair-window` if you hold keys longer than 0.4 s. |
