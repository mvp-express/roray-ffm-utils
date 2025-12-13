package express.mvp.roray.ffm.utils.memory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A reusable, zero-allocation flyweight for performing bit-level operations over a MemorySegment.
 *
 * <p>This provides a BitSet-like API for off-heap memory without heap allocation. Bits are indexed
 * from 0, where bit 0 is the LSB of byte 0.
 *
 * <p><b>Thread Safety:</b> Not thread-safe. Use one instance per thread.
 *
 * <p><b>Zero-GC:</b> All operations avoid heap allocation.
 */
public final class BitSetView {

    private MemorySegment segment;
    private long offset;
    private long size; // size in bytes

    /**
     * Wraps a segment slice for bit operations.
     *
     * @param segment The backing memory segment.
     * @param offset The offset within the segment where the bit array starts.
     * @param size The size of the bit array in bytes.
     */
    public void wrap(MemorySegment segment, long offset, long size) {
        this.segment = segment;
        this.offset = offset;
        this.size = size;
    }

    /**
     * Returns the underlying MemorySegment this view is wrapping.
     *
     * @return The MemorySegment, or null if not wrapped.
     */
    public MemorySegment segment() {
        return segment;
    }

    /**
     * Returns the offset within the segment where the bit array starts.
     *
     * @return The byte offset.
     */
    public long offset() {
        return offset;
    }

    /**
     * Returns the size of the bit array in bytes.
     *
     * @return The byte size.
     */
    public long byteSize() {
        return size;
    }

    /**
     * Checks if this view has been wrapped around valid data.
     *
     * @return true if wrapped with a non-null segment, false otherwise.
     */
    public boolean isValid() {
        return segment != null;
    }

    /**
     * Sets the bit at the specified index to 1.
     *
     * @param bitIndex The index of the bit to set (0-based).
     * @throws IndexOutOfBoundsException if bitIndex is out of range.
     */
    public void set(long bitIndex) {
        validateBitIndex(bitIndex);
        long byteIndex = bitIndex >>> 3; // Divide by 8
        int bitPosition = (int) (bitIndex & 7); // Modulo 8
        long address = offset + byteIndex;

        byte currentByte = segment.get(ValueLayout.JAVA_BYTE, address);
        byte newByte = (byte) (currentByte | (1 << bitPosition));
        segment.set(ValueLayout.JAVA_BYTE, address, newByte);
    }

    /**
     * Sets the bit at the specified index to the specified value.
     *
     * @param bitIndex The index of the bit to set (0-based).
     * @param value true to set the bit, false to clear it.
     * @throws IndexOutOfBoundsException if bitIndex is out of range.
     */
    public void set(long bitIndex, boolean value) {
        if (value) {
            set(bitIndex);
        } else {
            clear(bitIndex);
        }
    }

    /**
     * Clears the bit at the specified index (sets it to 0).
     *
     * @param bitIndex The index of the bit to clear (0-based).
     * @throws IndexOutOfBoundsException if bitIndex is out of range.
     */
    public void clear(long bitIndex) {
        validateBitIndex(bitIndex);
        long byteIndex = bitIndex >>> 3;
        int bitPosition = (int) (bitIndex & 7);
        long address = offset + byteIndex;

        byte currentByte = segment.get(ValueLayout.JAVA_BYTE, address);
        byte newByte = (byte) (currentByte & ~(1 << bitPosition));
        segment.set(ValueLayout.JAVA_BYTE, address, newByte);
    }

    /** Clears all bits in the bit array (sets all to 0). */
    public void clearAll() {
        if (segment != null) {
            segment.asSlice(offset, size).fill((byte) 0);
        }
    }

    /** Sets all bits in the bit array (sets all to 1). */
    public void setAll() {
        if (segment != null) {
            segment.asSlice(offset, size).fill((byte) 0xFF);
        }
    }

    /**
     * Returns the value of the bit at the specified index.
     *
     * @param bitIndex The index of the bit to get (0-based).
     * @return true if the bit is set, false otherwise.
     * @throws IndexOutOfBoundsException if bitIndex is out of range.
     */
    public boolean get(long bitIndex) {
        validateBitIndex(bitIndex);
        long byteIndex = bitIndex >>> 3;
        int bitPosition = (int) (bitIndex & 7);
        long address = offset + byteIndex;

        byte currentByte = segment.get(ValueLayout.JAVA_BYTE, address);
        return ((currentByte >>> bitPosition) & 1) != 0;
    }

    /**
     * Flips the bit at the specified index (0 becomes 1, 1 becomes 0).
     *
     * @param bitIndex The index of the bit to flip (0-based).
     * @throws IndexOutOfBoundsException if bitIndex is out of range.
     */
    public void flip(long bitIndex) {
        validateBitIndex(bitIndex);
        long byteIndex = bitIndex >>> 3;
        int bitPosition = (int) (bitIndex & 7);
        long address = offset + byteIndex;

        byte currentByte = segment.get(ValueLayout.JAVA_BYTE, address);
        byte newByte = (byte) (currentByte ^ (1 << bitPosition));
        segment.set(ValueLayout.JAVA_BYTE, address, newByte);
    }

