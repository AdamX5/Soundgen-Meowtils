package meowtils.extension.soundgen.engine;

import java.util.Random;

/**
 * Synthesised stand-in sounds so the engine and the detection can be tested before any
 * recordings exist. They are deliberately simple (a filtered noise burst plus a decaying
 * tone) and are not meant to sound like real switches.
 */
enum Placeholder {
    //       tone Hz, decay ms, length ms, noise mix, level
    MOUSE(3600, 2.2, 18, 0.55, 0.55),
    KEY_DOWN(1700, 5.0, 45, 0.45, 0.45),
    KEY_UP(2300, 3.0, 30, 0.50, 0.28),
    SPACE_DOWN(650, 11.0, 90, 0.35, 0.55),
    SPACE_UP(900, 7.0, 60, 0.40, 0.32),
    MOD_DOWN(1250, 6.0, 55, 0.40, 0.45),
    MOD_UP(1600, 4.0, 40, 0.45, 0.28),
    /** A continuous drag-click rattle, roughly 25 CPS with irregular spacing. */
    RATTLE(3600, 2.2, 2000, 0.55, 0.45);

    private static final int VARIATIONS = 4;

    private final double toneHz, decayMs, lengthMs, noiseMix, level;

    Placeholder(double toneHz, double decayMs, double lengthMs, double noiseMix, double level) {
        this.toneHz = toneHz;
        this.decayMs = decayMs;
        this.lengthMs = lengthMs;
        this.noiseMix = noiseMix;
        this.level = level;
    }

    Sample[] build(int rate, String name) {
        if (this == RATTLE) {
            return new Sample[]{rattle(rate, name, new Random(0x5EED))};
        }
        Sample[] out = new Sample[VARIATIONS];
        for (int v = 0; v < VARIATIONS; v++) {
            Random random = new Random(name.hashCode() * 31L + v);
            int frames = (int) (lengthMs * rate / 1000.0);
            float[] data = new float[frames];
            click(data, 0, rate, toneHz * (0.94 + 0.12 * random.nextDouble()), level, random);
            out[v] = new Sample(data, name + "#placeholder" + v);
        }
        return out;
    }

    private Sample rattle(int rate, String name, Random random) {
        float[] data = new float[(int) (lengthMs * rate / 1000.0)];
        int pos = 0;
        while (pos < data.length) {
            double amp = level * (0.5 + 0.5 * random.nextDouble());
            click(data, pos, rate, toneHz * (0.9 + 0.2 * random.nextDouble()), amp, random);
            pos += (int) ((0.028 + 0.026 * random.nextDouble()) * rate);
        }
        return new Sample(data, name + "#placeholder");
    }

    /** Adds one click starting at {@code offset}; the tail is truncated at the buffer end. */
    private void click(float[] data, int offset, int rate, double tone, double amp, Random random) {
        double decay = decayMs / 1000.0;
        double attack = 0.0003;
        double lowpass = 0;
        int frames = (int) Math.min(data.length - offset, decay * 8 * rate);
        for (int i = 0; i < frames; i++) {
            double t = i / (double) rate;
            double env = Math.exp(-t / decay) * Math.min(1.0, t / attack);
            lowpass += 0.45 * ((random.nextDouble() * 2 - 1) - lowpass);
            double s = noiseMix * lowpass * 1.6 + (1 - noiseMix) * Math.sin(2 * Math.PI * tone * t);
            data[offset + i] += (float) (s * env * amp);
        }
    }
}
