package express.mvp.roray.ffm.utils.memory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.foreign.MemorySegment;

/**
 * An AutoCloseable wrapper for a MemorySegment that automatically releases the segment back to its
 * pool when used in a try-with-resources block.
 *
 * <h2>Thread Safety</h2>
 *
 * <p><b>This class is NOT thread-safe for concurrent access to the same instance.</b> Once created,
 * a {@code PooledSegment} should be owned by a single thread until it is closed.
 *
 * <p>However, the class is safe to:
 *
 * <ul>
 *   <li>Create in one thread and transfer ownership to another (before any access)
 *   <li>Close from a different thread than where it was created (single close only)
 * </ul>
 *
 * <p><b>Important:</b> Do not access the underlying segment after calling {@link #close()}. The
 * segment is returned to the pool and may be reused by other code.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * try (PooledSegment pooled = new PooledSegment(encoder.acquire(1024), pool)) {
 *     MemorySegment segment = pooled.segment();
 *     // ... use segment for encoding ...
 * } // Segment automatically released back to pool
 * }</pre>
 */
public final class PooledSegment implements AutoCloseable {
    private final MemorySegment segment;
    private final MemorySegmentPool pool;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Stores pooled segment reference by design.")
    public PooledSegment(MemorySegment segment, MemorySegmentPool pool) {
        this.segment = segment;
        this.pool = pool;
    }

    /** Gets the underlying MemorySegment. */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Exposes pooled MemorySegment for zero-copy access.")
    public MemorySegment segment() {
        return this.segment;
    }

    @Override
    public void close() {
        // This is the key feature: automatically release the segment.
        pool.release(this.segment);
    }
}
