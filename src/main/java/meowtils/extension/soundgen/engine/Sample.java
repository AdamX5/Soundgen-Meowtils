package meowtils.extension.soundgen.engine;

/** One decoded variation: mono float PCM at {@link AudioEngine#RATE}. Immutable after construction. */
public final class Sample {
    final float[] data;
    final String name;

    Sample(float[] data, String name) {
        this.data = data;
        this.name = name;
    }

    public int frames() {
        return data.length;
    }

    public String name() {
        return name;
    }
}
