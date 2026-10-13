@echo off
rem Builds SoundGen: build\libs\SoundGen-<version>.meowtils and the drag-in folder build\dropin\meowtils
rem Needs a JDK 17 or newer on PATH (java -version).
cd /d "%~dp0"
java buildtools\Build.java %*
