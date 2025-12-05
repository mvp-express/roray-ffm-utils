package express.mvp.roray.utils.collections;

/**
 * A primitive long-to-int map backed entirely by off-heap memory.
 *
 * <p>Designed for zero-allocation, zero-GC lookups on hot paths. Both keys and values are stored in
 * off-heap memory.
 */
public interface OffHeapLongIntMap extends AutoCloseable {

    /**
     * Gets the value for the given key.
     *
     * @param key The key.
     * @return The value, or the configured "missing value" (default -1) if not found.
     */
    int get(long key);

    void put(long key, int value);

    /**
     * Removes the key and returns its value.
     *
     * @param key The key to remove.
     * @return The value, or the "missing value" if not found.
     */
    int remove(long key);

    void clear();

    int size();

    boolean isEmpty();

    boolean containsKey(long key);

    /** Releases the underlying off-heap memory. */
    @Override
    void close();
}