    /**
     * Returns the number of bits set to 1 (population count).
     *
     * @return The cardinality (number of 1 bits).
     */
    public long cardinality() {
        if (segment == null) {
            return 0;
        }

        long count = 0;
        for (long i = 0; i < size; i++) {
            byte b = segment.get(ValueLayout.JAVA_BYTE, offset + i);
            count += Integer.bitCount(b & 0xFF);
        }
        return count;
    }

    /**
     * Returns the index of the next bit that is set to 1, starting from the specified index
     * (inclusive).
     *
     * @param fromIndex The index to start searching from (0-based).
     * @return The index of the next set bit, or -1 if no set bit is found.
     * @throws IndexOutOfBoundsException if fromIndex is negative.
     */
    public long nextSetBit(long fromIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex cannot be negative: " + fromIndex);
        }
        if (segment == null) {
            return -1;
        }

        long maxBitIndex = size * 8;
        if (fromIndex >= maxBitIndex) {
            return -1;
        }

        long startByteIndex = fromIndex >>> 3;
        int startBitPosition = (int) (fromIndex & 7);

        // Check the starting byte (may be partial)
        if (startBitPosition != 0) {
            byte currentByte = segment.get(ValueLayout.JAVA_BYTE, offset + startByteIndex);
            // Mask off bits before startBitPosition
            int maskedByte = (currentByte & 0xFF) & (0xFF << startBitPosition);
            if (maskedByte != 0) {
                int bitPos = Integer.numberOfTrailingZeros(maskedByte);
                return (startByteIndex << 3) + bitPos;
            }
            startByteIndex++;
        }

        // Check remaining bytes
        for (long byteIndex = startByteIndex; byteIndex < size; byteIndex++) {
            byte currentByte = segment.get(ValueLayout.JAVA_BYTE, offset + byteIndex);
            if (currentByte != 0) {
                int bitPos = Integer.numberOfTrailingZeros(currentByte & 0xFF);
                return (byteIndex << 3) + bitPos;
            }
        }

        return -1;
    }

    /**
     * Returns the index of the next bit that is set to 0, starting from the specified index
     * (inclusive).
     *
     * @param fromIndex The index to start searching from (0-based).
     * @return The index of the next clear bit, or -1 if no clear bit is found.
     * @throws IndexOutOfBoundsException if fromIndex is negative.
     */
    public long nextClearBit(long fromIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex cannot be negative: " + fromIndex);
        }
        if (segment == null) {
            return fromIndex;
        }

        long maxBitIndex = size * 8;
        if (fromIndex >= maxBitIndex) {
            return fromIndex;
        }

        long startByteIndex = fromIndex >>> 3;
        int startBitPosition = (int) (fromIndex & 7);

        // Check the starting byte (may be partial)
        if (startBitPosition != 0) {
            byte currentByte = segment.get(ValueLayout.JAVA_BYTE, offset + startByteIndex);
            // Invert and mask off bits before startBitPosition
            int maskedByte = (~currentByte & 0xFF) & (0xFF << startBitPosition);
            if (maskedByte != 0) {
                int bitPos = Integer.numberOfTrailingZeros(maskedByte);
                long bitIndex = (startByteIndex << 3) + bitPos;
                if (bitIndex < maxBitIndex) {
                    return bitIndex;
                }
            }
            startByteIndex++;
        }

        // Check remaining bytes
        for (long byteIndex = startByteIndex; byteIndex < size; byteIndex++) {
            byte currentByte = segment.get(ValueLayout.JAVA_BYTE, offset + byteIndex);
            if (currentByte != (byte) 0xFF) {
                int bitPos = Integer.numberOfTrailingZeros((~currentByte) & 0xFF);
                long bitIndex = (byteIndex << 3) + bitPos;
                if (bitIndex < maxBitIndex) {
                    return bitIndex;
                }
            }
        }

        return maxBitIndex;
    }

    /**
     * Returns the number of bits that can be stored in this bit array.
     *
     * @return The capacity in bits (size * 8).
     */
    public long size() {
        return size * 8;
    }

    /**
     * Checks if this BitSetView is empty (no bits set to 1).
     *
     * @return true if all bits are 0, false otherwise.
     */
    public boolean isEmpty() {
        return cardinality() == 0;
    }

    private void validateBitIndex(long bitIndex) {
        if (segment == null) {
            throw new IllegalStateException("BitSetView is not wrapped");
        }
        if (bitIndex < 0) {
            throw new IndexOutOfBoundsException("Bit index cannot be negative: " + bitIndex);
        }
        long maxBitIndex = size * 8;
        if (bitIndex >= maxBitIndex) {
            throw new IndexOutOfBoundsException(
                    "Bit index " + bitIndex + " out of range [0, " + maxBitIndex + ")");
        }
    }
}
