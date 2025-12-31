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

/**
 * Additional comprehensive tests for MemorySegmentPool focusing on metrics, configurable zeroing,
 * and stress scenarios.
 */
@SuppressWarnings("checkstyle:VariableDeclarationUsageDistance") // Test setup pattern
class MemorySegmentPoolExtendedTest {

    // =================================================================
    // Metrics Tests
    // =================================================================

    @Test
    void metrics_TotalAllocations_TracksCorrectly() {
        var pool = new MemorySegmentPool(128, 5, 20);

        // Initial allocations
        assertEquals(5, pool.getTotalAllocations());

        // Acquire from pool (doesn't increment)
        MemorySegment seg1 = pool.acquire();
        assertEquals(5, pool.getTotalAllocations());

        pool.release(seg1);
        assertEquals(5, pool.getTotalAllocations());

        // Exhaust pool and force new allocations
        List<MemorySegment> segments = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            segments.add(pool.acquire());
        }

        assertEquals(10, pool.getTotalAllocations());

        // Release all
        segments.forEach(pool::release);
    }

    @Test
    void metrics_CurrentlyInUse_TracksCorrectly() {
        var pool = new MemorySegmentPool(128, 5, 20);

        assertEquals(0, pool.getCurrentlyInUse());

        MemorySegment seg1 = pool.acquire();
        assertEquals(1, pool.getCurrentlyInUse());

        MemorySegment seg2 = pool.acquire();
        MemorySegment seg3 = pool.acquire();
        assertEquals(3, pool.getCurrentlyInUse());

        pool.release(seg1);
        assertEquals(2, pool.getCurrentlyInUse());

        pool.release(seg2);
        pool.release(seg3);
        assertEquals(0, pool.getCurrentlyInUse());
    }

    @Test
    void metrics_PeakUsage_TracksCorrectly() {
        var pool = new MemorySegmentPool(128, 5, 20);

        assertEquals(0, pool.getPeakUsage());

        MemorySegment seg1 = pool.acquire();
        assertEquals(1, pool.getPeakUsage());

        MemorySegment seg2 = pool.acquire();
        MemorySegment seg3 = pool.acquire();
        assertEquals(3, pool.getPeakUsage());

        pool.release(seg1);
        assertEquals(3, pool.getPeakUsage()); // Peak remains

        MemorySegment seg4 = pool.acquire();
        MemorySegment seg5 = pool.acquire();
        assertEquals(4, pool.getPeakUsage()); // New peak (seg2, seg3, seg4, seg5)

        pool.release(seg2);
        pool.release(seg3);
        pool.release(seg4);
        pool.release(seg5);
        assertEquals(4, pool.getPeakUsage()); // Peak unchanged
    }

    @Test
    void metrics_ConcurrentPeakTracking() throws InterruptedException {
        var pool = new MemorySegmentPool(128, 10, 100);
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.execute(
                    () -> {
                        List<MemorySegment> held = new ArrayList<>();
                        for (int j = 0; j < 3; j++) {
                            held.add(pool.acquire());
                        }
                        // Hold briefly
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        held.forEach(pool::release);
                    });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Peak should reflect maximum concurrent usage
        assertTrue(pool.getPeakUsage() > 0);
        assertTrue(pool.getPeakUsage() <= numThreads * 3);
        assertEquals(0, pool.getCurrentlyInUse());
    }

    // =================================================================
    // Configurable Zeroing Tests
    // =================================================================

    @Test
    void zeroOnRelease_WhenEnabled_ZerosSegment() {
        var pool = new MemorySegmentPool(128, 1, 5, true);

        MemorySegment segment = pool.acquire();
        segment.set(ValueLayout.JAVA_INT, 0, 42);
        segment.set(ValueLayout.JAVA_INT, 4, 99);

        pool.release(segment);

        MemorySegment reacquired = pool.acquire();
        assertEquals(0, reacquired.get(ValueLayout.JAVA_INT, 0));
        assertEquals(0, reacquired.get(ValueLayout.JAVA_INT, 4));

        pool.release(reacquired);
    }

    @Test
    void zeroOnRelease_WhenDisabled_PreservesData() {
        var pool = new MemorySegmentPool(128, 1, 5, false);

        MemorySegment segment = pool.acquire();
        segment.set(ValueLayout.JAVA_INT, 0, 42);
        segment.set(ValueLayout.JAVA_INT, 4, 99);

        pool.release(segment);

        MemorySegment reacquired = pool.acquire();
        assertEquals(42, reacquired.get(ValueLayout.JAVA_INT, 0));
        assertEquals(99, reacquired.get(ValueLayout.JAVA_INT, 4));

        pool.release(reacquired);
    }

    @Test
    void defaultConstructor_EnablesZeroing() {
        var pool = new MemorySegmentPool(128, 1, 5);

        MemorySegment segment = pool.acquire();
        segment.set(ValueLayout.JAVA_INT, 0, 42);
        pool.release(segment);

        MemorySegment reacquired = pool.acquire();
        assertEquals(0, reacquired.get(ValueLayout.JAVA_INT, 0));

        pool.release(reacquired);
    }

    // =================================================================
    // Stress Tests
    // =================================================================

    @Test
    void stressTest_HighContentionScenario() throws InterruptedException {
        var pool = new MemorySegmentPool(256, 10, 50);
        int numThreads = 100;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Exception> exceptions = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            executor.execute(
                    () -> {
                        try {
                            for (int j = 0; j < operationsPerThread; j++) {
                                MemorySegment seg = pool.acquire();
                                seg.set(ValueLayout.JAVA_LONG, 0, System.nanoTime());
                                pool.release(seg);
                            }
                        } catch (Exception e) {
                            synchronized (exceptions) {
                                exceptions.add(e);
                            }
                        }
                    });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        synchronized (exceptions) {
            assertTrue(exceptions.isEmpty(), "No exceptions during high contention");
        }
        assertEquals(0, pool.getCurrentlyInUse());
    }

    @Test
    void stressTest_RapidAcquireRelease() {
        var pool = new MemorySegmentPool(128, 5, 100);
        int iterations = 10000;

        for (int i = 0; i < iterations; i++) {
            MemorySegment seg = pool.acquire();
            seg.set(ValueLayout.JAVA_INT, 0, i);
            pool.release(seg);
        }

        assertEquals(0, pool.getCurrentlyInUse());
        assertTrue(pool.getTotalAllocations() >= 5);
    }

    @Test
    void stressTest_BurstPattern() {
        var pool = new MemorySegmentPool(256, 10, 100);
        int numBursts = 10;
        int burstSize = 30;

        for (int burst = 0; burst < numBursts; burst++) {
            List<MemorySegment> segments = new ArrayList<>();

            // Acquire burst
            for (int i = 0; i < burstSize; i++) {
                segments.add(pool.acquire());
            }

            assertEquals(burstSize, pool.getCurrentlyInUse());

            // Release burst
            segments.forEach(pool::release);
            assertEquals(0, pool.getCurrentlyInUse());
        }

        assertEquals(0, pool.getCurrentlyInUse());
    }

    @Test
    void stressTest_VirtualThreads() throws InterruptedException {
        // Pool with reasonable max size to support high virtual thread concurrency
        var pool = new MemorySegmentPool(128, 50, 500);
        int numVirtualThreads = 1000;
        int operationsPerThread = 50;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Exception> exceptions = new ArrayList<>();

            for (int i = 0; i < numVirtualThreads; i++) {
                executor.execute(
                        () -> {
                            try {
                                for (int j = 0; j < operationsPerThread; j++) {
                                    MemorySegment seg = pool.acquire();
                                    seg.set(
                                            ValueLayout.JAVA_INT,
                                            0,
                                            Thread.currentThread().hashCode());
                                    // Immediate release to avoid pool exhaustion with 1000
                                    // concurrent threads
                                    pool.release(seg);
                                }
                            } catch (Exception e) {
                                synchronized (exceptions) {
                                    exceptions.add(e);
                                }
                            }
                        });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

            synchronized (exceptions) {
                if (!exceptions.isEmpty()) {
                    System.err.println(
                            "Virtual threads test caught " + exceptions.size() + " exceptions:");
                    for (Exception e : exceptions) {
                        e.printStackTrace();
                    }
                }
                assertTrue(exceptions.isEmpty(), "No exceptions with virtual threads");
            }
        }

        assertEquals(0, pool.getCurrentlyInUse());
        assertTrue(pool.getPeakUsage() > 0);
    }

    // =================================================================
    // Edge Cases
    // =================================================================

    @Test
    void edgeCase_AcquireExactMaxSize() {
        var pool = new MemorySegmentPool(128, 0, 10);

        List<MemorySegment> segments = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            segments.add(pool.acquire());
        }

        assertEquals(10, pool.getTotalCount());
        assertEquals(10, pool.getCurrentlyInUse());

        assertThrows(IllegalStateException.class, () -> pool.acquire());

        segments.forEach(pool::release);
        assertEquals(0, pool.getCurrentlyInUse());
    }

    @Test
    void edgeCase_ZeroInitialSize() {
        var pool = new MemorySegmentPool(128, 0, 10);

        assertEquals(0, pool.getAvailableCount());
        assertEquals(0, pool.getTotalCount());

        MemorySegment seg = pool.acquire();
        assertNotNull(seg);
        assertEquals(1, pool.getTotalCount());

        pool.release(seg);
    }

    @Test
    void edgeCase_ReleaseDifferentSize() {
        var pool = new MemorySegmentPool(128, 5, 10);

        // Create a segment with different size
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            MemorySegment differentSize = arena.allocate(256);
            pool.release(differentSize); // Should be silently ignored
        }

        assertEquals(5, pool.getAvailableCount());
    }

    @Test
    void edgeCase_ReleaseNull() {
        var pool = new MemorySegmentPool(128, 5, 10);

        assertDoesNotThrow(() -> pool.release(null)); // Should not throw

        assertEquals(5, pool.getAvailableCount());
    }

    @Test
    void edgeCase_LargeSegmentSize() {
        var pool = new MemorySegmentPool(1024 * 1024, 2, 5); // 1MB segments

        MemorySegment seg = pool.acquire();
        assertEquals(1024 * 1024, seg.byteSize());

        pool.release(seg);
    }
}
