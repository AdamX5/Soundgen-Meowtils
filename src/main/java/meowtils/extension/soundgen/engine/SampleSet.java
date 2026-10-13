package meowtils.extension.soundgen.engine;

/**
 * The variations for one {@link Sound}. Picking avoids repeating the previous variation
 * when more than one exists, because identical back-to-back clicks are the most obvious
 * "machine gun" artefact in click ASMR.
 *
 * Only the audio thread calls {@link #pick}, so {@code last} needs no synchronisation.
 */
public final class SampleSet {
    final Sample[] samples;
    /** True when these are synthesised placeholders rather than recordings. */
    public final boolean placeholder;
    private int last = -1;

    SampleSet(Sample[] samples, boolean placeholder) {
        this.samples = samples;
        this.placeholder = placeholder;
    }

    public int size() {
        return samples.length;
    }

    Sample pick(long random) {
        int n = samples.length;
        if (n == 1) return samples[0];
        int i;
        if (last < 0) {
            i = (int) ((random >>> 1) % n);
        } else {
            i = (int) ((random >>> 1) % (n - 1));
            if (i >= last) i++; // skip the previous variation
        }
        last = i;
        return samples[i];
    }
}
