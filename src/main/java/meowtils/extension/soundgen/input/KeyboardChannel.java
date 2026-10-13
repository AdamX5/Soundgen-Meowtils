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
 * Keys can have their own sounds by size (Tab, Shift, Backspace, Space are physically
 * bigger and sound different): see {@link #applyKeySizes}.
 *
 * A release only sounds for a press that sounded, so a key pressed before the module was
 * enabled (or while sounds weren't allowed) doesn't produce a stray release.
 */
public final class KeyboardChannel {
    public static final int KEYS = 256;

    private final AudioEngine engine;
    private final Timing timing;
    private final Sound[] downFor = new Sound[KEYS];
    private final Sound[] upFor = new Sound[KEYS];
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
        java.util.Arrays.fill(downFor, down);
        java.util.Arrays.fill(upFor, up);
    }

    /** Gives one key its own sounds. Call during setup only. */
    public void setKeySound(int key, Sound down, Sound up) {
        downFor[key] = down;
        upFor[key] = up;
    }

    // LWJGL 2 key codes (stable DirectInput scan codes)
    static final int KEY_BACK = 14, KEY_TAB = 15, KEY_RETURN = 28, KEY_LSHIFT = 42, KEY_BACKSLASH = 43,
            KEY_RSHIFT = 54, KEY_SPACE = 57, KEY_CAPITAL = 58, KEY_NUMPADENTER = 156;

    /**
     * Key sizes on the user's 75% ANSI keyboard:
     * - 1.5u Tab, Backslash             -> tab_*
     * - Shift (both), Caps Lock         -> shift_*
     * - 2u Backspace, Enter             -> backspace_*
     * - Space bar                       -> space_*
     * - everything else is 1u           -> the channel's default (other_* / key_*)
     * Fn has no key code (the keyboard handles it internally), so it can't make a sound.
     */
    public void applyKeySizes() {
        setKeySound(KEY_TAB, Sound.TAB_DOWN, Sound.TAB_UP);
        setKeySound(KEY_BACKSLASH, Sound.TAB_DOWN, Sound.TAB_UP);
        setKeySound(KEY_LSHIFT, Sound.SHIFT_DOWN, Sound.SHIFT_UP);
        setKeySound(KEY_RSHIFT, Sound.SHIFT_DOWN, Sound.SHIFT_UP);
        setKeySound(KEY_CAPITAL, Sound.SHIFT_DOWN, Sound.SHIFT_UP);
        setKeySound(KEY_BACK, Sound.BACKSPACE_DOWN, Sound.BACKSPACE_UP);
        setKeySound(KEY_RETURN, Sound.BACKSPACE_DOWN, Sound.BACKSPACE_UP);
        setKeySound(KEY_NUMPADENTER, Sound.BACKSPACE_DOWN, Sound.BACKSPACE_UP);
        setKeySound(KEY_SPACE, Sound.SPACE_DOWN, Sound.SPACE_UP);
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
                engine.play(downFor[key], gain, at);
                sounded[key] = true;
                pressedAt[key] = at;
                presses++;
            }
        } else if (sounded[key]) {
            sounded[key] = false;
            if (enabled && release) {
                engine.play(upFor[key], gain, Math.max(now + timing.keyJitter(), pressedAt[key] + minHoldMs * 1_000_000L));
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
