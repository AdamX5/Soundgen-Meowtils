#!/bin/sh
# Runs the SoundGen sound tool from the built extension. Build first: ./gradlew build
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$(ls "$DIR"/build/libs/SoundGen-*.meowtils 2>/dev/null | head -n 1)"
if [ -z "$JAR" ]; then
  echo "Build the project first: ./gradlew build" >&2
  exit 1
fi
exec java -cp "$JAR" meowtils.extension.soundgen.tools.SoundTool "$@"
