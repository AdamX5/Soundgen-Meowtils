package meowtils.extension.soundgen.engine;

/**
 * Bounded queue from game threads to the audio thread, stored as parallel primitive
 * arrays so posting a sound allocates nothing.
 *
 * Producers serialise on the monitor (uncontended in practice: almost everything comes from
 * the client thread). The single consumer never locks: it reads {@code published} and
 * {@code head} as volatiles, which gives it a happens-before edge over the slot writes.
 * A full queue drops the command rather than ever blocking the game.
 */
final class CommandQueue {
    static final int PLAY = 1;
    static final int LOOP_START = 2;
    static final int LOOP_STOP = 3;
    static final int STOP_ALL = 4;

    final int capacity;
    private final int mask;
    final int[] type;
    final int[] arg;
    final int[] tag;
    final float[] gain;
    final long[] time;

    private long tail;               // producer cursor, guarded by this
    private volatile long published; // last slot visible to the consumer
    private volatile long head;      // consumer cursor
    private volatile int dropped;

    CommandQueue(int capacityPowerOfTwo) {
        if (Integer.bitCount(capacityPowerOfTwo) != 1) throw new IllegalArgumentException("capacity must be a power of two");
        capacity = capacityPowerOfTwo;
        mask = capacityPowerOfTwo - 1;
        type = new int[capacity];
        arg = new int[capacity];
        tag = new int[capacity];
        gain = new float[capacity];
        time = new long[capacity];
    }

    synchronized boolean offer(int type, int arg, int tag, float gain, long timeNanos) {
        long t = tail;
        if (t - head >= capacity) {
            dropped++;
            return false;
        }
        int slot = (int) (t & mask);
        this.type[slot] = type;
        this.arg[slot] = arg;
        this.tag[slot] = tag;
        this.gain[slot] = gain;
        this.time[slot] = timeNanos;
        tail = t + 1;
        published = t + 1;
        return true;
    }

    /** Consumer only. Returns the slot index of the next command, or -1 when empty. */
    int peek() {
        long h = head;
        return h < published ? (int) (h & mask) : -1;
    }

    /** Consumer only. Releases the slot returned by the last {@link #peek()}. */
    void advance() {
        head = head + 1;
    }

    /** Consumer side; only call while no consumer thread is running. Discards stale commands. */
    void clear() {
        head = published;
    }

    int dropped() {
        return dropped;
    }
}
