@echo off
rem Runs the SoundGen sound tool from the built extension. Build first: gradlew build
setlocal
set "JAR="
for %%f in ("%~dp0build\libs\SoundGen-*.meowtils") do set "JAR=%%f"
if not defined JAR (
  echo Build the project first: gradlew build
  exit /b 1
)
java -cp "%JAR%" meowtils.extension.soundgen.tools.SoundTool %*
