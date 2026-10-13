package meowtils.extension.soundgen.engine;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;

/**
 * Software mixer running on its own thread and writing to one output device.
 *
 * Design for low latency and near-zero game-side cost:
 * - The game thread only writes a few primitives into {@link CommandQueue}; no allocation,
 *   no locks shared with the audio thread, no audio work.
 * - All samples are pre-decoded in memory; mixing is a tight loop over a fixed voice pool.
 * - The output line's buffer is the only latency (configurable). The thread spends nearly
 *   all its time blocked in {@code line.write}, which is what paces it.
 * - Commands carry a {@link System#nanoTime()} target, converted to an exact frame inside
 *   the next block, so scheduling resolution is one frame (~0.02 ms) rather than one block.
 *
 * Output is 48 kHz, 16-bit, stereo (mono duplicated). Stereo because virtual cables
 * (VB-Audio, Voicemeeter) expose stereo endpoints and some refuse mono lines.
 */
public final class AudioEngine {
    public static final int RATE = 48000;
    static final int BLOCK = 128; // 2.67 ms per mix iteration
    static final int MAX_VOICES = 48;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 2, true, false);
    private static final int BYTES_PER_FRAME = 4;
    private static final int LOOP_FADE_IN_FRAMES = RATE * 4 / 1000;

    /** Source of "now" in nanoseconds. Swappable so offline tests are deterministic. */
    interface Clock {
        long nanoTime();
    }

    private final CommandQueue queue = new CommandQueue(1024);
    private final Voice[] voices = new Voice[MAX_VOICES];
    private final float[] mix = new float[BLOCK];
    private final byte[] out = new byte[BLOCK * BYTES_PER_FRAME];
    private final Clock clock;

    private volatile SampleSet[] sets = new SampleSet[0];
    private volatile float masterGain = 1f;
    private volatile float gainJitter = 0.1f;
    private volatile float pitchJitter = 0.03f;
    private volatile int selection = SampleSet.SHUFFLE;
    private volatile int chokeFadeFrames = RATE * 15 / 1000;

    // Audio-thread state
    private long frameClock;
    private final Rng rng = new Rng(System.nanoTime());
    private volatile int activeVoices;
    private volatile int stolenVoices;
    private volatile boolean clipped;

    // Lifecycle (guarded by this)
    private Thread thread;
    private volatile boolean running;
    private volatile String deviceName = "none";
    private volatile int latencyMs;

    public AudioEngine() {
        this(System::nanoTime);
    }

    AudioEngine(Clock clock) {
        this.clock = clock;
        for (int i = 0; i < MAX_VOICES; i++) voices[i] = new Voice();
    }

    // ------------------------------------------------------------------ game-side API

    public void play(Sound sound, float gain, long atNanos) {
        play(sound, gain, atNanos, 0);
    }

    /**
     * Plays a one-shot in a choke group (0 = none). A new hit in a group fades out the tail of
     * the earlier hits in the same group over {@link #setChokeFadeMs}, like a real switch whose
     * next click stops the previous one ringing. Keeps very fast clicking crisp instead of smeared.
     */
    public void play(Sound sound, float gain, long atNanos, int chokeGroup) {
        if (gain > 0) queue.offer(CommandQueue.PLAY, sound.ordinal(), chokeGroup, gain, atNanos);
    }

    /** Starts a looping voice identified by {@code tag}; ignored if that tag is already looping. */
    public void loopStart(Sound sound, int tag, float gain, long atNanos) {
        queue.offer(CommandQueue.LOOP_START, sound.ordinal(), tag, gain, atNanos);
    }

    /** Fades out every loop with {@code tag} over {@code fadeMs}, starting at {@code atNanos}. */
    public void loopStop(int tag, int fadeMs, long atNanos) {
        queue.offer(CommandQueue.LOOP_STOP, fadeMs, tag, 0, atNanos);
    }

    public void stopAll() {
        queue.offer(CommandQueue.STOP_ALL, 0, 0, 0, 0);
    }

    public void setSamples(SampleSet[] sets) {
        this.sets = sets;
    }

    public void setMasterGain(float gain) {
        masterGain = gain;
    }

    /** Random per-hit gain variation, 0..1 (0.1 = ±10 %). */
    public void setGainJitter(float jitter) {
        gainJitter = jitter;
    }

    /** Random per-hit playback-rate variation, 0..1 (0.03 = ±3 %). */
    public void setPitchJitter(float jitter) {
        pitchJitter = jitter;
    }

    /** {@link SampleSet#SHUFFLE} or {@link SampleSet#RANDOM}. */
    public void setChokeFadeMs(float ms) {
        chokeFadeFrames = Math.max(1, (int) (ms * RATE / 1000f));
    }

    public void setSelection(int mode) {
        selection = mode;
    }

    public boolean isRunning() {
        return running;
    }

    public String deviceName() {
        return deviceName;
    }

    public int latencyMs() {
        return latencyMs;
    }

    public int activeVoices() {
        return activeVoices;
    }

    public int stolenVoices() {
        return stolenVoices;
    }

    public int droppedCommands() {
        return queue.dropped();
    }

    /** True if any output was clipped since the last call. */
    public boolean consumeClipped() {
        boolean c = clipped;
        clipped = false;
        return c;
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Opens the output and starts the mixer thread. {@code deviceHint} is matched
     * case-insensitively against device names ("CABLE Input", "Voicemeeter"); blank or
     * unmatched uses the system default. Returns a status line for the user.
     */
    public synchronized String start(String deviceHint, int bufferMs) {
        stop();
        int bufferFrames = Math.max(BLOCK * 2, RATE * Math.max(1, bufferMs) / 1000);
        SourceDataLine line;
        String status;
        Mixer.Info chosen = findDevice(deviceHint);
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
            line = chosen != null
                    ? (SourceDataLine) AudioSystem.getMixer(chosen).getLine(info)
                    : (SourceDataLine) AudioSystem.getLine(info);
            line.open(FORMAT, bufferFrames * BYTES_PER_FRAME);
            status = chosen != null ? "output: " + chosen.getName()
                    : (isBlank(deviceHint) ? "output: system default" : "no device matching '" + deviceHint + "', using system default");
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            return "audio failed to open: " + e.getMessage();
        }
        line.start();
        deviceName = chosen != null ? chosen.getName() : "system default";
        latencyMs = line.getBufferSize() / BYTES_PER_FRAME * 1000 / RATE;

        queue.clear(); // anything posted while stopped is stale
        final SourceDataLine target = line;
        running = true;
        thread = new Thread(() -> runLoop(target), "SoundGen-Audio");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
        return status + " (" + latencyMs + " ms buffer)";
    }

    public synchronized void stop() {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        for (Voice v : voices) v.active = false;
        activeVoices = 0;
    }

    private void runLoop(SourceDataLine line) {
        try {
            while (running) {
                renderBlock();
                line.write(out, 0, out.length); // blocks until the device has room: this paces the thread
            }
        } finally {
            line.stop();
            line.flush();
            line.close();
        }
    }

    /** Output devices that accept our format, for the device picker. */
    public static List<String> listDevices() {
        List<String> names = new ArrayList<String>();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
        for (Mixer.Info m : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(m).isLineSupported(info)) names.add(m.getName());
            } catch (RuntimeException ignored) {
                // a broken driver must not break the listing
            }
        }
        return names;
    }

    private static Mixer.Info findDevice(String hint) {
        if (isBlank(hint)) return null;
        String needle = hint.trim().toLowerCase();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
        for (Mixer.Info m : AudioSystem.getMixerInfo()) {
            if (!m.getName().toLowerCase().contains(needle)) continue;
            try {
                if (AudioSystem.getMixer(m).isLineSupported(info)) return m;
            } catch (RuntimeException ignored) {
                // keep looking
            }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ------------------------------------------------------------------ audio thread

    /** Mixes one block into {@link #out}. Package-private so tests can render offline. */
    void renderBlock() {
        drainCommands();
        float[] mix = this.mix;
        java.util.Arrays.fill(mix, 0f);
        long blockStart = frameClock;
        long blockEnd = blockStart + BLOCK;
        int active = 0;

        for (Voice v : voices) {
            if (!v.active) continue;
            active++;
            if (v.start >= blockEnd) continue; // scheduled for a later block
            int i = (int) Math.max(0, v.start - blockStart);
            float[] d = v.data;
            int len = d.length;
            double pos = v.pos;
            double inc = v.inc;
            float gain = v.gain;
            for (; i < BLOCK; i++) {
                int ip = (int) pos;
                if (ip >= len) {
                    if (!v.loop) {
                        v.active = false;
                        break;
                    }
                    pos -= len;
                    ip = (int) pos;
                }
                int next = ip + 1;
                if (next >= len) next = v.loop ? 0 : ip;
                float frac = (float) (pos - ip);
                float s = d[ip] + (d[next] - d[ip]) * frac;

                float g = gain;
                if (v.fadeInLeft > 0) {
                    g *= 1f - v.fadeInLeft / (float) LOOP_FADE_IN_FRAMES;
                    v.fadeInLeft--;
                }
                if (blockStart + i >= v.fadeStart) {
                    if (v.fadeLeft <= 0) {
                        v.active = false;
                        break;
                    }
                    g *= v.fadeLeft / (float) v.fadeTotal;
                    v.fadeLeft--;
                }
                mix[i] += s * g;
                pos += inc;
            }
            v.pos = pos;
        }
        activeVoices = active;

        float master = masterGain;
        byte[] out = this.out;
        boolean clip = false;
        for (int i = 0, o = 0; i < BLOCK; i++, o += BYTES_PER_FRAME) {
            float x = mix[i] * master;
            if (x > 1f) {
                x = 1f;
                clip = true;
            } else if (x < -1f) {
                x = -1f;
                clip = true;
            }
            int s = (int) (x * 32767f);
            byte lo = (byte) s;
            byte hi = (byte) (s >> 8);
            out[o] = lo;
            out[o + 1] = hi;
            out[o + 2] = lo;
            out[o + 3] = hi;
        }
        if (clip) clipped = true;
        frameClock = blockEnd;
    }

    byte[] lastBlock() {
        return out;
    }

    long frameClock() {
        return frameClock;
    }

    private void drainCommands() {
        CommandQueue q = queue;
        long now = clock.nanoTime();
        int slot;
        while ((slot = q.peek()) >= 0) {
            long start = frameClock + Math.max(0L, (q.time[slot] - now) * RATE / 1_000_000_000L);
            switch (q.type[slot]) {
                case CommandQueue.PLAY:
                    startVoice(q.arg[slot], q.tag[slot], q.gain[slot], start, false);
                    break;
                case CommandQueue.LOOP_START:
                    if (!loopActive(q.tag[slot])) startVoice(q.arg[slot], q.tag[slot], q.gain[slot], start, true);
                    break;
                case CommandQueue.LOOP_STOP:
                    fadeLoops(q.tag[slot], Math.max(1, q.arg[slot] * RATE / 1000), start);
                    break;
                case CommandQueue.STOP_ALL:
                    for (Voice v : voices) v.active = false;
                    break;
                default:
                    break;
            }
            q.advance();
        }
    }

    private void startVoice(int soundId, int tag, float gain, long start, boolean loop) {
        SampleSet[] sets = this.sets;
        if (soundId >= sets.length || sets[soundId] == null) return;
        Sample sample = sets[soundId].pick(rng, selection);
        if (sample.data.length < 2) return;

        Voice v = allocate();
        v.data = sample.data;
        v.start = start;
        v.loop = loop;
        v.tag = tag;
        v.fadeStart = Long.MAX_VALUE;
        v.fadeLeft = 0;
        v.fadeTotal = 1;
        if (loop) {
            // Start somewhere random in the take so repeated holds don't all begin identically.
            v.pos = rng.nextInt(sample.data.length);
            v.inc = 1.0;
            v.gain = gain;
            v.fadeInLeft = LOOP_FADE_IN_FRAMES;
        } else {
            v.pos = 0;
            v.inc = 1.0 + pitchJitter * rng.signedUnit();
            v.gain = gain * (1f + gainJitter * (float) rng.signedUnit());
            v.fadeInLeft = 0;
        }
        v.active = true;
        if (!loop && tag != 0) choke(v);
    }

    /** Earlier hits in {@code v}'s group fade out where {@code v} starts (and vice versa if v is earlier). */
    private void choke(Voice v) {
        for (Voice o : voices) {
            if (o == v || !o.active || o.loop || o.tag != v.tag) continue;
            if (o.start <= v.start) {
                fadeAt(o, v.start);
            } else {
                fadeAt(v, o.start);
            }
        }
    }

    private void fadeAt(Voice v, long frame) {
        long at = Math.max(frame, v.start);
        if (at < v.fadeStart) {
            v.fadeStart = at;
            v.fadeTotal = chokeFadeFrames;
            v.fadeLeft = chokeFadeFrames;
        }
    }

    private Voice allocate() {
        Voice oldest = null;
        for (Voice v : voices) {
            if (!v.active) return v;
            // Prefer stealing one-shots; a loop disappearing is far more audible.
            if (oldest == null || (oldest.loop && !v.loop) || (oldest.loop == v.loop && v.start < oldest.start)) {
                oldest = v;
            }
        }
        stolenVoices++;
        return oldest;
    }

    private boolean loopActive(int tag) {
        for (Voice v : voices) {
            if (v.active && v.loop && v.tag == tag && v.fadeStart == Long.MAX_VALUE) return true;
        }
        return false;
    }

    private void fadeLoops(int tag, int fadeFrames, long at) {
        for (Voice v : voices) {
            if (v.active && v.loop && v.tag == tag && v.fadeStart == Long.MAX_VALUE) {
                v.fadeStart = Math.max(at, v.start);
                v.fadeTotal = fadeFrames;
                v.fadeLeft = fadeFrames;
            }
        }
    }

    private static final class Voice {
        boolean active;
        float[] data;
        double pos;
        double inc;
        float gain;
        long start;
        boolean loop;
        int tag;
        int fadeInLeft;
        long fadeStart;
        int fadeLeft;
        int fadeTotal;
    }
}
