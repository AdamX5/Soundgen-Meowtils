package meowtils.extension.soundgen.input;

import meowtils.extension.soundgen.engine.AudioEngine;
import meowtils.extension.soundgen.engine.Sound;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * One key's press/release sounds. Like {@link ClickChannel}, every source reports all the
 * time (for live comparison) and only the selected one makes sound.
 *
 * Release modes:
 * - REAL:      release sound when the source sees the key go up. Sources that only see the
 *              press (e.g. jump physics) fall back to SYNTHETIC automatically.
 * - SYNTHETIC: release sound a fixed time after the press, ignoring the real release.
 * - OFF:       press sounds only.
 * - TAP:       one combined press+release recording (the *_tap slot) at the press, no
 *              separate release.
 *
 * Methods are synchronized because packet events may arrive off the client thread; the
 * monitor is uncontended in practice.
 */
public final class KeyChannel {
    public static final int RELEASE_REAL = 0;
    public static final int RELEASE_SYNTHETIC = 1;
    public static final int RELEASE_OFF = 2;
    public static final int RELEASE_TAP = 3;

    private final AudioEngine engine;
    private final Timing timing;
    private final Sound down;
    private final Sound up;
    private final Sound tap;
    private final boolean[] state;
    private final AtomicIntegerArray counts;

    public volatile boolean enabled = true;
    public volatile int method;
    public volatile int releaseMode = RELEASE_REAL;
    public volatile float gain = 1f;
    public volatile int syntheticReleaseMs = 80;
    /** A release never sounds sooner than this after its press, so 1-tick taps don't smear together. */
    public volatile int minHoldMs = 30;

    private int activeMethod = -1;
    private boolean releasePending;
    private long lastPressAt;

    public KeyChannel(AudioEngine engine, Timing timing, Sound down, Sound up, Sound tap, int sources) {
        this.engine = engine;
        this.timing = timing;
        this.down = down;
        this.up = up;
        this.tap = tap;
        this.state = new boolean[sources];
        this.counts = new AtomicIntegerArray(sources);
    }

    /** Held state from a source that sees both edges (polled or packet). */
    public synchronized void state(int source, boolean isDown, long now) {
        if (state[source] == isDown) return;
        state[source] = isDown;
        if (isDown) counts.incrementAndGet(source);
        if (!selected(source)) return;
        if (isDown) {
            press(now, false);
        } else {
            release(now);
        }
    }

    /** A press from a source that cannot see the release. */
    public synchronized void pulse(int source, long now) {
        counts.incrementAndGet(source);
        if (selected(source)) press(now, true);
    }

    private boolean selected(int source) {
        int m = method;
        if (m != activeMethod) {
            // Switching methods mid-hold must not leave a release hanging from the old one.
            activeMethod = m;
            releasePending = false;
        }
        return enabled && source == m;
    }

    private void press(long now, boolean cannotSeeRelease) {
        long at = now + timing.keyJitter();
        lastPressAt = at;
        int mode = releaseMode;
        if (mode == RELEASE_TAP) {
            engine.play(tap, gain, at);
            releasePending = false;
            return;
        }
        engine.play(down, gain, at);
        if (mode == RELEASE_SYNTHETIC || (mode == RELEASE_REAL && cannotSeeRelease)) {
            long hold = (long) (syntheticReleaseMs * (0.8 + 0.4 * Timing.unit()) * 1_000_000.0);
            engine.play(up, gain, at + Math.max(hold, minHoldMs * 1_000_000L));
            releasePending = false;
        } else {
            releasePending = mode == RELEASE_REAL;
        }
    }

    private void release(long now) {
        if (!releasePending) return;
        releasePending = false;
        long at = Math.max(now + timing.keyJitter(), lastPressAt + minHoldMs * 1_000_000L);
        engine.play(up, gain, at);
    }

    public synchronized void reset() {
        java.util.Arrays.fill(state, false);
        releasePending = false;
    }

    public int takeCount(int source) {
        return counts.getAndSet(source, 0);
    }

    public void clearCounts() {
        for (int i = 0; i < counts.length(); i++) counts.set(i, 0);
    }
}
