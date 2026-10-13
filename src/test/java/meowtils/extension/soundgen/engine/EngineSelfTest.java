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
 *   javac -d out $(find src/main/java/meowtils/extension/soundgen/engine src/main/java/meowtils/extension/soundgen/input src/test/java -name '*.java')
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
        check("onset within 0.5 ms of start after trim (got " + onset + " frames)", onset <= 25);
        check("tail trimmed", out.length < 1000);
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
        ClickChannel channel = new ClickChannel(e, timing, Sound.BUTTERFLY, Sound.DRAG_LOOP, Sound.DRAG_END, 1,
                new boolean[]{true, false});
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
        KeyChannel k = new KeyChannel(e, timing, Sound.KEY_DOWN, Sound.KEY_UP, 2);
        k.minHoldMs = 30;
        k.state(0, true, 0);
        k.state(0, false, ms(5)); // 5 ms tap must still release 30 ms after the press
        List<Integer> onsets = onsets(render(e, 4800));
        check("press at 0, release at 30 ms, got " + onsets, onsets.size() == 2 && onsets.get(0) == 0 && onsets.get(1) == 1440);
    }

    private static void placeholdersCoverEverySlot() throws IOException {
        File dir = Files.createTempDirectory("sgbank").toFile();
        SampleBank.Result r = SampleBank.load(dir, true);
        boolean all = true;
        for (SampleSet s : r.sets) all &= s != null && s.size() > 0;
        check("every slot has sound with no recordings", all && r.recorded == 0 && r.warnings.isEmpty());
        check("folders and README created", new File(dir, "drag_loop").isDirectory() && new File(dir, "README.txt").isFile());
    }

    private static void cpuCost() {
        AudioEngine e = new AudioEngine(() -> fakeNanos);
        fakeNanos = 0;
        e.setSamples(SampleBank.load(tempDir(), true).sets);
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
