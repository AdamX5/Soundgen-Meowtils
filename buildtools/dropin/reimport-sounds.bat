@echo off
rem SoundGen: re-import ALL named takes in recordings\ (after a SoundGen update changed how
rem sounds are cut). Clears only the pack\default folders those takes fill, then cuts again.
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

java -cp "%JAR%" meowtils.extension.soundgen.tools.SoundTool import-files "recordings" "packs\default" --replace
echo.
java -cp "%JAR%" meowtils.extension.soundgen.tools.SoundTool report "packs\default"
echo.
echo Done. In game: SoundGen ^> Sounds ^> Reload Sounds.
pause
