package meowtils.extension.soundgen.engine;

/**
 * The variations for one {@link Sound}.
 *
 * Two ways to choose the next variation:
 * - SHUFFLE (default): a shuffled "bag" of every variation is played through before any
 *   repeats, so a large set of re-recordings all get heard and none clusters. The first
 *   pick of a new bag is never the last pick of the previous one.
 * - RANDOM: independent random picks, only avoiding an immediate back-to-back repeat.
 *
 * Only the audio thread calls {@link #pick}, so the bag needs no synchronisation.
 */
public final class SampleSet {
    public static final int SHUFFLE = 0;
    public static final int RANDOM = 1;

    final Sample[] samples;
    /** True when these are synthesised placeholders rather than recordings. */
    public final boolean placeholder;
    private final int[] bag;
    private int bagPos;
    private int last = -1;

    SampleSet(Sample[] samples, boolean placeholder) {
        this.samples = samples;
        this.placeholder = placeholder;
        this.bag = new int[samples.length];
        this.bagPos = samples.length; // empty bag: refill on first pick
    }

    public int size() {
        return samples.length;
    }

    Sample pick(Rng rng, int mode) {
        int n = samples.length;
        if (n == 1) return samples[0];
        int i = mode == RANDOM ? pickRandom(rng, n) : pickShuffled(rng, n);
        last = i;
        return samples[i];
    }

    private int pickRandom(Rng rng, int n) {
        if (last < 0) return rng.nextInt(n);
        int i = rng.nextInt(n - 1);
        return i >= last ? i + 1 : i; // skip the previous variation
    }

    private int pickShuffled(Rng rng, int n) {
        if (bagPos >= n) {
            for (int k = 0; k < n; k++) bag[k] = k;
            for (int k = n - 1; k > 0; k--) { // Fisher-Yates
                int j = rng.nextInt(k + 1);
                int t = bag[k];
                bag[k] = bag[j];
                bag[j] = t;
            }
            if (bag[0] == last) { // no repeat across the bag boundary
                int j = 1 + rng.nextInt(n - 1);
                bag[0] = bag[j];
                bag[j] = last;
            }
            bagPos = 0;
        }
        return bag[bagPos++];
    }
}
