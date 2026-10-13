package meowtils.extension.soundgen.engine;

import meowtils.extension.soundgen.input.ClickChannel;
import meowtils.extension.soundgen.input.KeyChannel;
import meowtils.extension.soundgen.input.Timing;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline checks for the engine: renders blocks without an audio device against a fake
 * clock, so timing can be asserted to the frame. Plain main() so it needs no test framework:
 *
 *   javac -d out $(find src/main/java/meowtils/extension/soundgen/engine src/main/java/meowtils/extension/soundgen/input src/main/java/meowtils/extension/soundgen/tools src/test/java -name '*.java')
 *   java -cp out meowtils.extension.soundgen.engine.EngineSelfTest
 */
public final class EngineSelfTest {
    private static long fakeNanos;
    private static int failures;

    public static void main(String[] args) throws Exception {
        schedulingIsFrameAccurate();
        loopStopsWithFadeAndFreesVoice();
        voiceStealingStaysBounded();
        wavFormatsDecode();
        trimRemovesLeadingSilence();
        loopCrossfadeIsSeamless();
        spreadDistributesClicksAcrossTick();
        keyReleaseRespectsMinHold();
        placeholdersCoverEverySlot();
        shuffleBagPlaysEveryVariationBeforeRepeating();
        matchLevelsEvensPeaks();
        splitterFindsEveryHit();
        splitterSeparatesFastButterflyTake();
        toolSplitsReportsAndPreviews();
        chokeCutsPreviousTail();
        autoStyleSwitchesToRattleWhenFast();
        otherKeysSkipHandledAndPairRelease();
        keySizesUseTheirOwnSlots();
        fallbackFollowsTheChain();
        importSortsTakesIntoThePack();
        namedFilesMapToTheRightSlots();
        importFilesHandlesQuietNamedTakes();
        slicesKeepRoomAroundEachHit();
        replaceReimportsOnlyItsFolders();
        tapModePlaysOneSound();
        autoPlaysBurstBeforeLoop();
        cpuCost();
        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        if (failures > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ tests

    private static void schedulingIsFrameAccurate() {
        AudioEngine e = engineWithImpulse();
        e.play(Sound.BUTTERFLY, 1f, ms(10));
        e.play(Sound.BUTTERFLY, 1f, ms(37.5));
        List<Integer> onsets = onsets(render(e, 4800));
        check("onsets at 480 and 1800 frames, got " + onsets, onsets.size() == 2 && onsets.get(0) == 480 && onsets.get(1) == 1800);
    }

    private static void loopStopsWithFadeAndFreesVoice() {
        AudioEngine e = new AudioEngine(() -> fakeNanos);
        fakeNanos = 0;
        float[] dc = new float[4800];
        java.util.Arrays.fill(dc, 0.5f);
        e.setSamples(only(Sound.DRAG_LOOP, dc));
        e.setGainJitter(0);
        e.loopStart(Sound.DRAG_LOOP, 1, 1f, 0);
        e.loopStart(Sound.DRAG_LOOP, 1, 1f, 0); // duplicate start must be ignored
        float[] a = render(e, 9600);              // runs past the sample length, so it must wrap
        check("loop plays one voice at steady level", Math.abs(a[9000] - 0.5f) < 0.01f && e.activeVoices() == 1);
        e.loopStop(1, 10, fakeNanos);
        float[] b = render(e, 2400);
        check("loop faded to silence", Math.abs(b[600]) < 1e-4 && Math.abs(b[100]) > 0.1f);
        render(e, 128);
        check("loop voice freed", e.activeVoices() == 0);
    }

    private static void voiceStealingStaysBounded() {
        AudioEngine e = engineWithImpulse();
        for (int i = 0; i < 200; i++) e.play(Sound.BUTTERFLY, 0.01f, ms(1));
        render(e, 128);
        check("voices capped at " + AudioEngine.MAX_VOICES, e.activeVoices() <= AudioEngine.MAX_VOICES && e.stolenVoices() > 0);
    }

    private static void wavFormatsDecode() throws IOException {
        File dir = Files.createTempDirectory("sgtest").toFile();
        float[] ref = new float[441];
        for (int i = 0; i < ref.length; i++) ref[i] = (float) Math.sin(i * 0.05) * 0.5f;

        WavDecoder.Decoded pcm16 = WavDecoder.decode(writeWav(new File(dir, "a.wav"), ref, 44100, 2, 1, 16, false));
        WavDecoder.Decoded pcm24 = WavDecoder.decode(writeWav(new File(dir, "b.wav"), ref, 48000, 1, 1, 24, false));
        WavDecoder.Decoded flt = WavDecoder.decode(writeWav(new File(dir, "c.wav"), ref, 48000, 1, 3, 32, true));
        check("16-bit stereo decodes", pcm16.rate == 44100 && Math.abs(pcm16.mono[100] - ref[100]) < 1e-3);
        check("24-bit decodes", Math.abs(pcm24.mono[100] - ref[100]) < 1e-5);
        check("float extensible decodes", Math.abs(flt.mono[100] - ref[100]) < 1e-6);
        float[] up = SampleBank.resample(pcm16.mono, 44100, 48000);
        check("resample length 441->480 (got " + up.length + ")", Math.abs(up.length - 480) <= 1);
    }

    private static void trimRemovesLeadingSilence() {
        float[] in = new float[4800];
        for (int i = 0; i < 1920; i++) in[i] = 0.001f * (i % 2 == 0 ? 1 : -1); // 40 ms of room tone
        for (int i = 1920; i < 2400; i++) in[i] = 0.8f * (float) Math.exp(-(i - 1920) / 50.0);
        float[] out = SampleBank.trim(in);
        int onset = 0;
        while (Math.abs(out[onset]) < 0.06f) onset++;
        check("trim keeps ~10 ms before the onset (got " + onset + " frames)", onset >= 470 && onset <= 500);
        check("tail trimmed but keeps its ring-out (" + out.length + " frames)", out.length < 3000 && out.length > 1500);
    }

    private static void loopCrossfadeIsSeamless() {
        float[] in = new float[4800];
        for (int i = 0; i < in.length; i++) in[i] = (float) Math.sin(i * 2 * Math.PI * 100 / 48000.0 + 0.3);
        float[] loop = SampleBank.crossfadeLoop(in);
        float jump = Math.abs(loop[0] - loop[loop.length - 1]);
        check("loop seam has no jump (" + jump + ")", jump < 0.05f);
    }

    private static void spreadDistributesClicksAcrossTick() {
        AudioEngine e = engineWithImpulse();
        Timing timing = new Timing();
        timing.spread = true;
        timing.clickJitterMs = 0;
        ClickChannel channel = new ClickChannel(e, timing, Sound.BUTTERFLY, Sound.BUTTERFLY, Sound.DRAG_LOOP, Sound.DRAG_END, 1,
                new boolean[]{true, false});
        channel.choke = false; // impulses: choke would only fade tails, but keep this test about spacing
        channel.click(0, 0);
        channel.click(0, 0);
        channel.click(0, 0);
        channel.click(1, 0); // unselected source: counted, silent
        channel.tick(0);
        List<Integer> onsets = onsets(render(e, 4800));
        boolean spaced = onsets.size() == 3;
        for (int i = 0; spaced && i < 3; i++) {
            int lo = (int) (2400 * (i + 0.2) / 3), hi = (int) (2400 * (i + 0.8) / 3);
            spaced = onsets.get(i) >= lo && onsets.get(i) <= hi;
        }
        check("3 clicks spread across one tick, got " + onsets, spaced);
        check("debug counts per source", channel.takeCount(0) == 3 && channel.takeCount(1) == 1);
    }

    private static void keyReleaseRespectsMinHold() {
        AudioEngine e = engineWithImpulse(Sound.KEY_DOWN, Sound.KEY_UP);
        Timing timing = new Timing();
        timing.keyJitterMs = 0;
        KeyChannel k = new KeyChannel(e, timing, Sound.KEY_DOWN, Sound.KEY_UP, Sound.KEY_TAP, 2);
        k.minHoldMs = 30;
        k.state(0, true, 0);
        k.state(0, false, ms(5)); // 5 ms tap must still release 30 ms after the press
        List<Integer> onsets = onsets(render(e, 4800));
        check("press at 0, release at 30 ms, got " + onsets, onsets.size() == 2 && onsets.get(0) == 0 && onsets.get(1) == 1440);
    }

    private static void placeholdersCoverEverySlot() throws IOException {
        File dir = Files.createTempDirectory("sgbank").toFile();
        SampleBank.Result r = SampleBank.load(dir, true, true);
        boolean all = true;
        for (SampleSet s : r.sets) all &= s != null && s.size() > 0;
        check("every slot has sound with no recordings", all && r.recorded == 0 && r.warnings.isEmpty());
        check("folders and README created", new File(dir, "drag_loop").isDirectory() && new File(dir, "README.txt").isFile());
    }

    private static void shuffleBagPlaysEveryVariationBeforeRepeating() {
        Sample[] samples = new Sample[7];
        for (int i = 0; i < samples.length; i++) samples[i] = new Sample(new float[]{i}, "v" + i);
        SampleSet set = new SampleSet(samples, false);
        Rng rng = new Rng(42);
        boolean ok = true;
        Sample prev = null;
        for (int bag = 0; bag < 50 && ok; bag++) {
            java.util.Set<Sample> seen = new java.util.HashSet<Sample>();
            for (int k = 0; k < samples.length; k++) {
                Sample s = set.pick(rng, SampleSet.SHUFFLE);
                ok &= seen.add(s) && s != prev;
                prev = s;
            }
        }
        check("shuffle: all 7 variations per round, never back-to-back", ok);
        int repeats = 0;
        prev = null;
        for (int k = 0; k < 2000; k++) {
            Sample s = set.pick(rng, SampleSet.RANDOM);
            if (s == prev) repeats++;
            prev = s;
        }
        check("random: never back-to-back", repeats == 0);
    }

    private static void matchLevelsEvensPeaks() {
        Sample[] s = {new Sample(new float[]{0.2f}, "a"), new Sample(new float[]{0.4f}, "b"), new Sample(new float[]{0.8f}, "c"),
                new Sample(new float[]{0.01f}, "too quiet")};
        SampleBank.matchLevels(s);
        check("levels matched to median, boost capped at 12 dB",
                Math.abs(s[0].data[0] - 0.4f) < 1e-6 && Math.abs(s[2].data[0] - 0.4f) < 1e-6 && Math.abs(s[3].data[0] - 0.04f) < 1e-6);
    }

    private static void splitterFindsEveryHit() {
        int rate = 44100;
        float[] take = new float[rate * 4];
        java.util.Random r = new java.util.Random(3);
        for (int i = 0; i < take.length; i++) take[i] = (float) (r.nextGaussian() * 0.002); // room noise
        int[] at = new int[12];
        for (int k = 0; k < at.length; k++) {
            at[k] = (int) (rate * (0.2 + k * 0.3 + r.nextDouble() * 0.05));
            double amp = 0.3 + 0.5 * r.nextDouble();
            for (int i = 0; i < rate / 50; i++) take[at[k] + i] += (float) (amp * Math.exp(-i / (rate * 0.003)) * Math.sin(i * 0.9));
        }
        java.util.List<meowtils.extension.soundgen.tools.Splitter.Slice> found =
                meowtils.extension.soundgen.tools.Splitter.find(take, rate, new meowtils.extension.soundgen.tools.Splitter.Options());
        boolean ok = found.size() == at.length;
        for (int k = 0; ok && k < at.length; k++) {
            ok = Math.abs(found.get(k).onset - at[k]) <= rate / 1000 && found.get(k).start <= found.get(k).onset;
        }
        check("splitter: 12 hits in a noisy take, onsets within 1 ms (found " + found.size() + ")", ok);
    }

    private static void splitterSeparatesFastButterflyTake() {
        int rate = 48000;
        float[] take = new float[rate * 2];
        int n = 0;
        for (int t = rate / 10; t + 2000 < take.length; t += rate / 22) { // 22 clicks per second
            for (int i = 0; i < 1500; i++) take[t + i] += (float) (0.6 * Math.exp(-i / 90.0) * Math.sin(i * 1.3));
            n++;
        }
        meowtils.extension.soundgen.tools.Splitter.Options o = new meowtils.extension.soundgen.tools.Splitter.Options();
        o.minGapMs = 25;
        int found = meowtils.extension.soundgen.tools.Splitter.find(take, rate, o).size();
        check("splitter: 22 CPS take separated (" + found + "/" + n + ")", found == n);
    }

    private static void toolSplitsReportsAndPreviews() throws IOException {
        File root = Files.createTempDirectory("sgtool").toFile();
        File pack = new File(root, "pack");
        meowtils.extension.soundgen.tools.SoundTool.main(new String[]{"init", pack.getPath()});
        // 5 press/release pairs (150 ms hold, 700 ms apart) plus one stray hit that must be skipped
        float[] take = new float[48000 * 5];
        int[] hits = {4800, 12000, 38400, 45600, 72000, 79200, 105600, 112800, 139200, 146400, 180000};
        for (int t : hits) {
            for (int i = 0; i < 1000; i++) take[t + i] = (float) (0.5 * Math.exp(-i / 120.0) * Math.sin(i * 0.7));
        }
        File wav = writeWav(new File(root, "take.wav"), take, 48000, 1, 1, 24, false);
        File down = new File(pack, "key_down"), up = new File(pack, "key_up");
        meowtils.extension.soundgen.tools.SoundTool.main(new String[]{"split", wav.getPath(), down.getPath(), "--alternate", up.getPath()});
        meowtils.extension.soundgen.tools.SoundTool.main(new String[]{"split", wav.getPath(), down.getPath(), "--alternate", up.getPath()});
        check("split pairs press/release, skips strays, numbers re-takes after existing files",
                new File(down, "key_down_010.wav").isFile() && new File(up, "key_up_010.wav").isFile()
                        && !new File(down, "key_down_011.wav").exists() && !new File(up, "key_up_011.wav").exists());
        SampleBank.Result bank = SampleBank.load(pack, true, true);
        check("split files load back as recordings", bank.recorded == 20 && !bank.sets[Sound.W_DOWN.ordinal()].placeholder);
        meowtils.extension.soundgen.tools.SoundTool.main(new String[]{"report", pack.getPath()});
        File preview = new File(root, "preview.wav");
        meowtils.extension.soundgen.tools.SoundTool.main(new String[]{"preview", pack.getPath(), "key_down", preview.getPath(), "--seconds", "2"});
        check("preview rendered", preview.length() > 48000 * 3);
    }

    private static void chokeCutsPreviousTail() {
        float[] tone = new float[4800]; // 100 ms of steady level: easy to see overlap
        java.util.Arrays.fill(tone, 0.4f);
        for (int group : new int[]{0, 1}) {
            AudioEngine e = new AudioEngine(() -> fakeNanos);
            fakeNanos = 0;
            e.setSamples(only(Sound.BUTTERFLY, tone));
            e.setGainJitter(0);
            e.setPitchJitter(0);
            e.setChokeFadeMs(2);
            e.play(Sound.BUTTERFLY, 1f, 0, group);
            e.play(Sound.BUTTERFLY, 1f, ms(20), group);
            float[] out = render(e, 4800);
            float at30ms = out[1440];
            if (group == 0) {
                check("without choke both hits overlap (" + at30ms + ")", Math.abs(at30ms - 0.8f) < 0.01f);
            } else {
                check("with choke the earlier hit fades out over 2 ms (" + at30ms + ")",
                        Math.abs(at30ms - 0.4f) < 0.01f && Math.abs(out[960 + 48] - 0.6f) < 0.03f && Math.abs(out[960 + 100] - 0.4f) < 0.01f);
            }
        }
    }

    private static void autoStyleSwitchesToRattleWhenFast() {
        AudioEngine e = engineWithImpulse(Sound.RIGHT_CLICK);
        float[] dc = new float[4800];
        java.util.Arrays.fill(dc, 0.3f);
        SampleSet[] sets = new SampleSet[Sound.count()];
        float[] impulse = new float[64];
        impulse[0] = 1f;
        sets[Sound.RIGHT_CLICK.ordinal()] = new SampleSet(new Sample[]{new Sample(impulse, "single")}, false);
        sets[Sound.DRAG_LOOP.ordinal()] = new SampleSet(new Sample[]{new Sample(dc, "rattle")}, false);
        e.setSamples(sets);
        Timing timing = new Timing();
        timing.spread = false;
        timing.clickJitterMs = 0;
        ClickChannel c = new ClickChannel(e, timing, Sound.DRAG, Sound.RIGHT_CLICK, Sound.DRAG_LOOP, Sound.DRAG_END, 1,
                new boolean[]{true});
        c.style = ClickChannel.STYLE_AUTO;
        c.dragThresholdCps = 8;
        c.endClick = false;
        // two slow clicks (500 ms apart): single sounds, no rattle
        c.click(0, 0);
        render(e, 2400);
        c.click(0, ms(500));
        float[] slow = render(e, 24000);
        check("auto: slow clicks play single sounds", !c.isLooping() && onsets(slow).size() == 1);
        // then a 20 CPS hold: rattle starts by the 3rd click
        long t = fakeNanos;
        for (int k = 0; k < 6; k++) c.click(0, t + k * ms(50));
        check("auto: fast clicking switches to the rattle", c.isLooping());
        c.tick(t + ms(250) + ms(200)); // past the 120 ms hold timeout
        check("auto: rattle stops when clicking stops", !c.isLooping());
    }

    private static void otherKeysSkipHandledAndPairRelease() {
        AudioEngine e = engineWithImpulse(Sound.OTHER_DOWN, Sound.OTHER_UP);
        Timing timing = new Timing();
        timing.keyJitterMs = 0;
        meowtils.extension.soundgen.input.KeyboardChannel k =
                new meowtils.extension.soundgen.input.KeyboardChannel(e, timing, Sound.OTHER_DOWN, Sound.OTHER_UP);
        k.minHoldMs = 30;
        k.update(33, true, true, false, 0);          // F pressed: sounds
        k.update(17, true, true, true, 0);           // W owned by its own channel: silent
        k.update(33, false, true, false, ms(100));   // F released: release sounds
        k.update(17, false, true, true, ms(100));    // W released: silent (its press never sounded)
        k.update(2, true, false, false, ms(200));    // "1" while not allowed: silent
        k.update(2, false, true, false, ms(250));    //   ...and so is its release
        List<Integer> on = onsets(render(e, 24000));
        check("other keys: F press+release only, got " + on, on.size() == 2 && on.get(0) == 0 && on.get(1) == 4800);
        check("other keys: one press counted", k.takePresses() == 1);
    }

    private static void keySizesUseTheirOwnSlots() {
        AudioEngine e = engineWithImpulse(Sound.TAB_DOWN, Sound.SPACE_DOWN, Sound.BACKSPACE_DOWN);
        Timing timing = new Timing();
        timing.keyJitterMs = 0;
        meowtils.extension.soundgen.input.KeyboardChannel k =
                new meowtils.extension.soundgen.input.KeyboardChannel(e, timing, Sound.OTHER_DOWN, Sound.OTHER_UP);
        k.release = false;
        k.applyKeySizes();
        k.update(15, true, true, false, 0);          // Tab -> tab_down (loaded)
        k.update(57, true, true, false, ms(20));     // Space -> space_down (loaded)
        k.update(28, true, true, false, ms(40));     // Enter -> backspace_down (loaded)
        k.update(30, true, true, false, ms(60));     // A -> other_down (not loaded here: silent)
        List<Integer> on = onsets(render(e, 4800));
        check("key sizes: Tab, Space, Enter use their own slots, got " + on,
                on.size() == 3 && on.get(0) == 0 && on.get(1) == 960 && on.get(2) == 1920);
    }

    private static void fallbackFollowsTheChain() throws IOException {
        File pack = Files.createTempDirectory("sgchain").toFile();
        SampleBank.ensureLayout(pack, new ArrayList<String>());
        float[] hit = new float[480];
        for (int i = 0; i < hit.length; i++) hit[i] = (float) (0.5 * Math.exp(-i / 60.0) * Math.sin(i * 0.8));
        writeWav(new File(pack, "key_down/k1.wav"), hit, 48000, 1, 1, 16, false);
        SampleBank.Result r = SampleBank.load(pack, true, true);
        check("tab_down -> other_down -> key_down uses the key_down recording",
                !r.sets[Sound.TAB_DOWN.ordinal()].placeholder && !r.sets[Sound.OTHER_DOWN.ordinal()].placeholder
                        && r.sets[Sound.TAB_UP.ordinal()].placeholder);
    }

    private static void importSortsTakesIntoThePack() throws IOException {
        File root = Files.createTempDirectory("sgimport").toFile();
        File takes = new File(root, "recordings/close"), pack = new File(root, "packs/close");
        new File(takes, "butterfly").mkdirs();
        new File(takes, "key").mkdirs();
        new File(takes, "drag_loop").mkdirs();
        new File(takes, "nonsense").mkdirs();
        float[] clicks = new float[48000 * 2];
        for (int k = 0; k < 8; k++) {
            int t = 2400 + k * 9600;
            for (int i = 0; i < 800; i++) clicks[t + i] = (float) (0.5 * Math.exp(-i / 90.0) * Math.sin(i * 0.9));
        }
        writeWav(new File(takes, "butterfly/take1.wav"), clicks, 48000, 1, 1, 16, false);
        float[] keys = new float[48000 * 4];
        for (int k = 0; k < 4; k++) {
            int t = 4800 + k * 38400;
            for (int i = 0; i < 800; i++) keys[t + i] = (float) (0.5 * Math.exp(-i / 90.0) * Math.sin(i * 0.9));
            for (int i = 0; i < 800; i++) keys[t + 9600 + i] = (float) (0.2 * Math.exp(-i / 90.0) * Math.sin(i * 1.1));
        }
        writeWav(new File(takes, "key/take1.wav"), keys, 48000, 1, 1, 16, false);
        float[] rattle = new float[48000 * 3];
        java.util.Arrays.fill(rattle, 0.1f);
        writeWav(new File(takes, "drag_loop/run1.wav"), rattle, 48000, 1, 1, 16, false);
        writeWav(new File(takes, "nonsense/x.wav"), rattle, 48000, 1, 1, 16, false);

        meowtils.extension.soundgen.tools.SoundTool.main(new String[]{"import", takes.getPath(), pack.getPath()});
        boolean ok = new File(pack, "butterfly/butterfly_008.wav").isFile() && !new File(pack, "butterfly/butterfly_009.wav").exists()
                && new File(pack, "key_down/key_down_004.wav").isFile() && new File(pack, "key_up/key_up_004.wav").isFile()
                && new File(pack, "drag_loop/drag_loop_001.wav").isFile();
        check("import: butterfly split, key pairs split into down/up, loop copied", ok);
        meowtils.extension.soundgen.tools.SoundTool.main(new String[]{"import", takes.getPath(), pack.getPath()});
        check("import: running again adds nothing", !new File(pack, "butterfly/butterfly_009.wav").exists()
                && !new File(pack, "drag_loop/drag_loop_002.wav").exists());
        WavDecoder.Decoded loop = WavDecoder.decode(new File(pack, "drag_loop/drag_loop_001.wav"));
        check("import: loop edges trimmed (2.6 s of 3 s)", Math.abs(loop.mono.length - 48000 * 2.6) < 10);
    }

    private static void namedFilesMapToTheRightSlots() {
        String[][] cases = {
                {"quick-small_keys.wav", "quick", "key_tap", null},
                {"press_unpress-small_keys.wav", "pair", "key_down", "key_up"},
                {"quick-space_key.wav", "quick", "space_tap", null},
                {"press_unpress-space_key.wav", "pair", "space_down", "space_up"},
                {"quick-shift_key.wav", "quick", "shift_tap", null},
                {"press_unpress-shift_key.wav", "pair", "shift_down", "shift_up"},
                {"quick-tab_key.wav", "quick", "tab_tap", null},
                {"press_unpress-tab_key.wav", "pair", "tab_down", "tab_up"},
                {"quick-backspace_key.wav", "quick", "backspace_tap", null},
                {"press_unpress-backspace_key.wav", "pair", "backspace_down", "backspace_up"},
                {"drag_click-long.wav", "run", "drag_loop", null},
                {"drag_click-medium.wav", "run", "drag_loop", null},
                {"drag_click-short.wav", "burst", "drag_burst", null},
                {"quick-click.wav", "quick", "butterfly", null},
                {"press_unpress-click.wav", "pair", "mouse_down", "mouse_up"},
        };
        boolean ok = true;
        String bad = "";
        for (String[] c : cases) {
            String got = meowtils.extension.soundgen.tools.SoundTool.describePlan(new File(c[0]));
            String want = c[1] + " " + c[2] + (c[3] == null ? "" : " " + c[3]);
            if (!got.startsWith(want)) {
                ok = false;
                bad += " " + c[0] + "=" + got;
            }
        }
        check("all 15 of the user's file names map to the right slots" + bad, ok);
    }

    private static void importFilesHandlesQuietNamedTakes() throws IOException {
        File root = Files.createTempDirectory("sgnamed").toFile();
        File rec = new File(root, "recordings"), pack = new File(root, "packs/default");
        rec.mkdirs();
        float q = 0.004f; // about -48 dBFS
        java.util.Random r = new java.util.Random(9);
        // 10 taps: press transient + quieter release 45 ms later, 400 ms apart, varied intensity
        float[] taps = new float[48000 * 5];
        for (int k = 0; k < 10; k++) {
            int t = 4800 + k * 19200;
            float amp = q * (0.4f + 0.6f * r.nextFloat());
            for (int i = 0; i < 600; i++) taps[t + i] += amp * (float) (Math.exp(-i / 70.0) * Math.sin(i * 0.9));
            for (int i = 0; i < 600; i++) taps[t + 2160 + i] += amp * 0.4f * (float) (Math.exp(-i / 70.0) * Math.sin(i * 1.2));
        }
        writeWav(new File(rec, "quick-small_keys.wav"), taps, 48000, 1, 3, 32, true);
        // 6 press/release pairs, perfectly uniform spacing (300 ms), so timing can't tell them apart
        float[] pairs = new float[48000 * 5];
        for (int k = 0; k < 12; k++) {
            int t = 4800 + k * 14400;
            float amp = q * (k % 2 == 0 ? 1f : 0.35f);
            for (int i = 0; i < 600; i++) pairs[t + i] += amp * (float) (Math.exp(-i / 70.0) * Math.sin(i * 0.9));
        }
        writeWav(new File(rec, "press_unpress-space_key.wav"), pairs, 48000, 1, 3, 32, true);
        // 2 drag runs of 2 s with ~25 CPS clicks, 1 s silence between
        float[] runs = new float[48000 * 6];
        for (int run = 0; run < 2; run++) {
            for (int t = 4800 + run * 144000; t < 4800 + run * 144000 + 96000; t += 1700 + r.nextInt(600)) {
                for (int i = 0; i < 300; i++) runs[t + i] += q * (float) (Math.exp(-i / 40.0) * Math.sin(i * 1.4));
            }
        }
        writeWav(new File(rec, "drag_click-long.wav"), runs, 48000, 1, 3, 32, true);
        // 3 bursts of 5 clicks (60 ms apart), 700 ms apart
        float[] bursts = new float[48000 * 3];
        for (int b = 0; b < 3; b++) {
            for (int c = 0; c < 5; c++) {
                int t = 4800 + b * 33600 + c * 2880;
                for (int i = 0; i < 300; i++) bursts[t + i] += q * (float) (Math.exp(-i / 40.0) * Math.sin(i * 1.4));
            }
        }
        writeWav(new File(rec, "drag_click-short.wav"), bursts, 48000, 1, 3, 32, true);
        writeWav(new File(rec, "random-name.wav"), bursts, 48000, 1, 3, 32, true);

        meowtils.extension.soundgen.tools.SoundTool.main(new String[]{"import-files", rec.getPath(), pack.getPath()});
        check("quiet taps: 10 taps, each keeping its own release",
                count(new File(pack, "key_tap")) == 10);
        check("uniform press/release: 6 presses and 6 releases, presses louder",
                count(new File(pack, "space_down")) == 6 && count(new File(pack, "space_up")) == 6
                        && peakOf(new File(pack, "space_down/space_down_001.wav")) > 2 * peakOf(new File(pack, "space_up/space_up_001.wav")));
        check("drag runs: 2 loop takes", count(new File(pack, "drag_loop")) == 2);
        check("bursts: 3 burst one-shots", count(new File(pack, "drag_burst")) == 3);
        float loudest = 0;
        for (String d : new String[]{"key_tap", "space_down", "drag_loop", "drag_burst"}) {
            for (File f : new File(pack, d).listFiles()) loudest = Math.max(loudest, peakOf(f));
        }
        check("shared gain lifts the loudest hit to about -1 dB (" + loudest + ")", loudest > 0.8f && loudest <= 0.9f);
        meowtils.extension.soundgen.tools.SoundTool.main(new String[]{"import-files", rec.getPath(), pack.getPath()});
        check("named import: running again adds nothing", count(new File(pack, "key_tap")) == 10);
    }

    private static int count(File dir) {
        File[] f = dir.listFiles((d, n) -> n.endsWith(".wav"));
        return f == null ? 0 : f.length;
    }

    private static float peakOf(File wav) {
        try {
            return SampleBank.peak(WavDecoder.decode(wav).mono);
        } catch (IOException e) {
            return -1;
        }
    }

    private static void slicesKeepRoomAroundEachHit() {
        int rate = 48000;
        float[] x = new float[rate * 2];
        int[] at = {9600, 9600 + 14400}; // two hits 300 ms apart, long-ish ring-out
        for (int t : at) for (int i = 0; i < 9000; i++) x[t + i] += (float) (0.5 * Math.exp(-i / 900.0) * Math.sin(i * 0.5));
        List<meowtils.extension.soundgen.tools.Splitter.Slice> sl =
                meowtils.extension.soundgen.tools.Splitter.find(x, rate, new meowtils.extension.soundgen.tools.Splitter.Options());
        boolean ok = sl.size() == 2
                && sl.get(0).onset - sl.get(0).start >= 700                 // >= ~15 ms lead-in
                && sl.get(0).end <= at[1]                                   // never runs into the next hit
                && sl.get(1).end - at[1] > 4000;                            // ring-out kept (> 80 ms)
        check("slices keep a lead-in and the ring-out, without swallowing the next hit", ok);
    }

    private static void replaceReimportsOnlyItsFolders() throws IOException {
        File root = Files.createTempDirectory("sgreplace").toFile();
        File rec = new File(root, "recordings"), pack = new File(root, "packs/default");
        rec.mkdirs();
        float[] taps = new float[48000 * 2];
        for (int k = 0; k < 4; k++) for (int i = 0; i < 600; i++) taps[4800 + k * 19200 + i] = (float) (0.3 * Math.exp(-i / 70.0) * Math.sin(i * 0.9));
        writeWav(new File(rec, "quick-small_keys.wav"), taps, 48000, 1, 1, 16, false);
        meowtils.extension.soundgen.tools.SoundTool.main(new String[]{"import-files", rec.getPath(), pack.getPath()});
        File keep = new File(pack, "butterfly/mine.wav");
        writeWav(keep, taps, 48000, 1, 1, 16, false);
        meowtils.extension.soundgen.tools.SoundTool.main(new String[]{"import-files", rec.getPath(), pack.getPath(), "--replace"});
        check("--replace: re-imported (still 4 taps, not 8) and other folders untouched",
                count(new File(pack, "key_tap")) == 4 && keep.isFile());
    }

    private static void tapModePlaysOneSound() {
        AudioEngine e = engineWithImpulse(Sound.KEY_DOWN, Sound.KEY_UP, Sound.KEY_TAP);
        Timing timing = new Timing();
        timing.keyJitterMs = 0;
        KeyChannel k = new KeyChannel(e, timing, Sound.KEY_DOWN, Sound.KEY_UP, Sound.KEY_TAP, 1);
        k.releaseMode = KeyChannel.RELEASE_TAP;
        k.state(0, true, 0);
        k.state(0, false, ms(100));
        check("tap mode: one sound per press, no release", onsets(render(e, 9600)).size() == 1);
    }

    private static void autoPlaysBurstBeforeLoop() {
        AudioEngine e = engineWithImpulse(Sound.RIGHT_CLICK, Sound.DRAG_BURST);
        Timing timing = new Timing();
        timing.spread = false;
        timing.clickJitterMs = 0;
        ClickChannel c = new ClickChannel(e, timing, Sound.DRAG, Sound.RIGHT_CLICK, Sound.DRAG_LOOP, Sound.DRAG_END, 1,
                new boolean[]{true});
        c.style = ClickChannel.STYLE_AUTO;
        c.setBurst(Sound.DRAG_BURST);
        c.burstWindowMs = 350;
        c.endClick = false;
        for (int k = 0; k < 5; k++) c.click(0, k * ms(50)); // 250 ms of 20 CPS: a short burst
        check("auto: short fast clicking plays a burst, not the loop", !c.isLooping());
        for (int k = 5; k < 12; k++) c.click(0, k * ms(50)); // keeps going past the window
        check("auto: clicking past the burst window starts the loop", c.isLooping());
    }

    private static void cpuCost() {
        AudioEngine e = new AudioEngine(() -> fakeNanos);
        fakeNanos = 0;
        e.setSamples(SampleBank.load(tempDir(), true, true).sets);
        int blocks = AudioEngine.RATE * 60 / AudioEngine.BLOCK; // one minute of audio
        // warm up the JIT, then measure: 8 new clicks every 2.7 ms (~3000 clicks/s), far beyond real use
        for (int pass = 0; pass < 2; pass++) {
            long t0 = System.nanoTime();
            for (int b = 0; b < blocks; b++) {
                if (b % 4 == 0) for (int v = 0; v < 8; v++) e.play(Sound.BUTTERFLY, 0.05f, fakeNanos);
                e.renderBlock();
                fakeNanos += AudioEngine.BLOCK * 1_000_000_000L / AudioEngine.RATE;
            }
            double ms = (System.nanoTime() - t0) / 1e6;
            if (pass == 1) {
                System.out.printf("  info: 60 s of heavy mixing rendered in %.0f ms (%.2f%% of one core)%n", ms, ms / 600.0);
                check("mixing stays well under 5% of a core", ms < 3000);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static AudioEngine engineWithImpulse(Sound... slots) {
        AudioEngine e = new AudioEngine(() -> fakeNanos);
        fakeNanos = 0;
        float[] impulse = new float[64];
        impulse[0] = 1f;
        SampleSet[] sets = new SampleSet[Sound.count()];
        Sound[] targets = slots.length == 0 ? new Sound[]{Sound.BUTTERFLY} : slots;
        for (Sound s : targets) sets[s.ordinal()] = new SampleSet(new Sample[]{new Sample(impulse, "impulse")}, false);
        e.setSamples(sets);
        e.setGainJitter(0);
        e.setPitchJitter(0);
        return e;
    }

    private static SampleSet[] only(Sound sound, float[] data) {
        SampleSet[] sets = new SampleSet[Sound.count()];
        sets[sound.ordinal()] = new SampleSet(new Sample[]{new Sample(data, "test")}, false);
        return sets;
    }

    /** Renders {@code frames} (rounded up to blocks), advancing the fake clock in step. */
    private static float[] render(AudioEngine e, int frames) {
        int blocks = (frames + AudioEngine.BLOCK - 1) / AudioEngine.BLOCK;
        float[] out = new float[blocks * AudioEngine.BLOCK];
        for (int b = 0; b < blocks; b++) {
            fakeNanos = e.frameClock() * 1_000_000_000L / AudioEngine.RATE;
            e.renderBlock();
            byte[] bytes = e.lastBlock();
            for (int i = 0; i < AudioEngine.BLOCK; i++) {
                short s = (short) ((bytes[i * 4] & 0xFF) | (bytes[i * 4 + 1] << 8));
                out[b * AudioEngine.BLOCK + i] = s / 32767f;
            }
        }
        return out;
    }

    private static List<Integer> onsets(float[] audio) {
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < audio.length; i++) if (audio[i] > 0.5f) out.add(i);
        return out;
    }

    private static long ms(double ms) {
        return (long) (ms * 1_000_000);
    }

    private static File tempDir() {
        try {
            return Files.createTempDirectory("sgcpu").toFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static File writeWav(File f, float[] mono, int rate, int channels, int format, int bits, boolean extensible) throws IOException {
        int bps = bits / 8, block = bps * channels, dataLen = mono.length * block;
        int fmtLen = extensible ? 40 : 16;
        ByteBuffer b = ByteBuffer.allocate(12 + 8 + fmtLen + 8 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(4 + 8 + fmtLen + 8 + dataLen).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(fmtLen).putShort((short) (extensible ? 0xFFFE : format)).putShort((short) channels)
                .putInt(rate).putInt(rate * block).putShort((short) block).putShort((short) bits);
        if (extensible) {
            b.putShort((short) 22).putShort((short) bits).putInt(0).putShort((short) format).put(new byte[14]);
        }
        b.put("data".getBytes()).putInt(dataLen);
        for (float v : mono) {
            for (int c = 0; c < channels; c++) {
                if (format == 3) {
                    b.putFloat(v);
                } else if (bits == 16) {
                    b.putShort((short) Math.round(v * 32767));
                } else {
                    int x = Math.round(v * 8388607);
                    b.put((byte) x).put((byte) (x >> 8)).put((byte) (x >> 16));
                }
            }
        }
        Files.write(f.toPath(), b.array());
        return f;
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) failures++;
    }
}
