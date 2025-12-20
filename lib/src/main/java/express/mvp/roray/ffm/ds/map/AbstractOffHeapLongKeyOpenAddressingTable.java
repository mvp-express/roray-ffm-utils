package express.mvp.roray.ffm.ds.map;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

abstract class AbstractOffHeapLongKeyOpenAddressingTable implements AutoCloseable {

    protected static final byte FREE = 0;
    protected static final byte OCCUPIED = 1;
    protected static final byte REMOVED = 2;

    protected final Arena arena;
    protected final boolean arenaOwned;
    protected final MemorySegment keys;
    protected final MemorySegment states;
    protected final int capacity;
    protected final int mask;

    protected int size;
    protected volatile boolean closed;

    protected AbstractOffHeapLongKeyOpenAddressingTable(int capacity) {
        if (Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("Capacity must be a power of 2");
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.arena = Arena.ofShared();
        this.arenaOwned = true;
        this.keys = arena.allocate(ValueLayout.JAVA_LONG, capacity);
        this.states = arena.allocate(ValueLayout.JAVA_BYTE, capacity);
        this.size = 0;
        this.closed = false;
    }

    protected AbstractOffHeapLongKeyOpenAddressingTable(int capacity, Arena arena) {
        if (Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("Capacity must be a power of 2");
        }
        if (arena == null) {
            throw new IllegalArgumentException("Arena cannot be null");
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.arena = arena;
        this.arenaOwned = false;
        this.keys = arena.allocate(ValueLayout.JAVA_LONG, capacity);
        this.states = arena.allocate(ValueLayout.JAVA_BYTE, capacity);
        this.size = 0;
        this.closed = false;
    }

    protected final void ensureOpen() {
        if (closed) {
            throw new IllegalStateException(getClass().getSimpleName() + " is closed");
        }
    }

    protected final int findIndex(long key) {
        int index = hash(key) & mask;
        int start = index;
        do {
            byte state = states.getAtIndex(ValueLayout.JAVA_BYTE, index);
            if (state == FREE) {
                return -1;
            }
            if (state == OCCUPIED) {
                long k = keys.getAtIndex(ValueLayout.JAVA_LONG, index);
                if (k == key) {
                    return index;
                }
            }
            index = (index + 1) & mask;
        } while (index != start);
        return -1;
    }

    /**
     * Returns a packed long where the sign bit indicates whether the key already
     * exists.
     *
     * <p>
     * If found, {@code result < 0} and {@code (int) result} is the existing index.
     *
     * <p>
     * If not found, {@code result >= 0} and {@code (int) result} is an insertion
     * slot.
     *
     * @throws IllegalStateException if the table is full
     */
    protected final long probeForPut(long key) {
        int index = hash(key) & mask;
        int start = index;
        int firstRemoved = -1;

        do {
            byte state = states.getAtIndex(ValueLayout.JAVA_BYTE, index);
            if (state == FREE) {
                if (firstRemoved != -1) {
                    index = firstRemoved;
                }
                return packIndexResult(false, index);
            }
            if (state == REMOVED) {
                if (firstRemoved == -1) {
                    firstRemoved = index;
                }
            } else if (state == OCCUPIED) {
                long k = keys.getAtIndex(ValueLayout.JAVA_LONG, index);
                if (k == key) {
                    return packIndexResult(true, index);
                }
            }
            index = (index + 1) & mask;
        } while (index != start);

        if (firstRemoved != -1) {
            return packIndexResult(false, firstRemoved);
        }

        throw new IllegalStateException("Map is full (capacity=" + capacity + ")");
    }

    protected final void clearStates() {
        states.fill(FREE);
        size = 0;
    }

    protected static int hash(long key) {
        int h = (int) (key ^ (key >>> 32));
        return h ^ (h >>> 16);
    }

    private static long packIndexResult(boolean found, int index) {
        long low = index & 0xFFFF_FFFFL;
        return found ? (Long.MIN_VALUE | low) : low;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (arenaOwned) {
            arena.close();
        }
    }
}
