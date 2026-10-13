# SoundGen (Meowtils extension)

Turns what your character does in game into mouse and keyboard sounds for ASMR recordings. Right clicks become drag clicks, left clicks become butterfly clicks, and W/A/S/D, Space, Shift and Sprint get key press and release sounds.

Targets Meowtils 2.0.x on Lunar / Forge 1.8.9.

**New here? Follow [docs/GUIDE.md](docs/GUIDE.md)**: step by step from setup to recording, splitting and tuning variations.

## Status

The engine and the action detection are built. No sounds have been recorded yet: every sound slot uses a synthesised placeholder until you add `.wav` files.

## How it reads actions

Each action can be read several ways. **Every method is watched at the same time**. The one you select makes the sound, and the others are only counted. Turn on **Debug Readout** to see, once a second, what each method saw (the selected one is underlined):

```
[SG] R/s game 20 place 20 pkt 20 key 1 raw 1 | L/s game 9 atk 3 pkt 9 key 9 raw 9
```

| Action | Methods |
|---|---|
| Right click | **Game Click** (each time the game runs a right click, including fast-place repeats) · **Placement** (each block placement attempt) · **Packet** (each placement packet sent) · **Keybind** (button press edges) · **Raw Mouse** (physical button, checked every frame) |
| Left click | **Game Click** · **Attack** (hits on entities only) · **Packet** (arm-swing packets) · **Keybind** · **Raw Mouse** |
| W A S D | **Keybind** · **Movement** (the movement the game actually used, including module-driven input) · **Raw Key** |
| Space | Keybind · Movement · Raw Key · **Physics** (detects a jump take-off and ignores knockback; press only) |
| Shift | Keybind · Movement · **Player** (visibly crouching) · Raw Key · **Packet** (sneak packets) |
| Sprint | Keybind · Player · Raw Key · Packet |

"Raw" methods read the physical key or button, so they ignore modules. Keybind, Movement, Player and Packet methods include actions that modules perform.

### Key release modes

- **Real**: plays the release sound when the method sees the key go up. Methods that can't see the release (Physics) fall back to Synthetic.
- **Synthetic**: plays the release a set time after the press (the **Synthetic Release** slider, with ±20 % variation).
- **Off**: press sounds only.

**Min Hold** stops a 1-tick tap from playing its press and release on top of each other.

### Click timing

The game processes clicks in 50 ms ticks. Every click handled in the same tick happens at the same instant, so exact sub-tick timing is not available from game actions. That is a Minecraft limit, not an engine limit.

- **Spread** (default): clicks from the same tick are spaced evenly across the next 50 ms. They sound natural but play about one tick (50 ms) late. For recordings, shift the audio track 50 ms earlier in your editor.
- **Instant**: no delay, but clicks from the same tick play together.

Raw methods are checked every frame and always play immediately.

### Drag style

- **Clicks**: one sample per detected click.
- **Loop**: a recorded drag-click rattle plays while clicks keep arriving. It fades out after **Loop Hold Timeout** with no clicks, or immediately on release when the method is Keybind or Raw Mouse. Optional **Loop End Click** plays one last click at the end.

## Sounds and variations

Sounds live in packs at `<game dir>/meowtils/soundgen/packs/<pack>/<slot>/`. Pick the pack with the **Sound Pack** setting. Every `.wav` file in a slot folder is one variation.

- **Variation Order = Shuffle** (default): every variation plays once before any repeats, and never the same one twice in a row.
- **Match Levels**: evens out loudness between re-records made at slightly different distances.
- **Auto Trim**: cuts leading silence so each hit starts exactly on time.

| Slot | Contents |
|---|---|
| `butterfly`, `drag`, `drag_end` | one hit per file |
| `drag_loop` | continuous drag-click takes, 2–5 s each (the loop point is crossfaded automatically) |
| `key_down`, `key_up` | shared by every key |
| `w_down`, `space_up`, `shift_down`, … | optional per-key overrides (an empty folder falls back to `key_down`/`key_up`) |

### Sound tool

Run with `soundtool.bat` / `soundtool.sh` after building (plain Java, no game needed):

```
soundtool split take.wav <pack>/butterfly --gap 25           # one long take -> many numbered variations
soundtool split keys.wav <pack>/key_down --alternate <pack>/key_up   # press/release pairs, matched by timing
soundtool report <pack>                                      # counts, levels, problems per slot
soundtool preview <pack> butterfly out.wav --cps 15          # hear a slot through the real engine
```

Re-recordings are numbered after the existing files and never overwrite them.

## Getting it into your mic (Maono G1 Neo)

Software can't mix into a physical mic's signal. Send SoundGen to a virtual cable instead:

1. Install VB-Audio Cable (or Voicemeeter).
2. In SoundGen → Output, press **List Devices**, type part of the cable's name into **Output Device** (for example `CABLE Input`), then press **Apply Output**.
3. Choose one setup:
   - **Recording:** in OBS, add "CABLE Output" as its own audio track next to the G1 Neo. You keep separate tracks you can rebalance later.
   - **Live:** in Voicemeeter, combine the G1 Neo and the cable into one virtual mic, and pick that as your input in Discord or your streaming app.

To hear the sounds yourself while they go to the cable, turn on Windows "Listen to this device" for CABLE Output.

## Performance

- **Game thread:** each detected action costs a few field writes into a fixed-size queue. Nothing is allocated and nothing waits on the audio thread.
- **Audio thread:** one thread mixes up to 48 voices from samples already decoded into memory. It spends nearly all its time waiting on the audio device. The offline benchmark mixed 60 s of audio at about 3,000 clicks/s in roughly 0.4 s (about 0.6 % of one core). Real use is far lighter.
- **Latency:** set by the **Buffer** slider (default 20 ms). Lower it until you hear crackles, then raise it a little. The real figure depends on your Windows audio driver.
- **Disabled:** the audio thread is stopped and the device is closed.

## Install

Download `SoundGen-<version>-dropin.zip` from the GitHub **Releases** page, unzip, and drag the `meowtils` folder onto your game directory (the folder that already contains `meowtils`). See `docs/GUIDE.md`, Part 1.

## Building

Needs only a JDK 17+:

```
build.bat        (Windows)
./build.sh       (macOS/Linux)
```

This downloads the Meowtils reference jar into `libs/` (compile-only, never shipped), compiles for Java 8, runs the self-test, and translates Minecraft names to Forge runtime names using `buildtools/mappings.txt`. Output: `build/libs/SoundGen-<version>.meowtils`, plus the drag-in folder `build/dropin/meowtils` and the zip `build/SoundGen-<version>-dropin.zip`.

Every push to GitHub runs the same build (`.github/workflows/build.yml`). When `VERSION` contains a version that has no release yet, the workflow publishes one.

The engine self-test needs no Minecraft:

```
javac -d out $(find src/main/java/meowtils/extension/soundgen/engine src/main/java/meowtils/extension/soundgen/input src/main/java/meowtils/extension/soundgen/tools src/test/java -name '*.java')
java -cp out meowtils.extension.soundgen.engine.EngineSelfTest
```

## Known limitations

- **Bypassing modules:** a module that sends placement packets directly, skipping the game's click code, is only seen by the **Packet** method.
- **Jump detection:** Physics can misfire on slime blocks, ladders, or knockback that lands without registering a hurt.
- **Untested in game:** the extension compiles against the real Meowtils 2.0.1 API, but this build has not been run inside the game.
