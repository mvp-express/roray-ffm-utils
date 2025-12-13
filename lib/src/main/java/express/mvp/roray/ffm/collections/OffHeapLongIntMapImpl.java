package express.mvp.roray.ffm.collections;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A linear-probing, open-addressing hash map with off-heap keys and off-heap int values.
 *
 * <p>Not thread-safe. Designed for single-threaded hot paths.
 */
public class OffHeapLongIntMapImpl implements OffHeapLongIntMap {

    private static final byte FREE = 0;
    private static final byte OCCUPIED = 1;
    private static final byte REMOVED = 2;

    private final Arena arena;
    private final MemorySegment keys;
    private final MemorySegment values;
    private final MemorySegment states;
    private final int capacity;
    private final int mask;
    private final int missingValue;
    private int size;

    /**
     * Creates a new off-heap long-to-int map with specified capacity and missing value.
     *
     * @param capacity the capacity (must be a power of 2)
     * @param missingValue the value returned for missing keys
     */
    public OffHeapLongIntMapImpl(int capacity, int missingValue) {
        if (Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("Capacity must be a power of 2");
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.missingValue = missingValue;
        this.arena = Arena.ofShared();
        this.keys = arena.allocate(ValueLayout.JAVA_LONG, capacity);
        this.values = arena.allocate(ValueLayout.JAVA_INT, capacity);
        this.states = arena.allocate(ValueLayout.JAVA_BYTE, capacity);
        this.size = 0;
    }

    public OffHeapLongIntMapImpl(int capacity) {
        this(capacity, -1);
    }

    @Override
    public int get(long key) {
        int index = hash(key) & mask;
        int start = index;
        do {
            byte state = states.getAtIndex(ValueLayout.JAVA_BYTE, index);
            if (state == FREE) {
                return missingValue;
            }
            if (state == OCCUPIED) {
                long k = keys.getAtIndex(ValueLayout.JAVA_LONG, index);
                if (k == key) {
                    return values.getAtIndex(ValueLayout.JAVA_INT, index);
                }
            }
            index = (index + 1) & mask;
        } while (index != start);
        return missingValue;
    }

    @Override
    public void put(long key, int value) {
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

        throw new IllegalStateException("Map is full");
    }

    private void insertAt(int index, long key, int value) {
        keys.setAtIndex(ValueLayout.JAVA_LONG, index, key);
        values.setAtIndex(ValueLayout.JAVA_INT, index, value);
        states.setAtIndex(ValueLayout.JAVA_BYTE, index, OCCUPIED);
        size++;
    }

    @Override
    public int remove(long key) {
        int index = hash(key) & mask;
        int start = index;
        do {
            byte state = states.getAtIndex(ValueLayout.JAVA_BYTE, index);
            if (state == FREE) {
                return missingValue;
            }
            if (state == OCCUPIED) {
                long k = keys.getAtIndex(ValueLayout.JAVA_LONG, index);
                if (k == key) {
                    int val = values.getAtIndex(ValueLayout.JAVA_INT, index);
                    states.setAtIndex(ValueLayout.JAVA_BYTE, index, REMOVED);
                    size--;
                    return val;
                }
            }
            index = (index + 1) & mask;
        } while (index != start);
        return missingValue;
    }

    @Override
    public void clear() {
        states.fill(FREE);
        size = 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsKey(long key) {
        return get(key) != missingValue;
    }

    @Override
    public void close() {
        arena.close();
    }

    private static int hash(long key) {
        int h = (int) (key ^ (key >>> 32));
        return h ^ (h >>> 16);
    }
}
