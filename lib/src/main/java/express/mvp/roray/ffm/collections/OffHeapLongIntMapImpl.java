package express.mvp.roray.ffm.collections;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A linear-probing, open-addressing hash map with off-heap keys and off-heap
 * int values.
 *
 * <p>
 * Not thread-safe. Designed for single-threaded hot paths.
 */
public class OffHeapLongIntMapImpl implements OffHeapLongIntMap {

    private static final byte FREE = 0;
    private static final byte OCCUPIED = 1;
    private static final byte REMOVED = 2;

    private static final long FOUND_MASK = 1L << 63;

    private final Arena arena;
    private final MemorySegment keys;
    private final MemorySegment values;
    private final MemorySegment states;
    private final int capacity;
    private final int mask;
    private final boolean arenaOwned;
    private int size;

    private volatile boolean closed = false;

    /**
     * Creates a new off-heap long-to-int map with specified capacity.
     *
     * <p><b>Arena ownership:</b> This map owns its arena. Calling {@link #close()} closes the
     * arena and releases all off-heap storage.
     *
     * @param capacity the capacity (must be a power of 2)
     */
    public OffHeapLongIntMapImpl(int capacity) {
        if (Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("Capacity must be a power of 2");
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.arena = Arena.ofShared();
        this.arenaOwned = true;
        this.keys = arena.allocate(ValueLayout.JAVA_LONG, capacity);
        this.values = arena.allocate(ValueLayout.JAVA_INT, capacity);
        this.states = arena.allocate(ValueLayout.JAVA_BYTE, capacity);
        this.size = 0;
    }

    /**
     * Creates a new off-heap long-to-int map using a caller-provided {@link Arena}.
     *
     * <p><b>Arena ownership:</b> The map does <b>not</b> own the provided arena. Calling
     * {@link #close()} will release this map instance, but will <b>not</b> close the arena.
     * The caller remains responsible for closing the arena.
     *
     * @param capacity the capacity (must be a power of 2)
     * @param arena the arena to allocate the off-heap storage from (caller-owned)
     */
    public OffHeapLongIntMapImpl(int capacity, Arena arena) {
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
        this.values = arena.allocate(ValueLayout.JAVA_INT, capacity);
        this.states = arena.allocate(ValueLayout.JAVA_BYTE, capacity);
        this.size = 0;
    }

    private static long packFound(int value) {
        return FOUND_MASK | (value & 0xFFFF_FFFFL);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("OffHeapLongIntMapImpl is closed");
        }
    }

    @Override
    public long getPacked(long key) {
        ensureOpen();
        int index = hash(key) & mask;
        int start = index;
        do {
            byte state = states.getAtIndex(ValueLayout.JAVA_BYTE, index);
            if (state == FREE) {
                return 0L;
            }
            if (state == OCCUPIED) {
                long k = keys.getAtIndex(ValueLayout.JAVA_LONG, index);
                if (k == key) {
                    return packFound(values.getAtIndex(ValueLayout.JAVA_INT, index));
                }
            }
            index = (index + 1) & mask;
        } while (index != start);
        return 0L;
    }

    @Override
    public void put(long key, int value) {
        ensureOpen();
        int index = hash(key) & mask;
        int start = index;
        int firstRemoved = -1;

        do {
            byte state = states.getAtIndex(ValueLayout.JAVA_BYTE, index);
            if (state == FREE) {
                if (firstRemoved != -1) {
                    index = firstRemoved;
                }
                insertAt(index, key, value);
                return;
            }
            if (state == REMOVED) {
                if (firstRemoved == -1) {
                    firstRemoved = index;
                }
            } else if (state == OCCUPIED) {
                long k = keys.getAtIndex(ValueLayout.JAVA_LONG, index);
                if (k == key) {
                    values.setAtIndex(ValueLayout.JAVA_INT, index, value); // Update
                    return;
                }
            }
            index = (index + 1) & mask;
        } while (index != start);

        if (firstRemoved != -1) {
            insertAt(firstRemoved, key, value);
            return;
        }

        throw new IllegalStateException("Map is full (capacity=" + capacity + ")");
    }

    private void insertAt(int index, long key, int value) {
        keys.setAtIndex(ValueLayout.JAVA_LONG, index, key);
        values.setAtIndex(ValueLayout.JAVA_INT, index, value);
        states.setAtIndex(ValueLayout.JAVA_BYTE, index, OCCUPIED);
        size++;
    }

    @Override
    public long removePacked(long key) {
        ensureOpen();
        int index = hash(key) & mask;
        int start = index;
        do {
            byte state = states.getAtIndex(ValueLayout.JAVA_BYTE, index);
            if (state == FREE) {
                return 0L;
            }
            if (state == OCCUPIED) {
                long k = keys.getAtIndex(ValueLayout.JAVA_LONG, index);
                if (k == key) {
                    int val = values.getAtIndex(ValueLayout.JAVA_INT, index);
                    states.setAtIndex(ValueLayout.JAVA_BYTE, index, REMOVED);
                    size--;
                    return packFound(val);
                }
            }
            index = (index + 1) & mask;
        } while (index != start);
        return 0L;
    }

    @Override
    public void clear() {
        ensureOpen();
        states.fill(FREE);
        size = 0;
    }

    @Override
    public int size() {
        ensureOpen();
        return size;
    }

    @Override
    public boolean isEmpty() {
        ensureOpen();
        return size == 0;
    }

    @Override
    public boolean containsKey(long key) {
        ensureOpen();
        int index = hash(key) & mask;
        int start = index;
        do {
            byte state = states.getAtIndex(ValueLayout.JAVA_BYTE, index);
            if (state == FREE) {
                return false;
            }
            if (state == OCCUPIED) {
                long k = keys.getAtIndex(ValueLayout.JAVA_LONG, index);
                if (k == key) {
                    return true;
                }
            }
            index = (index + 1) & mask;
        } while (index != start);
        return false;
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

    private static int hash(long key) {
        int h = (int) (key ^ (key >>> 32));
        return h ^ (h >>> 16);
    }
}
