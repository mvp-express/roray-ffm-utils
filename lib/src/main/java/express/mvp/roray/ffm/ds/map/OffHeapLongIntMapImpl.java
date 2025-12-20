package express.mvp.roray.ffm.ds.map;

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
public class OffHeapLongIntMapImpl extends AbstractOffHeapLongKeyOpenAddressingTable
        implements OffHeapLongIntMap {

    private static final long FOUND_MASK = 1L << 63;

    private final MemorySegment values;

    /**
     * Creates a new off-heap long-to-int map with specified capacity.
     *
     * <p>
     * <b>Arena ownership:</b> This map owns its arena. Calling {@link #close()}
     * closes the
     * arena and releases all off-heap storage.
     *
     * @param capacity the capacity (must be a power of 2)
     */
    public OffHeapLongIntMapImpl(int capacity) {
        super(capacity);
        this.values = arena.allocate(ValueLayout.JAVA_INT, capacity);
    }

    /**
     * Creates a new off-heap long-to-int map using a caller-provided {@link Arena}.
     *
     * <p>
     * <b>Arena ownership:</b> The map does <b>not</b> own the provided arena.
     * Calling
     * {@link #close()} will release this map instance, but will <b>not</b> close
     * the arena.
     * The caller remains responsible for closing the arena.
     *
     * @param capacity the capacity (must be a power of 2)
     * @param arena    the arena to allocate the off-heap storage from
     *                 (caller-owned)
     */
    public OffHeapLongIntMapImpl(int capacity, Arena arena) {
        super(capacity, arena);
        this.values = this.arena.allocate(ValueLayout.JAVA_INT, capacity);
    }

    private static long packFound(int value) {
        return FOUND_MASK | (value & 0xFFFF_FFFFL);
    }

    @Override
    public long getPacked(long key) {
        ensureOpen();
        int index = findIndex(key);
        if (index == -1) {
            return 0L;
        }
        return packFound(values.getAtIndex(ValueLayout.JAVA_INT, index));
    }

    @Override
    public void put(long key, int value) {
        ensureOpen();
        long result = probeForPut(key);
        int index = (int) result;
        if (result < 0) {
            values.setAtIndex(ValueLayout.JAVA_INT, index, value);
            return;
        }
        keys.setAtIndex(ValueLayout.JAVA_LONG, index, key);
        values.setAtIndex(ValueLayout.JAVA_INT, index, value);
        states.setAtIndex(ValueLayout.JAVA_BYTE, index, OCCUPIED);
        size++;
    }

    @Override
    public long removePacked(long key) {
        ensureOpen();
        int index = findIndex(key);
        if (index == -1) {
            return 0L;
        }
        int val = values.getAtIndex(ValueLayout.JAVA_INT, index);
        states.setAtIndex(ValueLayout.JAVA_BYTE, index, REMOVED);
        size--;
        return packFound(val);
    }

    @Override
    public void clear() {
        ensureOpen();
        clearStates();
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
        return findIndex(key) != -1;
    }

    @Override
    public void close() {
        super.close();
    }
}
