package express.mvp.roray.utils.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A flexible, GC-free, and thread-safe pool for off-heap MemorySegments, designed for use with Loom
 * virtual threads.
 *
 * <p>This implementation is backed by a {@link Arena#ofShared()} and is intended to be used as a
 * long-lived, application-global singleton. It can be safely accessed by any number of virtual
 * threads.
 *
 * <p>⚠️ WARNING: This component is NOT AutoCloseable. The underlying shared arena is meant to live
 * for the duration of the application.
 */
public final class MemorySegmentPool {

    private final Arena arena;
    private final ConcurrentLinkedQueue<MemorySegment> pool;
    private final long segmentSize;
    private final int maxPoolSize;
    private final AtomicInteger totalSegments;
    private final boolean zeroOnRelease;

    // Metrics
    private final AtomicLong totalAllocations;
    private final AtomicInteger currentlyInUse;
    private final AtomicInteger peakUsage;

    /**
     * Creates a pool with default zeroing behavior (enabled).
     *
     * @param segmentSize Size of each segment in bytes.
     * @param initialSize Number of segments to pre-allocate.
     * @param maxSize Maximum number of segments the pool can create.
     */
    public MemorySegmentPool(long segmentSize, int initialSize, int maxSize) {
        this(segmentSize, initialSize, maxSize, true);
    }

    /**
     * Creates a pool with configurable zeroing behavior.
     *
     * @param segmentSize Size of each segment in bytes.
     * @param initialSize Number of segments to pre-allocate.
     * @param maxSize Maximum number of segments the pool can create.
     * @param zeroOnRelease Whether to zero segments on release (default: true). Set to false for
     *     performance-critical use cases where callers guarantee they will overwrite all data.
     */
    public MemorySegmentPool(
            long segmentSize, int initialSize, int maxSize, boolean zeroOnRelease) {
        if (segmentSize <= 0 || initialSize < 0 || maxSize <= 0 || initialSize > maxSize) {
            throw new IllegalArgumentException("Invalid pool size configuration.");
        }
        this.segmentSize = segmentSize;
        this.maxPoolSize = maxSize;
        this.zeroOnRelease = zeroOnRelease;
        this.arena = Arena.ofAuto();
        this.pool = new ConcurrentLinkedQueue<>();
        this.totalSegments = new AtomicInteger(initialSize);

        // Initialize metrics
        this.totalAllocations = new AtomicLong(initialSize);
        this.currentlyInUse = new AtomicInteger(0);
        this.peakUsage = new AtomicInteger(0);

        for (int i = 0; i < initialSize; i++) {
            pool.offer(arena.allocate(segmentSize, 1));
        }
    }

    /**
     * Acquires a memory segment from the pool.
     *
     * @return a memory segment of the configured size
     * @throws IllegalStateException if the pool is exhausted
     */
    public MemorySegment acquire() {
        MemorySegment segment = pool.poll();
        if (segment != null) {
            // Update metrics
            int inUse = currentlyInUse.incrementAndGet();
            updatePeakUsage(inUse);
            return segment;
        }

        // Correct CAS loop for thread-safe growth
        for (; ; ) {
            int currentTotal = totalSegments.get();
            if (currentTotal >= maxPoolSize) {
                throw new IllegalStateException(
                        "MemorySegmentPool is exhausted. Max size (" + maxPoolSize + ") reached.");
            }
            if (totalSegments.compareAndSet(currentTotal, currentTotal + 1)) {
                // Update metrics
                totalAllocations.incrementAndGet();
                int inUse = currentlyInUse.incrementAndGet();
                updatePeakUsage(inUse);
                return arena.allocate(segmentSize, 1);
            }
        }
    }

    /**
     * Acquires a memory segment with at least the required size.
     *
     * <p>If the required size is less than or equal to the pool's segment size, a pooled segment is
     * returned. Otherwise, a new segment is allocated directly from the arena.
     *
     * @param requiredSize the minimum size required
     * @return a memory segment of at least the required size
     */
    public MemorySegment acquire(long requiredSize) {
        if (requiredSize <= this.segmentSize) {
            return acquire();
        }
        return arena.allocate(requiredSize, 1);
    }

    /**
     * Releases a memory segment back to the pool.
     *
     * <p>The segment will be zeroed (if configured) and returned to the pool for reuse. Segments
     * with a different size than the pool's segment size are silently ignored.
     *
     * @param segment the segment to release (may be null)
     */
    public void release(MemorySegment segment) {
        if (segment != null && segment.byteSize() == this.segmentSize) {
            if (zeroOnRelease) {
                segment.fill((byte) 0);
            }
            pool.offer(segment);
            currentlyInUse.decrementAndGet();
        }
    }

    public int getAvailableCount() {
        return pool.size();
    }

    public int getTotalCount() {
        return totalSegments.get();
    }

    public long getSegmentSize() {
        return segmentSize;
    }

    /**
     * Returns the total number of allocations made by this pool since creation. This includes both
     * pooled segments reused and new segments allocated.
     *
     * @return Total allocation count.
     */
    public long getTotalAllocations() {
        return totalAllocations.get();
    }

    /**
     * Returns the current number of segments in use (acquired but not released).
     *
     * @return Currently in-use segment count.
     */
    public int getCurrentlyInUse() {
        return currentlyInUse.get();
    }

    /**
     * Returns the peak number of segments that were in use simultaneously.
     *
     * @return Peak usage count.
     */
    public int getPeakUsage() {
        return peakUsage.get();
    }

    private void updatePeakUsage(int currentInUse) {
        for (; ; ) {
            int currentPeak = peakUsage.get();
            if (currentInUse <= currentPeak) {
                break;
            }
            if (peakUsage.compareAndSet(currentPeak, currentInUse)) {
                break;
            }
        }
    }

    // Note: No close() method, as the shared arena is intended to live for the
    // application's lifetime.
}
