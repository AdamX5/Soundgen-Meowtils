package meowtils.extension.soundgen.tools;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/** Writes mono 24-bit PCM WAV: lossless enough for clicks and readable by every editor. */
final class WavWriter {
    private WavWriter() {
    }

    static void write24(File file, float[] mono, int rate) throws IOException {
        int dataLen = mono.length * 3;
        ByteBuffer b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataLen).put(new byte[]{'W', 'A', 'V', 'E'});
        b.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 3).putShort((short) 3).putShort((short) 24);
        b.put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataLen);
        for (float v : mono) {
            int x = (int) Math.round(Math.max(-1.0, Math.min(1.0, v)) * 8388607.0);
            b.put((byte) x).put((byte) (x >> 8)).put((byte) (x >> 16));
        }
        Files.write(file.toPath(), b.array());
    }
}
