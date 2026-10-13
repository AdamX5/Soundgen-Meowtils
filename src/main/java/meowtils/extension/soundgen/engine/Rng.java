package meowtils.extension.soundgen.engine;

/** Allocation-free xorshift generator for the audio thread (not thread-safe by design). */
final class Rng {
    private long state;

    Rng(long seed) {
        state = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
    }

    long next() {
        long x = state;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        state = x;
        return x;
    }

    /** Uniform in [0, bound). */
    int nextInt(int bound) {
        return (int) ((next() >>> 1) % bound);
    }

    /** Uniform in [-1, 1). */
    double signedUnit() {
        return ((next() >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }
}
