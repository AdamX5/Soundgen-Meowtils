package meowtils.extension.soundgen.engine;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loads every {@link Sound} folder into memory, fully decoded and resampled to the engine
 * rate, so the audio thread never touches the disk or decodes anything.
 *
 * Meant to run off the game thread; the result is handed to {@link AudioEngine#setSamples}
 * in one reference swap.
 */
public final class SampleBank {
    /** Leading-edge threshold relative to the sample's peak for auto-trim (about -22 dB). */
    private static final float ONSET_THRESHOLD = 0.08f;
    /** Tail threshold relative to peak (about -55 dB). */
    private static final float TAIL_THRESHOLD = 0.0018f;
    /** Room kept before the onset: the soft start of a press is part of its sound. */
    private static final double PRE_ROLL_MS = 10.0;
    private static final double FADE_IN_MS = 3.0;
    /** Ring-out kept after the sound falls under the tail threshold. */
    private static final double TAIL_PAD_MS = 30.0;
    private static final double FADE_OUT_MS = 10.0;
    private static final double LOOP_CROSSFADE_MS = 10.0;
    /** Level matching never moves a variation by more than this (12 dB), so noise isn't boosted. */
    private static final float MAX_MATCH_GAIN = 4f;

    public static final class Result {
        public final SampleSet[] sets;
        public final int recorded;
        public final int placeholders;
        public final List<String> warnings;

        Result(SampleSet[] sets, int recorded, int placeholders, List<String> warnings) {
            this.sets = sets;
            this.recorded = recorded;
            this.placeholders = placeholders;
            this.warnings = warnings;
        }
    }

    private SampleBank() {
    }

    /**
     * @param root      the pack folder (one sub-folder per {@link Sound})
     * @param autoTrim  cut leading/trailing silence from one-shots
     * @param matchLevels scale each variation's peak to its set's median peak, so a pile of
     *                  re-recordings made at slightly different distances plays evenly
     */
    public static Result load(File root, boolean autoTrim, boolean matchLevels) {
        List<String> warnings = new ArrayList<String>();
        ensureLayout(root, warnings);

        int count = Sound.count();
        Sample[][] own = new Sample[count][];
        int recorded = 0;
        for (Sound sound : Sound.values()) {
            own[sound.ordinal()] = loadFolder(new File(root, sound.folder), sound, autoTrim, warnings);
            if (matchLevels && !sound.loop) matchLevels(own[sound.ordinal()]);
            recorded += own[sound.ordinal()].length;
        }

        SampleSet[] sets = new SampleSet[count];
        int placeholders = 0;
        for (Sound sound : Sound.values()) {
            Sample[] samples = own[sound.ordinal()];
            for (Sound f = sound.fallback; samples.length == 0 && f != null; f = f.fallback) {
                samples = own[f.ordinal()];
            }
            if (samples.length > 0) {
                sets[sound.ordinal()] = new SampleSet(samples, false);
            } else {
                Sample[] synth = sound.placeholder.build(AudioEngine.RATE, sound.folder);
                if (sound.loop) synth = loopable(synth);
                sets[sound.ordinal()] = new SampleSet(synth, true);
                placeholders++;
            }
        }
        return new Result(sets, recorded, placeholders, warnings);
    }

    private static Sample[] loadFolder(File dir, Sound sound, boolean autoTrim, List<String> warnings) {
        File[] files = dir.listFiles();
        if (files == null) return new Sample[0];
        Arrays.sort(files);
        List<Sample> out = new ArrayList<Sample>();
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".wav")) continue;
            try {
                WavDecoder.Decoded decoded = WavDecoder.decode(file);
                float[] data = resample(decoded.mono, decoded.rate, AudioEngine.RATE);
                if (sound.loop) {
                    data = crossfadeLoop(data);
                } else if (autoTrim) {
                    data = trim(data);
                }
                if (data.length < 2) {
                    warnings.add(sound.folder + "/" + file.getName() + ": silent or empty, skipped");
                    continue;
                }
                out.add(new Sample(data, sound.folder + "/" + file.getName()));
            } catch (IOException | RuntimeException e) {
                warnings.add(sound.folder + "/" + file.getName() + ": " + e.getMessage());
            }
        }
        return out.toArray(new Sample[0]);
    }

    public static float peak(float[] data) {
        float peak = 0;
        for (float v : data) peak = Math.max(peak, Math.abs(v));
        return peak;
    }

    /** Scales every variation's peak towards the set's median peak (in place). */
    static void matchLevels(Sample[] samples) {
        if (samples.length < 2) return;
        float[] peaks = new float[samples.length];
        for (int i = 0; i < samples.length; i++) peaks[i] = peak(samples[i].data);
        float[] sorted = peaks.clone();
        Arrays.sort(sorted);
        float target = sorted[sorted.length / 2];
        for (int i = 0; i < samples.length; i++) {
            if (peaks[i] <= 0) continue;
            float g = Math.max(1f / MAX_MATCH_GAIN, Math.min(MAX_MATCH_GAIN, target / peaks[i]));
            float[] d = samples[i].data;
            for (int k = 0; k < d.length; k++) d[k] *= g;
        }
    }

    /** Cubic Hermite resampling. Recording at 48 kHz skips this entirely. */
    public static float[] resample(float[] in, int from, int to) {
        if (from == to || in.length < 2) return in;
        double step = from / (double) to;
        int frames = (int) Math.floor((in.length - 1) / step) + 1;
        float[] out = new float[frames];
        int last = in.length - 1;
        for (int i = 0; i < frames; i++) {
            double p = i * step;
            int i1 = (int) p;
            float t = (float) (p - i1);
            float y0 = in[Math.max(i1 - 1, 0)];
            float y1 = in[i1];
            float y2 = in[Math.min(i1 + 1, last)];
            float y3 = in[Math.min(i1 + 2, last)];
            float c1 = 0.5f * (y2 - y0);
            float c2 = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3;
            float c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);
            out[i] = ((c3 * t + c2) * t + c1) * t + y1;
        }
        return out;
    }

    /**
     * Cuts leading silence so the transient lands exactly when scheduled (a recording with
     * 40 ms of room tone before the click would otherwise play 40 ms late), and cuts the
     * silent tail so voices free up sooner. Short fades stop the cuts from clicking.
     */
    static float[] trim(float[] in) {
        float peak = peak(in);
        if (peak == 0) return new float[0];

        int onset = 0;
        float onsetLevel = peak * ONSET_THRESHOLD;
        while (onset < in.length && Math.abs(in[onset]) < onsetLevel) onset++;
        int start = Math.max(0, onset - ms(PRE_ROLL_MS));

        int end = in.length - 1;
        float tailLevel = peak * TAIL_THRESHOLD;
        while (end > onset && Math.abs(in[end]) < tailLevel) end--;
        end = Math.min(in.length, end + 1 + ms(TAIL_PAD_MS));

        float[] out = Arrays.copyOfRange(in, start, end);
        int fadeIn = start > 0 ? Math.min(ms(FADE_IN_MS), out.length) : 0;
        for (int i = 0; i < fadeIn; i++) out[i] *= i / (float) fadeIn;
        if (end < in.length) {
            int fadeOut = Math.min(ms(FADE_OUT_MS), out.length);
            for (int i = 0; i < fadeOut; i++) out[out.length - 1 - i] *= i / (float) fadeOut;
        }
        return out;
    }

    /**
     * Makes a take loop seamlessly: the last N frames are blended into the first N and then
     * dropped, so reading past the end continues from index 0 without a discontinuity.
     */
    static float[] crossfadeLoop(float[] in) {
        int n = Math.min(ms(LOOP_CROSSFADE_MS), in.length / 4);
        if (n < 2) return in;
        int len = in.length - n;
        float[] out = Arrays.copyOf(in, len);
        for (int i = 0; i < n; i++) {
            float w = i / (float) n;
            out[i] = in[i] * w + in[len + i] * (1 - w);
        }
        return out;
    }

    private static Sample[] loopable(Sample[] samples) {
        Sample[] out = new Sample[samples.length];
        for (int i = 0; i < samples.length; i++) {
            out[i] = new Sample(crossfadeLoop(samples[i].data), samples[i].name);
        }
        return out;
    }

    private static int ms(double ms) {
        return (int) Math.round(ms * AudioEngine.RATE / 1000.0);
    }

    /** Creates the pack's slot folders and README if missing. */
    public static void ensureLayout(File root, List<String> warnings) {
        for (Sound sound : Sound.values()) {
            File dir = new File(root, sound.folder);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                warnings.add("could not create " + dir);
            }
        }
        File readme = new File(root, "README.txt");
        if (readme.exists()) return;
        try (PrintWriter w = new PrintWriter(readme, StandardCharsets.UTF_8.name())) {
            w.println("SoundGen sound pack");
            w.println("===================");
            w.println("One folder per sound slot. Every .wav in a folder is one variation. With");
            w.println("Variation Order = Shuffle, every variation plays once before any repeats.");
            w.println();
            w.println("Format: 48000 Hz mono is ideal; 16/24-bit PCM or 32-bit float. Others are resampled.");
            w.println("One hit per file, except drag_loop (continuous drag-click takes, 2-5 s each).");
            w.println();
            w.println("Easiest way to fill a folder: record one long take of many hits, then");
            w.println("  soundtool split take.wav <this pack>/butterfly");
            w.println("For press+release takes (keys):");
            w.println("  soundtool split take.wav <this pack>/key_down --alternate <this pack>/key_up");
            w.println("Check a pack:  soundtool report <this pack>");
            w.println();
            w.println("Per-key folders (w_down, space_up, other_down, ...) are optional: empty ones use");
            w.println("key_down / key_up. drag_end and right_click use drag. Slots with nothing at all use a");
            w.println("synthesised placeholder.");
            w.println("right_click = single right clicks (Right Style Auto, slow clicking).");
            w.println("Key sizes: key_* = normal keys, tab_* = Tab + Backslash, shift_* = Shifts + Caps Lock,");
            w.println("backspace_* = Backspace + Enter, space_* = space bar. other_* overrides key_* for keys");
            w.println("outside the movement/jump/sneak/sprint binds (hotbar, Esc, chat typing...).");
            w.println();
            w.println("After adding files press 'Reload Sounds' in the module settings (Sounds section).");
        } catch (IOException e) {
            warnings.add("could not write README: " + e.getMessage());
        }
    }
}
