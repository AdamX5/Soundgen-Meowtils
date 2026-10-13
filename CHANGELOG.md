# Changelog

Each `## <version>` section becomes the GitHub release notes when that version is published
(see `.github/workflows/build.yml`). Newest first.

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
