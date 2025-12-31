package express.mvp.roray.ffm.utils.memory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * A collection of stateless, high-performance utility methods for operating on {@link
 * MemorySegment}s.
 */
public final class SegmentUtils {

    private SegmentUtils() {}

    /**
     * Thread-local pool of CRC32 instances to avoid allocation on every checksum calculation. Each
     * thread gets its own CRC32 instance, eliminating contention and GC pressure.
     */
    private static final ThreadLocal<CRC32> CRC32_POOL = ThreadLocal.withInitial(CRC32::new);

    /**
     * Calculates the CRC32 checksum for the data within the given memory segment. This method uses
     * a pooled CRC32 instance per thread and avoids heap allocation. It prefers a zero-copy
     * ByteBuffer view when supported, and falls back to direct byte reads for shared segments that
     * cannot expose a ByteBuffer.
     *
     * @param segment The segment of memory to checksum.
     * @return The 32-bit CRC32 checksum value.
     */
    public static int calculateCrc32(MemorySegment segment) {
        // Get pooled CRC32 instance for this thread (no allocation after first use)
        CRC32 crc32 = CRC32_POOL.get();
        crc32.reset();

        try {
            // segment.asByteBuffer() creates a zero-copy view of the off-heap memory
            ByteBuffer bufferView = segment.asByteBuffer();

            // update() processes all bytes in the buffer
            crc32.update(bufferView);
        } catch (UnsupportedOperationException ex) {
            // Some shared segments cannot expose a ByteBuffer view; fall back to direct reads.
            long size = segment.byteSize();
            for (long i = 0; i < size; i++) {
                crc32.update(segment.get(ValueLayout.JAVA_BYTE, i));
            }
        }

        // getValue() returns a long, but CRC32 is a 32-bit value.
        return (int) crc32.getValue();
    }

    // Commented out vectorizedZero implementation - Vector API not yet enabled
    // public static void vectorizedZero(MemorySegment segment) {
    // // 1. Find the largest vector shape (species) supported by the CPU for byte
    // // operations.
    // // This could be 128, 256, or 512 bits depending on the hardware.
    // final VectorSpecies<Byte> species = ByteVector.SPECIES_PREFERRED;
    // final int vectorSize = species.vectorByteSize();
    // final ByteVector zeroVector = ByteVector.zero(species);

    // long offset = 0;
    // long loopBound = segment.byteSize() - vectorSize;

    // // 2. Main loop: Write zeros in large, vectorized chunks.
    // for (; offset <= loopBound; offset += vectorSize) {
    // zeroVector.intoMemorySegment(segment, offset, ByteOrder.nativeOrder());
    // }

    // // 3. Scalar tail loop: Zero out any remaining bytes that don't fit in a full
    // // vector.
    // for (; offset < segment.byteSize(); offset++) {
    // segment.set(ValueLayout.JAVA_BYTE, offset, (byte) 0);
    // }
    // }
}
