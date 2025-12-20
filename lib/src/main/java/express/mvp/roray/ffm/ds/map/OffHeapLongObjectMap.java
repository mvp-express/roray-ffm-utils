package express.mvp.roray.ffm.ds.map;

/**
 * A primitive long-to-Object map backed by off-heap memory for the
 * keys/structure.
 *
 * <p>
 * Designed for zero-allocation lookups on hot paths. The keys are stored in
 * off-heap memory
 * (MemorySegment). The values are stored in a standard Java Object array
 * (on-heap).
 *
 * @param <V> The value type.
 */
public interface OffHeapLongObjectMap<V> extends AutoCloseable {

    V get(long key);

    void put(long key, V value);

    V remove(long key);

    void clear();

    int size();

    boolean isEmpty();

    boolean containsKey(long key);

    /** Releases the underlying off-heap memory. */
    @Override
    void close();
}
