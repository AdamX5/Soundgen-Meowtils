package meowtils.extension.soundgen.input;

import meowtils.extension.soundgen.engine.AudioEngine;
import meowtils.extension.soundgen.engine.Sound;

/**
 * Press/release sounds for every keyboard key that has no dedicated channel: hotbar 1-0,
 * perspective, inventory, drop, Esc, chat typing, and any custom keybinds.
 *
 * Fed with the raw physical state of each key once per rendered frame (client thread only).
 * Keys the caller marks as "handled" (bound to W/A/S/D/Space/Shift/Sprint channels that are
 * switched on) are skipped so they never sound twice.
 *
 * A release only sounds for a press that sounded, so a key pressed before the module was
 * enabled (or while sounds weren't allowed) doesn't produce a stray release.
 */
public final class KeyboardChannel {
    public static final int KEYS = 256;

    private final AudioEngine engine;
    private final Timing timing;
    private final Sound down;
    private final Sound up;
    private final boolean[] isDown = new boolean[KEYS];
    private final boolean[] sounded = new boolean[KEYS];
    private final long[] pressedAt = new long[KEYS];
    private int presses;

    public volatile boolean enabled = true;
    public volatile boolean release = true;
    public volatile float gain = 1f;
    public volatile int minHoldMs = 30;

    public KeyboardChannel(AudioEngine engine, Timing timing, Sound down, Sound up) {
        this.engine = engine;
        this.timing = timing;
        this.down = down;
        this.up = up;
    }

    /**
     * @param key     LWJGL key code (1..255)
     * @param pressed current physical state
     * @param allowed whether a new press may sound right now (focus / "where" setting)
     * @param handled whether another channel already owns this key
     */
    public void update(int key, boolean pressed, boolean allowed, boolean handled, long now) {
        if (isDown[key] == pressed) return;
        isDown[key] = pressed;
        if (pressed) {
            if (enabled && allowed && !handled) {
                long at = now + timing.keyJitter();
                engine.play(down, gain, at);
                sounded[key] = true;
                pressedAt[key] = at;
                presses++;
            }
        } else if (sounded[key]) {
            sounded[key] = false;
            if (enabled && release) {
                engine.play(up, gain, Math.max(now + timing.keyJitter(), pressedAt[key] + minHoldMs * 1_000_000L));
            }
        }
    }

    public void reset() {
        java.util.Arrays.fill(isDown, false);
        java.util.Arrays.fill(sounded, false);
    }

    /** Presses that sounded since the last call (debug readout). */
    public int takePresses() {
        int n = presses;
        presses = 0;
        return n;
    }
}
