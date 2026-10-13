# Minecraft API stubs (compile-only)

Empty stand-ins for the handful of Minecraft 1.8.9 classes SoundGen touches, written with
MCP (readable) names. They exist only so `javac` can compile the extension without a full
Forge/Minecraft development setup. **They are never packaged**; at runtime the real game
classes are used.

Rules:
- Every member here must match the real 1.8.9 signature (name, type, static-ness).
- Every member used from SoundGen code must also have an entry in `../mappings.txt`,
  or the build fails (on purpose) instead of producing a broken extension.
