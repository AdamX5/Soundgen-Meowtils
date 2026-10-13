package meowtils.extension.soundgen.engine;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/**
 * Minimal RIFF/WAVE reader producing mono float PCM.
 *
 * Written by hand instead of using javax.sound's AudioSystem because Java 8's reader
 * rejects 32-bit float and WAVE_FORMAT_EXTENSIBLE files, which is what most DAWs and
 * recorders (Audacity, OBS, Reaper) export by default.
 *
 * Supports integer PCM at 8/16/24/32 bits and IEEE float at 32/64 bits, any channel
 * count (channels are averaged to mono).
 */
final class WavDecoder {
    private static final int FORMAT_PCM = 1;
    private static final int FORMAT_FLOAT = 3;
    private static final int FORMAT_EXTENSIBLE = 0xFFFE;

    static final class Decoded {
        final float[] mono;
        final int rate;

        Decoded(float[] mono, int rate) {
            this.mono = mono;
            this.rate = rate;
        }
    }

    private WavDecoder() {
    }

    static Decoded decode(File file) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(file.toPath())).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.remaining() < 12 || buf.getInt(0) != 0x46464952 /* RIFF */ || buf.getInt(8) != 0x45564157 /* WAVE */) {
            throw new IOException("not a RIFF/WAVE file");
        }
        int format = -1, channels = 0, rate = 0, bits = 0, blockAlign = 0;
        int dataOffset = -1, dataLength = 0;
        int pos = 12;
        while (pos + 8 <= buf.limit()) {
            int id = buf.getInt(pos);
            long size = buf.getInt(pos + 4) & 0xFFFFFFFFL;
            int body = pos + 8;
            if (id == 0x20746D66 /* "fmt " */) {
                format = buf.getShort(body) & 0xFFFF;
                channels = buf.getShort(body + 2) & 0xFFFF;
                rate = buf.getInt(body + 4);
                blockAlign = buf.getShort(body + 12) & 0xFFFF;
                bits = buf.getShort(body + 14) & 0xFFFF;
                if (format == FORMAT_EXTENSIBLE && size >= 26) {
                    format = buf.getShort(body + 24) & 0xFFFF; // first two bytes of the sub-format GUID
                }
            } else if (id == 0x61746164 /* "data" */) {
                dataOffset = body;
                // Some recorders write 0 or 0xFFFFFFFF while still recording; clamp to the file.
                dataLength = (int) Math.min(size, buf.limit() - body);
                break;
            }
            pos = (int) Math.min(Integer.MAX_VALUE, body + size + (size & 1)); // chunks are word aligned
        }
        if (format < 0 || dataOffset < 0) throw new IOException("missing fmt or data chunk");
        if (channels < 1 || rate < 1000 || blockAlign < 1) throw new IOException("invalid fmt chunk");
        if (format != FORMAT_PCM && format != FORMAT_FLOAT) throw new IOException("unsupported encoding " + format);

        int bytesPerSample = blockAlign / channels;
        int frames = dataLength / blockAlign;
        float[] mono = new float[frames];
        float scale = 1f / channels;
        for (int f = 0; f < frames; f++) {
            int base = dataOffset + f * blockAlign;
            float sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += read(buf, base + c * bytesPerSample, format, bits);
            }
            mono[f] = sum * scale;
        }
        return new Decoded(mono, rate);
    }

    private static float read(ByteBuffer buf, int at, int format, int bits) throws IOException {
        if (format == FORMAT_FLOAT) {
            if (bits == 32) return buf.getFloat(at);
            if (bits == 64) return (float) buf.getDouble(at);
            throw new IOException("unsupported float width " + bits);
        }
        switch (bits) {
            case 8:
                return ((buf.get(at) & 0xFF) - 128) / 128f; // 8-bit WAV is unsigned
            case 16:
                return buf.getShort(at) / 32768f;
            case 24:
                int v = (buf.get(at) & 0xFF) | ((buf.get(at + 1) & 0xFF) << 8) | (buf.get(at + 2) << 16);
                return v / 8388608f;
            case 32:
                return buf.getInt(at) / 2147483648f;
            default:
                throw new IOException("unsupported PCM width " + bits);
        }
    }
}
