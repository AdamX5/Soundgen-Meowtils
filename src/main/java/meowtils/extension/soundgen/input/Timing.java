package meowtils.extension.soundgen.input;

import java.util.concurrent.ThreadLocalRandom;

/** Shared timing settings for all channels. Written by the client thread, read anywhere. */
public final class Timing {
    public static final long TICK_NANOS = 50_000_000L;

    /**
     * Spread mode: clicks the game processed inside one tick (they all happen at the same
     * instant) are delayed by up to one tick and spaced evenly across it. Sounds natural at
     * the cost of a constant ~50 ms delay. Off = play immediately, clicks in one tick stack.
     */
    public volatile boolean spread = true;
    public volatile float clickJitterMs = 3f;
    public volatile float keyJitterMs = 6f;

    long clickJitter() {
        return jitter(clickJitterMs);
    }

    long keyJitter() {
        return jitter(keyJitterMs);
    }

    static long jitter(float ms) {
        return ms <= 0 ? 0 : (long) (ThreadLocalRandom.current().nextDouble() * ms * 1_000_000.0);
    }

    static double unit() {
        return ThreadLocalRandom.current().nextDouble();
    }
}
