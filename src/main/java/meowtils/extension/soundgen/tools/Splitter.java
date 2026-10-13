package meowtils.extension.soundgen.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds individual clicks/keypresses in one long recording, so many variations can be
 * recorded in a single take instead of one file per click.
 *
 * Works on a 1 ms peak envelope. A slice starts when the envelope rises above the "on"
 * threshold after having fallen below the "off" threshold (re-arm), and at least the
 * minimum gap after the previous start. Thresholds adapt to the take: "on" is the louder of
 * (take peak - threshold dB) and (noise floor + 10 dB), so quiet rooms and loud rooms both work.
 */
public final class Splitter {

    public static final class Options {
        /** How far below the loudest hit a hit may be and still count, in dB. */
        public double thresholdDb = 30;
        /** Minimum time between two slice starts, in ms. Lower for fast butterfly takes. */
        public double minGapMs = 40;
        /** Longest slice kept, in ms. */
        public double maxLengthMs = 400;
        /** Audio kept before each detected onset, in ms. */
        public double preRollMs = 3;
        /** Audio kept after the sound drops below the off threshold, in ms. */
        public double tailMs = 10;
    }

    /** A detected slice: [start, end) in frames, plus its onset frame. */
    public static final class Slice {
        public final int start, onset, end;
        public final float peak;

        Slice(int start, int onset, int end, float peak) {
            this.start = start;
            this.onset = onset;
            this.end = end;
            this.peak = peak;
        }
    }

    private Splitter() {
    }

    public static List<Slice> find(float[] x, int rate, Options o) {
        List<Slice> out = new ArrayList<Slice>();
        int win = Math.max(1, rate / 1000);
        int blocks = (x.length + win - 1) / win;
        if (blocks < 4) return out;

        float[] env = new float[blocks];
        float peak = 0;
        for (int b = 0; b < blocks; b++) {
            float m = 0;
            for (int i = b * win, end = Math.min(x.length, i + win); i < end; i++) m = Math.max(m, Math.abs(x[i]));
            env[b] = m;
            peak = Math.max(peak, m);
        }
        if (peak <= 0) return out;
        float[] sorted = env.clone();
        Arrays.sort(sorted);
        float noise = sorted[(int) (blocks * 0.2)];
        float on = (float) Math.max(peak * Math.pow(10, -o.thresholdDb / 20), noise * 3.16);
        float off = Math.max(on * 0.25f, noise * 2f);

        List<Integer> onsets = new ArrayList<Integer>();
        int gapBlocks = (int) Math.max(1, o.minGapMs);
        int lastOnsetBlock = Integer.MIN_VALUE / 2;
        boolean armed = true;
        for (int b = 0; b < blocks; b++) {
            if (env[b] < off) armed = true;
            if (armed && env[b] >= on && b - lastOnsetBlock >= gapBlocks) {
                // refine to the first sample in this or the previous block that rises past half "on"
                int i = Math.max(0, (b - 1) * win);
                int end = Math.min(x.length, (b + 1) * win);
                while (i < end && Math.abs(x[i]) < on * 0.5f) i++;
                onsets.add(i);
                lastOnsetBlock = b;
                armed = false;
            }
        }

        int preRoll = frames(o.preRollMs, rate);
        int tail = frames(o.tailMs, rate);
        int maxLen = frames(o.maxLengthMs, rate);
        int prevEnd = 0;
        for (int k = 0; k < onsets.size(); k++) {
            int onset = onsets.get(k);
            int start = Math.max(prevEnd, onset - preRoll);
            int hardEnd = Math.min(x.length, onset + maxLen);
            if (k + 1 < onsets.size()) hardEnd = Math.min(hardEnd, onsets.get(k + 1) - preRoll);
            if (hardEnd <= onset) continue;

            int lastLoud = onset / win;
            for (int b = onset / win; b * win < hardEnd; b++) {
                if (env[b] >= off) lastLoud = b;
            }
            int end = Math.min(hardEnd, (lastLoud + 1) * win + tail);
            float slicePeak = 0;
            for (int i = start; i < end; i++) slicePeak = Math.max(slicePeak, Math.abs(x[i]));
            out.add(new Slice(start, onset, end, slicePeak));
            prevEnd = end;
        }
        return out;
    }

    /** Copies a slice with a short fade-out so a cut tail never clicks. */
    public static float[] extract(float[] x, Slice s, int rate) {
        float[] d = Arrays.copyOfRange(x, s.start, s.end);
        int fade = Math.min(d.length, frames(2, rate));
        for (int i = 0; i < fade; i++) d[d.length - 1 - i] *= i / (float) fade;
        return d;
    }

    private static int frames(double ms, int rate) {
        return (int) Math.round(ms * rate / 1000.0);
    }
}
