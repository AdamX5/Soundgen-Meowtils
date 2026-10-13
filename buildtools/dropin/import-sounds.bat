@echo off
rem SoundGen: double-click to turn your recordings into sound packs.
rem   recordings\quick-small_keys.wav, press_unpress-space_key.wav, drag_click-long.wav ...
rem       -> packs\default\<sound folders>
rem   recordings\<pack>\<sound>\*.wav   ->   packs\<pack>\<sound folders>   (optional, per-folder layout)
rem New takes only: already-imported takes are listed in imported.txt
setlocal
cd /d "%~dp0"

set "JAR="
for %%f in ("..\extensions\SoundGen-*.meowtils") do set "JAR=%%f"
if not defined JAR (
  echo SoundGen is not installed in ..\extensions
  pause
  exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install Eclipse Temurin 21 from adoptium.net
  echo and tick "Add to PATH" during setup, then run this again.
  pause
  exit /b 1
)

echo.
echo ================ recordings (named files) -^> pack: default ================
java -cp "%JAR%" meowtils.extension.soundgen.tools.SoundTool import-files "recordings" "packs\default"
echo.
java -cp "%JAR%" meowtils.extension.soundgen.tools.SoundTool report "packs\default"

rem Optional: sub-folders of recordings\ are extra packs (recordings\<pack>\<sound>\*.wav)
for /d %%p in ("recordings\*") do (
  echo.
  echo ================ pack: %%~nxp ================
  java -cp "%JAR%" meowtils.extension.soundgen.tools.SoundTool import "%%p" "packs\%%~nxp"
  echo.
  java -cp "%JAR%" meowtils.extension.soundgen.tools.SoundTool report "packs\%%~nxp"
)

echo.
echo Done. In game: SoundGen ^> Sounds ^> type the pack name in Sound Pack ^> Reload Sounds.
pause
