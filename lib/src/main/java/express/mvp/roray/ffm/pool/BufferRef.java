package express.mvp.roray.ffm.pool;

import java.lang.foreign.MemorySegment;

/**
 * A reference-counted handle to a shared off-heap memory segment.
 *
 * <p>This is a general-purpose building block for off-heap pooling. It wraps a {@link
 * MemorySegment} with explicit ownership semantics via reference counting, designed for efficient
 * reuse.
 *
 * <h2>Reference Counting</h2>
 *
 * <ul>
 *   <li><b>Acquire:</b> When obtained from a pool, refCount starts at 1
 *   <li><b>Retain:</b> Call {@link #retain()} to increment count when sharing
 *   <li><b>Release:</b> Call {@link #release()} when done; auto-returns to pool at 0
 * </ul>
 *
 * <h2>SoA (Structure of Arrays)</h2>
 *
 * <p>{@link #poolIndex()} supports SoA patterns where metadata (timestamps, flags, tokens, etc.) is
 * stored in separate primitive arrays indexed by pool index, avoiding additional object overhead.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Reference counting operations ({@link #retain()}, {@link #release()}) are thread-safe using
 * lock-free CAS operations. The underlying segment access is not synchronized; callers must ensure
 * proper happens-before relationships.
 *
 * @see BufferRefImpl
 * @see LockFreeBufferPool
 */
public interface BufferRef {

    /**
     * Returns the underlying FFM MemorySegment.
     *
     * <p><b>Warning:</b> Accessing this segment after {@link #release()} has reduced the refCount
     * to 0 is undefined behavior. The segment may be reused by another thread or the underlying
     * memory may be invalidated.
     */
    MemorySegment segment();

    /**
     * Returns the raw native address of the segment.
     *
     * <p>This is cached to avoid repeated FFM overhead on hot paths.
     */
    long address();

    /**
     * Returns the unique index of this buffer in its pool.
     *
     * @return pool index (0-based)
     */
    int poolIndex();

    /** Returns the current length of valid data in the buffer (bytes). */
    int length();

    /** Sets the length of valid data in the buffer (bytes). */
    void length(int newLength);

    /** Increments the reference count. */
    void retain();

    /** Decrements the reference count (auto-returns to pool at 0). */
    void release();
}
