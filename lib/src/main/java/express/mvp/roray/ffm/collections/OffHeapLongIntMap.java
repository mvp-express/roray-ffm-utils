package express.mvp.roray.ffm.collections;

/**
 * A primitive long-to-int map backed entirely by off-heap memory.
 *
 * <p>Designed for zero-allocation, zero-GC lookups on hot paths. Both keys and values are stored in
 * off-heap memory.
 */
public interface OffHeapLongIntMap extends AutoCloseable {

    /**
     * Gets the value for the given key in a packed format.
     *
     * @param key The key.
     * @return A packed long where bit 63 (MSB) is the "found" flag and bits 0-31 contain the
     *     int value. If the key is not found, the MSB is 0.
     *
     * <p><b>Example:</b>
     *
     * <pre>{@code
     * long packed = map.getPacked(key);
     * boolean found = packed < 0;   // MSB (bit 63) set => negative => found
     * if (found) {
     *     int value = (int) packed; // low 32 bits
     *     // use value
     * }
     * }</pre>
     */
    long getPacked(long key);

    void put(long key, int value);

    /**
     * Removes the key & returns its value in the same packed format as {@link #getPacked(long)}.
     *
     * @param key The key to remove.
     * @return A packed long where MSB indicates whether the key was present.
     * 
     */
    long removePacked(long key);

    void clear();

    int size();

    boolean isEmpty();

    boolean containsKey(long key);

    /** Releases the underlying off-heap memory. */
    @Override
    void close();
}
