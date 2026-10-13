# SoundGen (Meowtils extension)

Turns what your character does in game into mouse and keyboard sounds for ASMR recordings. Right clicks become drag clicks, left clicks become butterfly clicks, and W/A/S/D, Space, Shift and Sprint get key press and release sounds.

Targets Meowtils 2.0.x on Lunar / Forge 1.8.9.

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

## Sounds

Folders are created at `.minecraft/meowtils/soundgen/<slot>/` the first time the module is enabled; a README inside lists them. Every `.wav` file in a folder is one variation, and the engine never plays the same variation twice in a row.

- `drag`, `butterfly`, `drag_end`: one click per file
- `drag_loop`: continuous drag-click takes, 2–5 s each (the loop point is crossfaded automatically)
- `key_down`, `key_up`: shared by every key
- `w_down`, `space_up`, `shift_down`, …: optional per-key overrides (an empty folder falls back to `key_down`/`key_up`)

Record at 48 kHz mono if you can, so files don't need resampling; 16/24-bit PCM and 32-bit float are all fine. **Auto Trim** cuts leading silence so each click starts exactly when it's scheduled. Press **Reload Sounds** after adding files.

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

## Building

1. Download the Meowtils jar from [the releases page](https://github.com/femboytatp/meowtils/releases) and save it as `libs/meowtils.jar`.
2. Run `./gradlew build`. This needs Java 8 (ForgeGradle 2.1).
3. Copy `build/libs/SoundGen-<version>.meowtils` into the Meowtils extensions folder.

The engine self-test needs no Minecraft:

```
javac -d out $(find src/main/java/meowtils/extension/soundgen/engine src/main/java/meowtils/extension/soundgen/input src/test/java -name '*.java')
java -cp out meowtils.extension.soundgen.engine.EngineSelfTest
```

## Known limitations

- **Bypassing modules:** a module that sends placement packets directly, skipping the game's click code, is only seen by the **Packet** method.
- **Jump detection:** Physics can misfire on slime blocks, ladders, or knockback that lands without registering a hurt.
- **Untested in game:** the extension compiles against the real Meowtils 2.0.1 API, but this build has not been run inside the game.
