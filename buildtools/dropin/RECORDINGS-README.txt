SoundGen recordings
===================

Put your raw takes (long recordings with many clicks/presses) here, one folder per sound:

  recordings\close\butterfly\take1.wav      <- many separate left clicks
  recordings\close\right_click\take1.wav    <- many separate right clicks
  recordings\close\drag_loop\run1.wav       <- ONE continuous drag-click run per file (3-6 s)
  recordings\close\drag_end\take1.wav       <- "stop dragging and lift off", many times
  recordings\close\key\take1.wav            <- normal keys: press, hold ~0.2 s, release, pause 0.6 s
  recordings\close\space\take1.wav          <- space bar, same rhythm
  recordings\close\shift\take1.wav          <- Shift / Caps Lock, same rhythm
  recordings\close\tab\take1.wav            <- Tab / Backslash, same rhythm
  recordings\close\backspace\take1.wav      <- Backspace / Enter, same rhythm

"close" and "far" are two packs (mic near the keyboard / mic where it sits when you talk).
Any folder name you create here becomes a pack with the same name.

Then double-click import-sounds.bat (one folder up). It cuts every NEW take into
packs\<pack>\... and prints a report. Add more takes any time and run it again.

Format: WAV, 48000 Hz, mono, 24-bit or 32-bit float (Audacity: see the guide).
