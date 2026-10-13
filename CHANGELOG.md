# Changelog

Each `## <version>` section becomes the GitHub release notes when that version is published
(see `.github/workflows/build.yml`). Newest first.

## 0.5.0

Built around the user's first real recordings:

- **Named-file import**: drop takes like `quick-small_keys.wav`, `press_unpress-space_key.wav`,
  `drag_click-long.wav` straight into `soundgen\recordings\` and double-click
  `import-sounds.bat`; they're cut and sorted into `packs\default`.
  - press/release files: hits alternate press, release (even count); odd counts fall back to
    timing-based pairing.
  - quick (tap) files: each tap keeps its own release inside it.
  - drag_click-long/medium: each run (separated by silence) becomes a `drag_loop` take;
    drag_click-short: each burst becomes a `drag_burst`.
- **Quiet recordings**: one shared gain per pack (loudest hit -> -1 dB, saved in gain.txt),
  so relative levels between keys and intensities are kept.
- **Tap release mode** (W/A/S/D/Space/Shift/Sprint and Other Keys): one combined `*_tap`
  recording per press. New slots `key_tap`, `space_tap`, `shift_tap`, `tab_tap`, `backspace_tap`.
- **Drag bursts** (Right Click, on by default): in Auto style, a short spell of fast clicking
  plays a `drag_burst` with a natural ending; the loop only takes over past Burst Window.
- New slots `mouse_down` / `mouse_up` (stored for a later hold feature).
- Match Levels now defaults to off (keeps recorded intensity differences). Existing configs
  keep their saved value: switch it off in Sounds.
- The install package no longer creates close/far folders.

## 0.4.0

Recording workflow (no command line needed):

- `soundgen\import-sounds.bat` (in the install package): double-click to cut every new take in
  `soundgen\recordings\<pack>\<sound>\` into `soundgen\packs\<pack>\`, then print a report.
  Press/release takes (`key`, `space`, `shift`, `tab`, `backspace`, ...) are split into
  `<name>_down` / `<name>_up`; `drag_loop` runs are kept whole with 0.2 s trimmed off each end.
  Already-imported takes are remembered, so it can be run again after every session.
- Ready-made `recordings\close` and `recordings\far` folders and matching packs, for the two
  mic distances (switch with Sound Pack).
- `soundtool import <recordings> <pack>` for the same thing from the command line.
- Guide: Audacity settings (recommended), OBS settings, two-distance recording.

## 0.3.0

From the second in-game test (0.2.0):

- **Inventory clicks**: clicking in any menu or inventory plays clicks (shift-click included).
  Left = single clicks. Right = single click (e.g. taking half a stack), switching to the drag
  rattle when clicking fast or dragging across slots. "Inventory Read": Raw Mouse (every
  physical click in any menu, default) or Slot Click (only clicks that act on a slot).
- **Key sizes**: bigger keys have their own sounds. New folders `tab_*` (Tab, Backslash) and
  `backspace_*` (Backspace, Enter); both Shifts and Caps Lock use `shift_*`; the space bar
  uses `space_*`, also when typing in chat. Everything else is a normal 1u key.
  Empty folders now fall back step by step (`tab_down` -> `other_down` -> `key_down`).
- **Drag Release Fade** (Right Click section, default 20 ms): letting go fades the rattle out
  instead of cutting it, followed by the optional `drag_end` lift-off click.
- Placeholder sounds are darker and softer.

## 0.2.0

From the first in-game test (Lunar, 0.1.1):

- **Choke** (Left Choke / Right Choke, on by default): each new click fades out the previous
  click's tail over **Choke Fade** (Timing, default 3 ms). Very fast butterfly clicking or an
  auto clicker stays crisp instead of piling up.
- **Right Style "Auto"** (new default): single `right_click` sounds while clicking slowly,
  switching to the drag rattle once the rate reaches **Drag Threshold** (default 8 CPS), e.g.
  holding right click with fast place. New sound folder `right_click` (falls back to `drag`).
  Existing configs keep their old style: switch Right Style to Auto to try it.
- **Other Keys**: every other keyboard key now clicks too (hotbar 1-0, perspective, Esc, E, Q,
  chat typing...). Keys owned by the W/A/S/D/Space/Shift/Sprint channels are skipped in game.
  "Where": Everywhere (includes menus and chat) or In Game. New folders `other_down` /
  `other_up` (fall back to `key_down` / `key_up`).
- Buttons now use Meowtils' standard size (the text was tiny). "Apply Output" renamed
  "Apply Device".
- `build.bat --install`: builds and copies the extension straight into the live
  `.minecraft\meowtils\extensions`, replacing only older SoundGen files.

## 0.1.1

- Install package reorganised into two folders named after their destination:
  "Drop into extensions folder" (the `.meowtils` file) and "Drop into meowtils folder"
  (the `soundgen` sound folders). No more nesting to get right.
- Install notes point to `%APPDATA%\.minecraft\meowtils` and warn against the
  `lunarclient\Meowtils-Lunar` folder.
- No code changes to the extension itself.

## 0.1.0

First build. Not yet tested in game.

- Audio engine: separate mixer thread, 48 voices, frame-accurate scheduling, lock-free on the
  audio side, selectable output device (for virtual cables) and buffer size.
- Detection with switchable read methods for every action (game click, placement, packet,
  keybind, raw input, movement, player state, jump physics) and a live Debug Readout that
  compares them.
- Drag-click styles: per-click or continuous loop. Key release modes: real, synthetic or off.
  Spread or instant timing.
- Sound packs with shuffle-order variations, level matching, auto-trim, and synthesised
  placeholders until recordings exist.
- `soundtool`: split long takes into numbered variations (press/release pairing by timing),
  pack report, and offline preview.
- One-command build (`build.bat` / `build.sh`) that produces the `.meowtils` file and a
  drag-in folder/zip.
