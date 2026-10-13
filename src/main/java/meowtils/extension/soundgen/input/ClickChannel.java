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
 * - AUTO:   single-click sounds while clicking slowly; switches to the LOOP rattle once the
 *           click rate passes the drag threshold (fast place, drag clicking), and back again
 *           when it stops. With bursts on, a short spell of fast clicking plays one
 *           drag_burst recording (which ends naturally); only if the fast clicking outlasts
 *           the burst window does the continuous rattle take over.
 *
 * Choke: each new click fades out the previous click's tail, so very fast clicking stays
 * crisp instead of piling up into a smear.
 */
public final class ClickChannel {
    public static final int STYLE_CLICKS = 0;
    public static final int STYLE_LOOP = 1;
    public static final int STYLE_AUTO = 2;
    /** AUTO measures the click rate over this window. */
    static final long RATE_WINDOW_NANOS = 300_000_000L;
    private static final int HISTORY = 32;

    private final AudioEngine engine;
    private final Timing timing;
    private final Sound click;
    private final Sound single;
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
    public volatile boolean choke = true;
    /** How long a rattle takes to fade out after the clicking stops (no hard cut). */
    public volatile int loopFadeMs = 20;
    /** AUTO: play a drag_burst first (natural end) before committing to the loop. */
    public volatile boolean bursts = true;
    public volatile int burstWindowMs = 350;
    /** AUTO switches to the rattle at or above this many clicks per second. */
    public volatile int dragThresholdCps = 8;

    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicInteger pendingSingle = new AtomicInteger();
    private final AtomicBoolean looping = new AtomicBoolean();
    private final AtomicBoolean bursting = new AtomicBoolean();
    private volatile long burstStart;
    private volatile Sound burst;
    private volatile long lastActivity;
    private final long[] history = new long[HISTORY]; // guarded by itself
    private int historyPos;

    /**
     * @param click  sample for CLICKS style
     * @param single sample for AUTO style's slow clicks
     * @param loop   rattle for LOOP / AUTO
     * @param end    optional click when a rattle stops
     * @param tag    unique per channel: loop identity and choke group
     */
    public ClickChannel(AudioEngine engine, Timing timing, Sound click, Sound single, Sound loop, Sound end, int tag,
                        boolean[] tickBound) {
        this.engine = engine;
        this.timing = timing;
        this.click = click;
        this.single = single;
        this.loop = loop;
        this.end = end;
        this.tag = tag;
        this.tickBound = tickBound.clone();
        this.held = new boolean[tickBound.length];
        this.counts = new AtomicIntegerArray(tickBound.length);
        java.util.Arrays.fill(history, Long.MIN_VALUE);
    }

    /** A discrete click seen by {@code source}. Safe from any thread. */
    public void click(int source, long now) {
        counts.incrementAndGet(source);
        if (!enabled || source != method) return;
        int s = style;
        if (s == STYLE_LOOP) {
            lastActivity = now;
            startLoop(now);
        } else if (s == STYLE_AUTO) {
            lastActivity = now;
            if (rateAtLeast(now, dragThresholdCps)) {
                Sound b = burst;
                if (looping.get()) {
                    // already rattling
                } else if (b != null && bursts) {
                    if (bursting.compareAndSet(false, true)) {
                        burstStart = now;
                        engine.play(b, gain, now, chokeGroup());
                    } else if (now - burstStart >= burstWindowMs * 1_000_000L) {
                        startLoop(now);
                    }
                } else {
                    startLoop(now);
                }
            } else if (!looping.get()) {
                hit(single, pendingSingle, source, now);
            }
        } else {
            hit(click, pending, source, now);
        }
    }

    private void hit(Sound sound, AtomicInteger spreadCounter, int source, long now) {
        if (timing.spread && tickBound[source]) {
            spreadCounter.incrementAndGet();
        } else {
            engine.play(sound, gain, now + timing.clickJitter(), chokeGroup());
        }
    }

    /** Records this click and reports whether the recent rate reaches {@code cps}. */
    private boolean rateAtLeast(long now, int cps) {
        synchronized (history) {
            history[historyPos] = now;
            historyPos = (historyPos + 1) % HISTORY;
            int n = 0;
            for (long t : history) if (t != Long.MIN_VALUE && now - t < RATE_WINDOW_NANOS) n++;
            return n * 1_000_000_000L >= (long) cps * RATE_WINDOW_NANOS;
        }
    }

    private void startLoop(long now) {
        if (looping.compareAndSet(false, true)) engine.loopStart(loop, tag, gain, now);
    }

    /** Sound for AUTO's short bursts (null = no bursts). Call during setup. */
    public void setBurst(Sound sound) {
        burst = sound;
    }

    private int chokeGroup() {
        return choke ? tag : 0;
    }

    /**
     * Polled held state for sources that only know "down or up" (keybind, raw mouse).
     * A press edge counts as one click; while a rattle runs, holding keeps it alive and
     * releasing stops it at once. Client thread only.
     */
    public void held(int source, boolean down, long now) {
        boolean was = held[source];
        held[source] = down;
        if (down && !was) {
            click(source, now);
        } else if (enabled && source == method && style != STYLE_CLICKS && looping.get()) {
            if (down) {
                lastActivity = now;
            } else if (was) {
                stopLoop(now);
            }
        }
    }

    /** Call once per client tick after that tick's events (tick POST). Client thread only. */
    public void tick(long now) {
        flush(click, pending, now);
        flush(single, pendingSingle, now);
        boolean idle = now - lastActivity > holdTimeoutMs * 1_000_000L;
        if (looping.get() && (style == STYLE_CLICKS || !enabled || idle)) {
            stopLoop(now);
        }
        if (bursting.get() && idle && !looping.get()) bursting.set(false);
    }

    /**
     * Everything the game processed this tick happened at one instant; space it out over the
     * following tick in order, with a little randomness in each slot.
     */
    private void flush(Sound sound, AtomicInteger counter, long now) {
        int n = counter.getAndSet(0);
        for (int k = 0; k < n; k++) {
            double slot = (k + 0.5 + (Timing.unit() - 0.5) * 0.6) / n;
            engine.play(sound, gain, now + (long) (slot * Timing.TICK_NANOS) + timing.clickJitter(), chokeGroup());
        }
    }

    public void stopLoop(long now) {
        bursting.set(false);
        if (looping.compareAndSet(true, false)) {
            engine.loopStop(tag, loopFadeMs, now);
            if (endClick) engine.play(end, gain, now, chokeGroup());
        }
    }

    public boolean isLooping() {
        return looping.get();
    }

    public void reset() {
        pending.set(0);
        pendingSingle.set(0);
        looping.set(false);
        bursting.set(false);
        java.util.Arrays.fill(held, false);
        synchronized (history) {
            java.util.Arrays.fill(history, Long.MIN_VALUE);
        }
    }

    /** Clicks seen by {@code source} since the last call (for the debug readout). */
    public int takeCount(int source) {
        return counts.getAndSet(source, 0);
    }

    public void clearCounts() {
        for (int i = 0; i < counts.length(); i++) counts.set(i, 0);
    }
}
