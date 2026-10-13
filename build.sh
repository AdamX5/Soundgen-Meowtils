#!/bin/sh
# Builds SoundGen: build/libs/SoundGen-<version>.meowtils and the drag-in folder build/dropin/meowtils
# Needs a JDK 17 or newer on PATH (java -version).
cd "$(dirname "$0")" && exec java buildtools/Build.java "$@"
