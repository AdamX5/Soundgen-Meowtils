SoundGen recordings
===================

Drop your WAV takes straight into this folder and double-click import-sounds.bat (one folder
up). Every NEW take is cut up and sorted into packs\default\..., then a report is printed.
Add more takes any time and run it again; files already imported are listed in imported.txt.

File names tell the importer what's inside:

  quick-<sound>.wav           quick taps/clicks: press and release in one go (any intensity)
  press_unpress-<sound>.wav   press, release, press, release... (starts with a press, even count)
  drag_click-long.wav         drag-click runs, separated by silence   -> drag_loop
  drag_click-medium.wav       (same)                                  -> drag_loop
  drag_click-short.wav        short drag bursts, separated by silence -> drag_burst

  <sound> = small_keys (normal keys), space_key, shift_key, tab_key, backspace_key, click (mouse)

Examples: quick-small_keys.wav, press_unpress-tab_key.wav, quick-click.wav
Add a number for more takes of the same thing: quick-click-2.wav

Quiet recordings are fine: the first import picks one gain for the whole pack (loudest hit ->
-1 dB) and saves it in gain.txt, so later takes get the same boost and every level stays
relative (space louder than small keys, soft hits softer). Delete gain.txt to recalculate.

To undo an import: delete the new files in packs\default\<folder> and remove the take's line
from imported.txt.
