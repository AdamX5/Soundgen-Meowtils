package meowtils.extension.soundgen.tools;

import meowtils.extension.soundgen.engine.AudioEngine;
import meowtils.extension.soundgen.engine.Preview;
import meowtils.extension.soundgen.engine.SampleBank;
import meowtils.extension.soundgen.engine.SampleSet;
import meowtils.extension.soundgen.engine.Sound;
import meowtils.extension.soundgen.engine.WavDecoder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command-line helper for building sound packs. Runs from the built .meowtils file with
 * plain Java; it never touches Minecraft classes.
 *
 *   soundtool init    <pack>
 *   soundtool split   <take.wav | folder> <slot folder> [options]
 *   soundtool report  <pack>
 *   soundtool preview <pack> <slot> <out.wav> [--cps N] [--seconds N]
 */
public final class SoundTool {
    private static final Pattern NUMBERED = Pattern.compile("^(.*)_(\\d+)\\.wav$", Pattern.CASE_INSENSITIVE);

    private SoundTool() {
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        if (args.length == 0) {
            usage();
            return;
        }
        try {
            String[] rest = Arrays.copyOfRange(args, 1, args.length);
            switch (args[0].toLowerCase()) {
                case "init":
                    init(rest);
                    break;
                case "split":
                    split(rest);
                    break;
                case "report":
                    report(rest);
                    break;
                case "preview":
                    preview(rest);
                    break;
                default:
                    usage();
                    System.exit(2);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println();
            usage();
            System.exit(2);
        } catch (IOException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void usage() {
        System.out.println(String.join("\n",
                "SoundGen sound tool",
                "",
                "  init <pack>",
                "      Create the slot folders for a pack.",
                "",
                "  split <take.wav | folder of takes> <slot folder> [options]",
                "      Cut a long recording of many clicks/presses into one file per hit.",
                "      New files are numbered after the existing ones, so re-recordings add up.",
                "      --name <prefix>       file name prefix (default: the slot folder's name)",
                "      --alternate <folder>  press/release takes: the 2nd hit of each pair goes here",
                "      --pair-window <ms>    max press-to-release time when pairing (default 400);",
                "                            hold each key briefly and pause longer than this between presses",
                "      --threshold <dB>      quietest hit to keep, below the loudest (default 30)",
                "      --gap <ms>            minimum spacing between hits (default 40)",
                "      --max <ms>            longest hit kept (default 400)",
                "      --dry-run             only list what would be cut",
                "",
                "  report <pack>",
                "      Per slot: variation count, levels, lengths, and problems to fix.",
                "",
                "  preview <pack> <slot> <out.wav> [--cps N] [--seconds N] [--order shuffle|random]",
                "      Render a slot through the real engine to a WAV, to audition the set."));
    }

    // ------------------------------------------------------------------ init

    private static void init(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("init needs exactly one pack folder");
        File pack = new File(args[0]);
        List<String> warnings = new ArrayList<String>();
        SampleBank.ensureLayout(pack, warnings);
        for (String w : warnings) System.out.println("warning: " + w);
        System.out.println("pack ready: " + pack.getAbsolutePath());
    }

    // ------------------------------------------------------------------ split

    private static void split(String[] args) throws IOException {
        Splitter.Options o = new Splitter.Options();
        String name = null;
        File alternate = null;
        boolean dryRun = false;
        double pairWindowMs = 400;
        List<String> positional = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--name":
                    name = value(args, ++i);
                    break;
                case "--alternate":
                    alternate = new File(value(args, ++i));
                    break;
                case "--threshold":
                    o.thresholdDb = number(args, ++i);
                    break;
                case "--gap":
                    o.minGapMs = number(args, ++i);
                    break;
                case "--max":
                    o.maxLengthMs = number(args, ++i);
                    break;
                case "--pair-window":
                    pairWindowMs = number(args, ++i);
                    break;
                case "--dry-run":
                    dryRun = true;
                    break;
                default:
                    if (args[i].startsWith("--")) throw new IllegalArgumentException("unknown option " + args[i]);
                    positional.add(args[i]);
            }
        }
        if (positional.size() != 2) throw new IllegalArgumentException("split needs <take.wav | folder> <slot folder>");
        File input = new File(positional.get(0));
        File out = new File(positional.get(1));
        String prefix = name != null ? name : out.getName();
        String altPrefix = alternate == null ? null : (name != null ? name : alternate.getName());

        List<File> takes = new ArrayList<File>();
        if (input.isDirectory()) {
            File[] files = input.listFiles((dir, n) -> n.toLowerCase().endsWith(".wav"));
            if (files != null) {
                Arrays.sort(files);
                takes.addAll(Arrays.asList(files));
            }
        } else {
            takes.add(input);
        }
        if (takes.isEmpty()) throw new IllegalArgumentException("no .wav takes found at " + input);
        if (!dryRun) {
            mkdirs(out);
            if (alternate != null) mkdirs(alternate);
        }

        int next = nextNumber(out, prefix);
        int nextAlt = alternate == null ? 0 : nextNumber(alternate, altPrefix);
        int written = 0;
        int clipped = 0;
        int unpaired = 0;
        for (File take : takes) {
            WavDecoder.Decoded d = WavDecoder.decode(take);
            List<Splitter.Slice> slices = Splitter.find(d.mono, d.rate, o);
            System.out.printf("%s: %d hits found (%.1f s, %d Hz, %d ch)%n",
                    take.getName(), slices.size(), d.mono.length / (double) d.rate, d.rate, d.channels);
            // role per hit: 0 = main folder, 1 = alternate folder, -1 = skipped (unpaired)
            int[] role = new int[slices.size()];
            if (alternate != null) {
                long window = (long) (pairWindowMs * d.rate / 1000);
                java.util.Arrays.fill(role, -1);
                for (int k = 0; k < slices.size(); k++) {
                    if (k + 1 < slices.size() && slices.get(k + 1).onset - slices.get(k).onset <= window) {
                        role[k] = 0;
                        role[k + 1] = 1;
                        k++;
                    }
                }
            }
            for (int k = 0; k < slices.size(); k++) {
                Splitter.Slice s = slices.get(k);
                if (role[k] < 0) {
                    unpaired++;
                    System.out.printf("  %-24s at %7.3f s  no partner within %.0f ms%n", "(skipped)", s.onset / (double) d.rate, pairWindowMs);
                    continue;
                }
                boolean toAlt = role[k] == 1;
                File dir = toAlt ? alternate : out;
                String file = String.format("%s_%03d.wav", toAlt ? altPrefix : prefix, toAlt ? nextAlt++ : next++);
                boolean clip = s.peak >= 0.999f;
                if (clip) clipped++;
                System.out.printf("  %-24s at %7.3f s  %5.0f ms  peak %6.1f dB%s%n", file, s.onset / (double) d.rate,
                        (s.end - s.start) * 1000.0 / d.rate, db(s.peak), clip ? "  CLIPPED" : "");
                if (!dryRun) {
                    WavWriter.write24(new File(dir, file), Splitter.extract(d.mono, s, d.rate), d.rate);
                    written++;
                }
            }
        }
        System.out.println(dryRun ? "dry run: nothing written" : written + " files written");
        if (clipped > 0) {
            System.out.println(clipped + " hits clipped: lower the mic gain and re-record those, they will sound harsh");
        }
        if (unpaired > 0) {
            System.out.println(unpaired + " hits had no partner and were skipped (a missed quiet release, a double press, "
                    + "or pauses shorter than --pair-window). Lower --threshold if releases are being missed.");
        }
    }

    private static int nextNumber(File dir, String prefix) {
        int max = 0;
        File[] files = dir.listFiles();
        if (files == null) return 1;
        for (File f : files) {
            Matcher m = NUMBERED.matcher(f.getName());
            if (m.matches() && m.group(1).equalsIgnoreCase(prefix)) {
                max = Math.max(max, Integer.parseInt(m.group(2)));
            }
        }
        return max + 1;
    }

    // ------------------------------------------------------------------ report

    /** Recommended minimum variation counts; fewer is audible as repetition at speed. */
    private static int recommended(Sound s) {
        switch (s) {
            case DRAG:
            case BUTTERFLY:
                return 40;
            case DRAG_LOOP:
                return 6;
            case DRAG_END:
                return 8;
            case KEY_DOWN:
            case KEY_UP:
                return 20;
            default:
                return 12; // per-key overrides
        }
    }

    private static void report(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("report needs exactly one pack folder");
        File pack = new File(args[0]);
        if (!pack.isDirectory()) throw new IOException("no such pack folder: " + pack);
        System.out.printf("%-12s %5s %9s %15s %13s %9s  %s%n", "slot", "files", "rate", "peak dB (range)", "length ms", "lead ms", "notes");
        int total = 0;
        for (Sound sound : Sound.values()) {
            File dir = new File(pack, sound.folder);
            File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".wav"));
            int n = files == null ? 0 : files.length;
            total += n;
            if (n == 0) {
                String using = sound.fallback != null ? "uses " + sound.fallback.folder : "placeholder";
                System.out.printf("%-12s %5d %9s %15s %13s %9s  %s%n", sound.folder, 0, "-", "-", "-", "-", using);
                continue;
            }
            float minPk = Float.MAX_VALUE, maxPk = 0;
            double minLen = Double.MAX_VALUE, maxLen = 0, maxLead = 0;
            int clipped = 0, stereo = 0, bad = 0;
            java.util.Set<Integer> rates = new java.util.TreeSet<Integer>();
            for (File f : files) {
                WavDecoder.Decoded d;
                try {
                    d = WavDecoder.decode(f);
                } catch (IOException | RuntimeException e) {
                    bad++;
                    System.out.println("  unreadable: " + f.getName() + " (" + e.getMessage() + ")");
                    continue;
                }
                float pk = 0;
                int onset = -1;
                for (float v : d.mono) pk = Math.max(pk, Math.abs(v));
                for (int i = 0; i < d.mono.length && onset < 0; i++) if (Math.abs(d.mono[i]) >= pk * 0.08f) onset = i;
                minPk = Math.min(minPk, pk);
                maxPk = Math.max(maxPk, pk);
                double len = d.mono.length * 1000.0 / d.rate;
                minLen = Math.min(minLen, len);
                maxLen = Math.max(maxLen, len);
                maxLead = Math.max(maxLead, Math.max(0, onset) * 1000.0 / d.rate);
                if (pk >= 0.999f) clipped++;
                if (d.channels > 1) stereo++;
                rates.add(d.rate);
            }
            List<String> notes = new ArrayList<String>();
            if (n < recommended(sound)) notes.add("aim for " + recommended(sound) + "+");
            if (clipped > 0) notes.add(clipped + " clipped");
            if (bad > 0) notes.add(bad + " unreadable");
            if (maxPk > 0 && db(maxPk) - db(minPk) > 6) notes.add("level spread " + Math.round(db(maxPk) - db(minPk)) + " dB (Match Levels evens it)");
            if (!sound.loop && maxLead > 20) notes.add("up to " + Math.round(maxLead) + " ms lead silence (Auto Trim cuts it)");
            if (!sound.loop && maxLen > 600) notes.add("long files: is this one hit per file?");
            if (sound.loop && minLen < 1500) notes.add("loop takes under 1.5 s repeat noticeably");
            if (stereo > 0) notes.add(stereo + " stereo (mixed to mono)");
            if (!rates.equals(java.util.Collections.singleton(AudioEngine.RATE))) notes.add("resampled to 48 kHz");
            System.out.printf("%-12s %5d %9s %15s %13s %9.0f  %s%n", sound.folder, n, rateText(rates),
                    String.format("%.0f..%.0f", db(minPk), db(maxPk)),
                    String.format("%.0f..%.0f", minLen, maxLen), maxLead,
                    notes.isEmpty() ? "ok" : String.join("; ", notes));
        }
        System.out.println(total + " files in " + pack.getAbsolutePath());
    }

    private static String rateText(java.util.Set<Integer> rates) {
        if (rates.isEmpty()) return "-";
        if (rates.size() == 1) return rates.iterator().next() + "";
        return "mixed";
    }

    // ------------------------------------------------------------------ preview

    private static void preview(String[] args) throws IOException {
        double cps = 15, seconds = 6;
        int order = SampleSet.SHUFFLE;
        List<String> positional = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cps":
                    cps = number(args, ++i);
                    break;
                case "--seconds":
                    seconds = number(args, ++i);
                    break;
                case "--order":
                    order = "random".equalsIgnoreCase(value(args, ++i)) ? SampleSet.RANDOM : SampleSet.SHUFFLE;
                    break;
                default:
                    if (args[i].startsWith("--")) throw new IllegalArgumentException("unknown option " + args[i]);
                    positional.add(args[i]);
            }
        }
        if (positional.size() != 3) throw new IllegalArgumentException("preview needs <pack> <slot> <out.wav>");
        Sound sound = null;
        for (Sound s : Sound.values()) if (s.folder.equalsIgnoreCase(positional.get(1))) sound = s;
        if (sound == null) throw new IllegalArgumentException("unknown slot " + positional.get(1));

        SampleBank.Result bank = SampleBank.load(new File(positional.get(0)), true, true);
        for (String w : bank.warnings) System.out.println("warning: " + w);
        SampleSet set = bank.sets[sound.ordinal()];
        float[] audio = Preview.render(bank.sets, sound, cps, seconds, 3, 0.1f, 0.03f, order);
        File out = new File(positional.get(2));
        WavWriter.write24(out, audio, AudioEngine.RATE);
        System.out.printf("%s: %d variation(s)%s, %.0f hits/s for %.0f s -> %s%n", sound.folder, set.size(),
                set.placeholder ? " (placeholder)" : "", cps, seconds, out.getAbsolutePath());
    }

    // ------------------------------------------------------------------ helpers

    private static String value(String[] args, int i) {
        if (i >= args.length) throw new IllegalArgumentException("missing value after " + args[i - 1]);
        return args[i];
    }

    private static double number(String[] args, int i) {
        String v = value(args, i);
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number: " + v);
        }
    }

    private static double db(float x) {
        return x <= 0 ? -120 : 20 * Math.log10(x);
    }

    private static void mkdirs(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("could not create " + dir);
    }
}
