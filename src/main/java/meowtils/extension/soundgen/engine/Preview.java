package meowtils.extension.soundgen.engine;

import java.util.Random;

/**
 * Renders a slot through the real engine offline (no audio device, no game) so a set of
 * variations can be auditioned as it will actually be played: same variation order,
 * same gain/pitch variation, same mixing.
 */
public final class Preview {
    private Preview() {
    }

    /**
     * @param cps          hits per second (ignored for loop slots)
     * @param jitterMs     random timing offset per hit, like the in-game click jitter
     * @return mono audio at {@link AudioEngine#RATE}
     */
    public static float[] render(SampleSet[] sets, Sound sound, double cps, double seconds, double jitterMs,
                                 float gainJitter, float pitchJitter, int selection) {
        final long[] now = {0};
        AudioEngine engine = new AudioEngine(() -> now[0]);
        engine.setSamples(sets);
        engine.setGainJitter(gainJitter);
        engine.setPitchJitter(pitchJitter);
        engine.setSelection(selection);

        long end = (long) (seconds * 1e9);
        long step = (long) (1e9 / Math.max(0.1, cps));
        Random random = new Random(1);
        long nextHit = 0;
        if (sound.loop) {
            engine.loopStart(sound, 1, 1f, 0);
            engine.loopStop(1, 12, end);
        }

        int blocks = (int) ((seconds + 0.5) * AudioEngine.RATE / AudioEngine.BLOCK);
        float[] out = new float[blocks * AudioEngine.BLOCK];
        for (int b = 0; b < blocks; b++) {
            now[0] = engine.frameClock() * 1_000_000_000L / AudioEngine.RATE;
            // Feed hits a little ahead of the render position, as the game would.
            while (!sound.loop && nextHit < end && nextHit < now[0] + 100_000_000L) {
                engine.play(sound, 1f, nextHit + (long) (random.nextDouble() * jitterMs * 1e6));
                nextHit += step;
            }
            engine.renderBlock();
            byte[] bytes = engine.lastBlock();
            for (int i = 0; i < AudioEngine.BLOCK; i++) {
                out[b * AudioEngine.BLOCK + i] = (short) ((bytes[i * 4] & 0xFF) | (bytes[i * 4 + 1] << 8)) / 32768f;
            }
        }
        return out;
    }
}
