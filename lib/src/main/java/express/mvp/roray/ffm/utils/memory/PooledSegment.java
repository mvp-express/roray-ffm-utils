package express.mvp.roray.ffm.utils.memory;

import java.lang.foreign.MemorySegment;

/** AutoCloseable wrapper that returns a MemorySegment to its pool when closed. */
public final class PooledSegment implements AutoCloseable {
    private final MemorySegment segment;
    private final MemorySegmentPool pool;

    public PooledSegment(MemorySegment segment, MemorySegmentPool pool) {
        this.segment = segment;
        this.pool = pool;
    }

    /** Returns the underlying MemorySegment. */
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public void close() {
        pool.release(segment);
    }
}
