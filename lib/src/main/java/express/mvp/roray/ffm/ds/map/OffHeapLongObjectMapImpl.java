package express.mvp.roray.ffm.ds.map;

import java.lang.foreign.ValueLayout;

/**
 * A linear-probing, open-addressing hash map with off-heap keys and on-heap values.
 *
 * <p>Not thread-safe. Designed for single-threaded hot paths.
 */
@Deprecated
public class OffHeapLongObjectMapImpl<V> extends AbstractOffHeapLongKeyOpenAddressingTable
        implements OffHeapLongObjectMap<V> {
    // TODO: implement a complete off-heap key,value map later
    private final Object[] values;

    /**
     * Creates a new off-heap long-to-object map with specified capacity.
     *
     * @param capacity the capacity (must be a power of 2)
     */
    public OffHeapLongObjectMapImpl(int capacity) {
        super(capacity);
        this.values = new Object[capacity];
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(long key) {
        ensureOpen();
        int index = findIndex(key);
        return index == -1 ? (V) (Object) null : (V) values[index];
    }

    @Override
    public void put(long key, V value) {
        ensureOpen();
        long result = probeForPut(key);
        int index = (int) result;
        if (result < 0) {
            values[index] = value;
            return;
        }
        keys.setAtIndex(ValueLayout.JAVA_LONG, index, key);
        values[index] = value;
        states.setAtIndex(ValueLayout.JAVA_BYTE, index, OCCUPIED);
        size++;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V remove(long key) {
        ensureOpen();
        int index = findIndex(key);
        if (index == -1) {
            return (V) (Object) null;
        }
        V val = (V) values[index];
        values[index] = null;
        states.setAtIndex(ValueLayout.JAVA_BYTE, index, REMOVED);
        size--;
        return val;
    }

    @Override
    public void clear() {
        ensureOpen();
        clearStates();
        for (int i = 0; i < capacity; i++) {
            values[i] = null;
        }
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
