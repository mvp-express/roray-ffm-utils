package express.mvp.roray.utils.collections;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A linear-probing, open-addressing hash map with off-heap keys and on-heap values.
 *
 * <p>Not thread-safe. Designed for single-threaded hot paths.
 */
public class OffHeapLongObjectMapImpl<V> implements OffHeapLongObjectMap<V> {

    private static final byte FREE = 0;
    private static final byte OCCUPIED = 1;
    private static final byte REMOVED = 2;

    private final Arena arena;
    private final MemorySegment keys;
    private final MemorySegment states;
    private final Object[] values;
    private final int capacity;
    private final int mask;
    private int size;

    /**
     * Creates a new off-heap long-to-object map with specified capacity.
     *
     * @param capacity the capacity (must be a power of 2)
     */
    public OffHeapLongObjectMapImpl(int capacity) {
        if (Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("Capacity must be a power of 2");
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.arena = Arena.ofShared();
        this.keys = arena.allocate(ValueLayout.JAVA_LONG, capacity);
        this.states = arena.allocate(ValueLayout.JAVA_BYTE, capacity);
        this.values = new Object[capacity];
        this.size = 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(long key) {
        int index = hash(key) & mask;
        int start = index;
        do {
            byte state = states.getAtIndex(ValueLayout.JAVA_BYTE, index);
            if (state == FREE) {
                return null;
            }
            if (state == OCCUPIED) {
                long k = keys.getAtIndex(ValueLayout.JAVA_LONG, index);
                if (k == key) {
                    return (V) values[index];
                }
            }
            index = (index + 1) & mask;
        } while (index != start);
        return null;
    }

    @Override
    public void put(long key, V value) {
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
                    values[index] = value; // Update
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

    private void insertAt(int index, long key, V value) {
        keys.setAtIndex(ValueLayout.JAVA_LONG, index, key);
        values[index] = value;
        states.setAtIndex(ValueLayout.JAVA_BYTE, index, OCCUPIED);
        size++;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V remove(long key) {
        int index = hash(key) & mask;
        int start = index;
        do {
            byte state = states.getAtIndex(ValueLayout.JAVA_BYTE, index);
            if (state == FREE) {
                return null;
            }
            if (state == OCCUPIED) {
                long k = keys.getAtIndex(ValueLayout.JAVA_LONG, index);
                if (k == key) {
                    V val = (V) values[index];
                    values[index] = null;
                    states.setAtIndex(ValueLayout.JAVA_BYTE, index, REMOVED);
                    size--;
                    return val;
                }
            }
            index = (index + 1) & mask;
        } while (index != start);
        return null;
    }

    @Override
    public void clear() {
        states.fill(FREE);
        for (int i = 0; i < capacity; i++) {
            values[i] = null;
        }
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
        return get(key) != null;
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
