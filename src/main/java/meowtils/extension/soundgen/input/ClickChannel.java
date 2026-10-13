package meowtils.extension.soundgen.input;

import meowtils.extension.soundgen.engine.AudioEngine;
import meowtils.extension.soundgen.engine.Sound;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * One mouse button's worth of sound. Every read method ("source") reports into the channel
 * all the time, so their counts can be compared live; only the selected source makes sound.
 *
 * Styles:
 * - CLICKS: one sample per detected click.
 * - LOOP:   a continuous recorded rattle that runs while clicks keep arriving and fades out
 *           when they stop (or immediately on release, for held-state sources).
 */
public final class ClickChannel {
    public static final int STYLE_CLICKS = 0;
    public static final int STYLE_LOOP = 1;
    private static final int LOOP_FADE_MS = 12;

    private final AudioEngine engine;
    private final Timing timing;
    private final Sound click;
    private final Sound loop;
    private final Sound end;
    private final int tag;
    /** Per source: true if its events fire inside the client tick (eligible for spreading). */
    private final boolean[] tickBound;
    /** Per source: last polled held state (held-state sources only; client thread). */
    private final boolean[] held;
    private final AtomicIntegerArray counts;

    public volatile boolean enabled = true;
    public volatile int method;
    public volatile int style = STYLE_CLICKS;
    public volatile float gain = 1f;
    public volatile int holdTimeoutMs = 120;
    public volatile boolean endClick = true;

    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicBoolean looping = new AtomicBoolean();
    private volatile long lastActivity;

    public ClickChannel(AudioEngine engine, Timing timing, Sound click, Sound loop, Sound end, int tag, boolean[] tickBound) {
        this.engine = engine;
        this.timing = timing;
        this.click = click;
        this.loop = loop;
        this.end = end;
        this.tag = tag;
        this.tickBound = tickBound.clone();
        this.held = new boolean[tickBound.length];
        this.counts = new AtomicIntegerArray(tickBound.length);
    }

    /** A discrete click seen by {@code source}. Safe from any thread. */
    public void click(int source, long now) {
        counts.incrementAndGet(source);
        if (!enabled || source != method) return;
        if (style == STYLE_LOOP) {
            lastActivity = now;
            if (looping.compareAndSet(false, true)) engine.loopStart(loop, tag, gain, now);
        } else if (timing.spread && tickBound[source]) {
            pending.incrementAndGet();
        } else {
            engine.play(click, gain, now + timing.clickJitter());
        }
    }

    /**
     * Polled held state for sources that only know "down or up" (keybind, raw mouse).
     * A press edge counts as one click; in loop style, holding keeps the loop alive and
     * releasing stops it at once. Client thread only.
     */
    public void held(int source, boolean down, long now) {
        boolean was = held[source];
        held[source] = down;
        if (down && !was) {
            click(source, now);
        } else if (enabled && source == method && style == STYLE_LOOP) {
            if (down) {
                lastActivity = now;
            } else if (was) {
                stopLoop(now);
            }
        }
    }

    /** Call once per client tick after that tick's events (tick POST). Client thread only. */
    public void tick(long now) {
        int n = pending.getAndSet(0);
        if (n > 0) {
            // Everything the game processed this tick happened at one instant; space it out
            // over the following tick in order, with a little randomness in each slot.
            for (int k = 0; k < n; k++) {
                double slot = (k + 0.5 + (Timing.unit() - 0.5) * 0.6) / n;
                engine.play(click, gain, now + (long) (slot * Timing.TICK_NANOS) + timing.clickJitter());
            }
        }
        if (looping.get() && (style != STYLE_LOOP || !enabled || now - lastActivity > holdTimeoutMs * 1_000_000L)) {
            stopLoop(now);
        }
    }

    public void stopLoop(long now) {
        if (looping.compareAndSet(true, false)) {
            engine.loopStop(tag, LOOP_FADE_MS, now);
            if (endClick) engine.play(end, gain, now);
        }
    }

    public void reset() {
        pending.set(0);
        looping.set(false);
        java.util.Arrays.fill(held, false);
    }

    /** Clicks seen by {@code source} since the last call (for the debug readout). */
    public int takeCount(int source) {
        return counts.getAndSet(source, 0);
    }

    public void clearCounts() {
        for (int i = 0; i < counts.length(); i++) counts.set(i, 0);
    }
}
