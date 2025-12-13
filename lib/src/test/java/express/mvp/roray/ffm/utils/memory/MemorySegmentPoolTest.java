package express.mvp.roray.ffm.utils.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MemorySegmentPoolTest {

    // =================================================================
    // Constructor and Initialization Tests
    // =================================================================

    @Test
    void constructor_WithValidParameters_InitializesCorrectly() {
        var pool = new MemorySegmentPool(1024, 10, 20);

        assertEquals(1024, pool.getSegmentSize());
        // The initial available count relies on pool.size(), which is correct
        assertEquals(
                10, pool.getAvailableCount(), "Initial available count should match initial size");
        assertEquals(10, pool.getTotalCount(), "Initial total count should match initial size");
    }

    @Test
    void constructor_WithInvalidSegmentSize_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> new MemorySegmentPool(0, 10, 20));
        assertThrows(IllegalArgumentException.class, () -> new MemorySegmentPool(-1, 10, 20));
    }

    @Test
    void constructor_WithInvalidPoolSizes_ShouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MemorySegmentPool(128, -1, 10),
                "Negative initial size");
        assertThrows(
                IllegalArgumentException.class,
                () -> new MemorySegmentPool(128, 5, 0),
                "Zero max size");
        assertThrows(
                IllegalArgumentException.class,
                () -> new MemorySegmentPool(128, 11, 10),
                "Initial size greater than max size");
    }

    // =================================================================
    // Core Acquire/Release Tests
    // =================================================================

    @Test
    void acquire_WhenPoolHasAvailableSegments_ShouldSucceed() {
        var pool = new MemorySegmentPool(128, 5, 10);
        assertEquals(5, pool.getAvailableCount());
        MemorySegment segment = pool.acquire();
        assertNotNull(segment);
        assertEquals(128, segment.byteSize());
        assertEquals(4, pool.getAvailableCount(), "Available count should decrease after acquire");
        assertEquals(5, pool.getTotalCount(), "Total count should not change yet");
    }

    @Test
    void release_StandardSegment_ReturnsToPoolAndIncreasesAvailableCount() {
        var pool = new MemorySegmentPool(128, 5, 10);
        MemorySegment segment = pool.acquire();
        assertEquals(4, pool.getAvailableCount());

        pool.release(segment);
        assertEquals(
                5, pool.getAvailableCount(), "Available count should be restored after release");
    }

    @Test
    void acquire_GrowsPool_WhenInitialSegmentsExhausted() {
        var pool = new MemorySegmentPool(128, 2, 5);
        pool.acquire(); // 1 left
        pool.acquire(); // 0 left
        assertEquals(0, pool.getAvailableCount());
        assertEquals(2, pool.getTotalCount());

        // This acquire should grow the pool
        MemorySegment newSegment = pool.acquire();
        assertNotNull(newSegment);
        assertEquals(0, pool.getAvailableCount(), "Grown segment is immediately in use");
        assertEquals(3, pool.getTotalCount(), "Total count should increase when pool grows");
    }

    @Test
    void acquire_ThrowsException_WhenPoolIsExhaustedAndAtMaxSize() {
        var pool = new MemorySegmentPool(128, 2, 2);
        pool.acquire();
        pool.acquire();

        // Pool is now exhausted and at max size
        var exception = assertThrows(IllegalStateException.class, pool::acquire);
        assertTrue(exception.getMessage().contains("exhausted"));
    }

    @Test
    void release_ClearsSegmentMemory() {
        var pool = new MemorySegmentPool(128, 1, 1);
        // 1. Acquire, write data, and release
        MemorySegment segment1 = pool.acquire();
        segment1.set(ValueLayout.JAVA_BYTE, 0, (byte) 42);
        pool.release(segment1);

        // 2. Re-acquire the same segment
        MemorySegment segment2 = pool.acquire();

        // 3. Verify the memory was zeroed out
        assertEquals(
                (byte) 0,
                segment2.get(ValueLayout.JAVA_BYTE, 0),
                "Memory should be cleared upon release");
    }

    // =================================================================
    // Variable-Sized Segment Tests
    // =================================================================

    @Test
    void acquire_WithLargeSize_ReturnsCorrectSizeAndDoesNotAffectPoolMetrics() {
        var pool = new MemorySegmentPool(128, 5, 10);
        long largeSize = 512;
        MemorySegment largeSegment = pool.acquire(largeSize);

        assertEquals(largeSize, largeSegment.byteSize());
        assertEquals(
                5,
                pool.getAvailableCount(),
                "Acquiring large segment should not affect available count");
    }

    @Test
    void release_IgnoresLargeSegment() {
        var pool = new MemorySegmentPool(128, 5, 10);
        MemorySegment largeSegment = pool.acquire(512);
        pool.release(largeSegment);

        assertEquals(
                5,
                pool.getAvailableCount(),
                "Releasing large segment should not return it to the pool");
    }

    // =================================================================
    // Lifecycle and Concurrency Tests
    // =================================================================

    @Test
    void concurrentAcquireAndRelease_MaintainsPoolIntegrity() throws InterruptedException {
        int numThreads = 8;
        int operationsPerThread = 1000;
        int maxPoolSize = 20;

        var pool = new MemorySegmentPool(1024, numThreads, maxPoolSize);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Exception> exceptions = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(
                    () -> {
                        try {
                            for (int j = 0; j < operationsPerThread; j++) {
                                MemorySegment segment = pool.acquire();
                                segment.set(
                                        ValueLayout.JAVA_INT, 0, Thread.currentThread().hashCode());
                                pool.release(segment);
                            }
                        } catch (Exception e) {
                            exceptions.add(e);
                        }
                    });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertTrue(exceptions.isEmpty(), "No exceptions should be thrown during concurrent access");
        // The final assertion needs to account for the actual number of segments
        // created.
        assertEquals(
                pool.getTotalCount(),
                pool.getAvailableCount(),
                "All segments should be returned to the pool");
        assertTrue(pool.getTotalCount() <= maxPoolSize, "Pool should not grow beyond max size");
    }
}
