package meowtils.extension.soundgen.engine;

/**
 * Every sound slot the engine knows about. Each slot maps to a folder of the same name
 * under the sounds directory; every .wav in that folder is one variation.
 *
 * A slot with a fallback borrows the fallback's samples when its own folder is empty
 * (following the chain: tab_down -> other_down -> key_down),
 * so per-key recordings are optional: record key_down/key_up once and every key uses them.
 * A slot with no recordings at all (and no fallback recordings) gets a synthesised
 * placeholder so the engine can be tested before anything is recorded.
 */
public enum Sound {
    DRAG("drag", null, Placeholder.MOUSE, false),
    DRAG_LOOP("drag_loop", null, Placeholder.RATTLE, true),
    DRAG_END("drag_end", DRAG, Placeholder.MOUSE, false),
    /** Single right clicks in AUTO style (slow clicking); empty folder uses drag. */
    RIGHT_CLICK("right_click", DRAG, Placeholder.MOUSE, false),
    BUTTERFLY("butterfly", null, Placeholder.MOUSE, false),
    /** A short drag-click burst (a few fast clicks with a natural end), for brief fast clicking. */
    DRAG_BURST("drag_burst", null, Placeholder.BURST, false),
    /** Mouse button press / release halves (stored for hold-style features). */
    MOUSE_DOWN("mouse_down", null, Placeholder.MOUSE, false),
    MOUSE_UP("mouse_up", null, Placeholder.KEY_UP, false),

    KEY_DOWN("key_down", null, Placeholder.KEY_DOWN, false),
    KEY_UP("key_up", null, Placeholder.KEY_UP, false),
    W_DOWN("w_down", KEY_DOWN, Placeholder.KEY_DOWN, false),
    W_UP("w_up", KEY_UP, Placeholder.KEY_UP, false),
    A_DOWN("a_down", KEY_DOWN, Placeholder.KEY_DOWN, false),
    A_UP("a_up", KEY_UP, Placeholder.KEY_UP, false),
    S_DOWN("s_down", KEY_DOWN, Placeholder.KEY_DOWN, false),
    S_UP("s_up", KEY_UP, Placeholder.KEY_UP, false),
    D_DOWN("d_down", KEY_DOWN, Placeholder.KEY_DOWN, false),
    D_UP("d_up", KEY_UP, Placeholder.KEY_UP, false),
    SPACE_DOWN("space_down", KEY_DOWN, Placeholder.SPACE_DOWN, false),
    SPACE_UP("space_up", KEY_UP, Placeholder.SPACE_UP, false),
    SHIFT_DOWN("shift_down", KEY_DOWN, Placeholder.MOD_DOWN, false),
    SHIFT_UP("shift_up", KEY_UP, Placeholder.MOD_UP, false),
    SPRINT_DOWN("sprint_down", KEY_DOWN, Placeholder.MOD_DOWN, false),
    SPRINT_UP("sprint_up", KEY_UP, Placeholder.MOD_UP, false),
    /** Every other keyboard key (hotbar, F5/perspective, Esc, chat typing, ...). */
    OTHER_DOWN("other_down", KEY_DOWN, Placeholder.KEY_DOWN, false),
    OTHER_UP("other_up", KEY_UP, Placeholder.KEY_UP, false),
    /** 1.5u keys: Tab and Backslash. */
    TAB_DOWN("tab_down", OTHER_DOWN, Placeholder.MOD_DOWN, false),
    TAB_UP("tab_up", OTHER_UP, Placeholder.MOD_UP, false),
    /** 2u keys: Backspace and Enter. (Shift-size keys use shift_*, the space bar space_*.) */
    BACKSPACE_DOWN("backspace_down", OTHER_DOWN, Placeholder.WIDE_DOWN, false),
    BACKSPACE_UP("backspace_up", OTHER_UP, Placeholder.WIDE_UP, false),
    /** Taps: one combined press+release sound, used by the "Tap" release mode. */
    KEY_TAP("key_tap", KEY_DOWN, Placeholder.KEY_DOWN, false),
    SPACE_TAP("space_tap", SPACE_DOWN, Placeholder.SPACE_DOWN, false),
    SHIFT_TAP("shift_tap", SHIFT_DOWN, Placeholder.MOD_DOWN, false),
    TAB_TAP("tab_tap", TAB_DOWN, Placeholder.MOD_DOWN, false),
    BACKSPACE_TAP("backspace_tap", BACKSPACE_DOWN, Placeholder.WIDE_DOWN, false);

    public final String folder;
    public final Sound fallback;
    final Placeholder placeholder;
    /** Loop slots are crossfaded at load time and are never trimmed. */
    public final boolean loop;

    Sound(String folder, Sound fallback, Placeholder placeholder, boolean loop) {
        this.folder = folder;
        this.fallback = fallback;
        this.placeholder = placeholder;
        this.loop = loop;
    }

    private static final Sound[] VALUES = values();

    public static Sound byOrdinal(int ordinal) {
        return VALUES[ordinal];
    }

    public static int count() {
        return VALUES.length;
    }
}
