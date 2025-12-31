package express.mvp.roray.ffm.utils.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

/** Tests for {@link SegmentUtils}, particularly the CRC32 pooling optimization. */
class SegmentUtilsTest {

    @Test
    void calculateCrc32_ShouldReturnCorrectChecksum() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(16);
            // Write known data
            for (int i = 0; i < 16; i++) {
                segment.set(Layouts.BYTE, i, (byte) i);
            }

            int checksum = SegmentUtils.calculateCrc32(segment);

            // Verify against reference implementation
            assertEquals(calculateCrc32Reference(segment), checksum);
        }
    }

    @Test
    void calculateCrc32_ShouldReturnZeroForEmptySegment() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(0);

            int checksum = SegmentUtils.calculateCrc32(segment);

            // CRC32 of empty data is 0
            assertEquals(0, checksum);
        }
    }

    @Test
    void calculateCrc32_ShouldBeDeterministic() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(100);
            for (int i = 0; i < 100; i++) {
                segment.set(Layouts.BYTE, i, (byte) (i * 7));
            }

            int checksum1 = SegmentUtils.calculateCrc32(segment);
            int checksum2 = SegmentUtils.calculateCrc32(segment);
            int checksum3 = SegmentUtils.calculateCrc32(segment);

            assertEquals(checksum1, checksum2);
            assertEquals(checksum2, checksum3);
        }
    }

    @Test
    void calculateCrc32_ShouldResetBetweenCalls() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment1 = arena.allocate(10);
            MemorySegment segment2 = arena.allocate(10);

            // Fill with different data
            segment1.fill((byte) 0xAA);
            segment2.fill((byte) 0xBB);

            int checksum1 = SegmentUtils.calculateCrc32(segment1);
            int checksum2 = SegmentUtils.calculateCrc32(segment2);

            // Checksums should be different (proves reset is working)
            assertNotEquals(checksum1, checksum2);

            // Calling again should produce same results (proves reset is correct)
            assertEquals(checksum1, SegmentUtils.calculateCrc32(segment1));
            assertEquals(checksum2, SegmentUtils.calculateCrc32(segment2));
        }
    }

    @Test
    void calculateCrc32_ShouldWorkCorrectlyWithMultipleThreads() throws InterruptedException {
        int threadCount = 8;
        int iterationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try (Arena arena = Arena.ofShared()) {
            // Create segments with known data for each thread
            MemorySegment[] segments = new MemorySegment[threadCount];
            int[] expectedChecksums = new int[threadCount];

            for (int i = 0; i < threadCount; i++) {
                segments[i] = arena.allocate(64);
                segments[i].fill((byte) (i + 1));
                // Compute expected checksum
                expectedChecksums[i] = calculateCrc32Reference(segments[i]);
            }

            // Submit tasks
            for (int t = 0; t < threadCount; t++) {
                final int threadIdx = t;
                futures.add(
                        executor.submit(
                                () -> {
                                    startLatch.await(); // Wait for all threads to be ready
                                    for (int i = 0; i < iterationsPerThread; i++) {
                                        int actual =
                                                SegmentUtils.calculateCrc32(segments[threadIdx]);
                                        if (actual != expectedChecksums[threadIdx]) {
                                            return false;
                                        }
                                    }
                                    return true;
                                }));
            }

            // Release all threads at once
            startLatch.countDown();

            // Verify all threads completed successfully
            for (Future<Boolean> future : futures) {
                try {
                    assertTrue(
                            future.get(10, TimeUnit.SECONDS),
                            "Thread failed checksum verification");
                } catch (Exception e) {
                    fail("Thread failed checksum verification", e);
                }
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void calculateCrc32_PooledInstanceShouldNotLeakState() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment small = arena.allocate(8);
            MemorySegment large = arena.allocate(1024);

            small.fill((byte) 0x11);
            large.fill((byte) 0x22);

            // Interleave calls to ensure pooled instance doesn't leak state
            int smallChecksum1 = SegmentUtils.calculateCrc32(small);
            int largeChecksum1 = SegmentUtils.calculateCrc32(large);
            int smallChecksum2 = SegmentUtils.calculateCrc32(small);
            int largeChecksum2 = SegmentUtils.calculateCrc32(large);

            assertEquals(smallChecksum1, smallChecksum2, "Small segment checksum should be stable");
            assertEquals(largeChecksum1, largeChecksum2, "Large segment checksum should be stable");
            assertNotEquals(
                    smallChecksum1,
                    largeChecksum1,
                    "Different data should have different checksums");
        }
    }

    private static int calculateCrc32Reference(MemorySegment segment) {
        CRC32 crc32 = new CRC32();
        long size = segment.byteSize();
        for (long i = 0; i < size; i++) {
            crc32.update(segment.get(Layouts.BYTE, i));
        }
        return (int) crc32.getValue();
    }
}
