package express.mvp.roray.utils.memory;

import java.lang.foreign.MemorySegment;

/**
 * Defines the contract for a flyweight object.
 *
 * <p>A flyweight is a lightweight Java object that does not hold data itself. Instead, it wraps a
 * {@link MemorySegment} and provides structured accessor methods (getters/setters) that read from
 * and write to the underlying memory at specific offsets. This enables zero-copy, object-oriented
 * data manipulation.
 */
public interface FlyweightAccessor {

    /**
     * Wraps a memory segment at a specific offset to make this flyweight point to the underlying
     * data. This is the core method for reusing the flyweight object.
     *
     * @param segment The memory segment containing the structured data.
     * @param offset The starting position of the data for this flyweight within the segment.
     */
    void wrap(MemorySegment segment, long offset);

    /**
     * Returns the underlying MemorySegment this flyweight is currently pointing to.
     *
     * @return The MemorySegment, or null if not wrapped.
     */
    MemorySegment segment();

    /**
     * Returns the total size in bytes of the data structure this flyweight represents. This is also
     * known as the "block length".
     *
     * @return The size of the structure in bytes.
     */
    int byteSize();

    /**
     * Writes the data represented by this flyweight to the given writer.
     *
     * @param writer The writer to serialize the data into.
     */
    void writeTo(BinaryWriter writer);

    /**
     * Checks if this flyweight is currently wrapped around a valid segment.
     *
     * @return true if wrapped with a non-null segment, false otherwise.
     */
    boolean isWrapped();

    /**
     * Validates that this flyweight is in a valid state (has a non-null segment).
     *
     * @throws IllegalStateException if the flyweight is not wrapped.
     */
    void validate();
}
