@echo off
rem SoundGen: double-click to turn your recordings into sound packs.
rem   recordings\<pack>\<sound>\*.wav   ->   packs\<pack>\<sound folders>
rem New takes only: already-imported takes are listed in recordings\<pack>\imported.txt
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
