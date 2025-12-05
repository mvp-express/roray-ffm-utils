package express.mvp.roray.utils.memory;

import java.lang.foreign.MemorySegment;

// import java.lang.foreign.MemorySegment; // Uncomment when MemorySegment is a dependency

/**
 * An interface for writing binary data sequentially to an underlying sink. This provides a
 * high-level, fluent abstraction for zero-copy encoding operations.
 */
// LE/BE are standard suffixes for endianness
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public interface BinaryWriter {

    // --- Core Buffer Operations ---

    /** Gets the current write offset in bytes. */
    long position();

    /** Sets the current write offset in bytes. */
    void position(long newPosition);

    /** Gets the number of remaining writable bytes. */
    long remaining();

    // --- Primitive Writing (Big Endian - Network Byte Order) ---

    BinaryWriter writeByte(byte value);

    BinaryWriter writeShortBE(short value);

    BinaryWriter writeIntBE(int value);

    BinaryWriter writeLongBE(long value);

    BinaryWriter writeFloatBE(float value);

    BinaryWriter writeDoubleBE(double value);

    BinaryWriter writeBoolean(boolean value);

    // --- Primitive Writing (Little Endian) ---

    BinaryWriter writeShortLE(short value);

    BinaryWriter writeIntLE(int value);

    BinaryWriter writeLongLE(long value);

    BinaryWriter writeFloatLE(float value);

    BinaryWriter writeDoubleLE(double value);

    // --- Variable-Length Integer Writing ---

    /** Writes a 32-bit integer in a variable-length format. */
    BinaryWriter writeVarInt(int value);

    /** Writes a 64-bit integer in a variable-length format. */
    BinaryWriter writeVarLong(long value);

    // --- String & Byte Array Writing (prefixed with a VarInt length) ---

    BinaryWriter writeString(String value, MemorySegment scratchBuffer);

    BinaryWriter writeBytes(byte[] value);

    // --- Nullable Variants (prefixed with a presence byte) ---

    BinaryWriter writeNullableByte(Byte value);

    BinaryWriter writeNullableShortBE(Short value);

    BinaryWriter writeNullableIntBE(Integer value);

    BinaryWriter writeNullableIntLE(Integer value);

    BinaryWriter writeNullableFloatBE(Float value);

    BinaryWriter writeNullableFloatLE(Float value);

    BinaryWriter writeNullableDoubleBE(Double value);

    BinaryWriter writeNullableDoubleLE(Double value);

    BinaryWriter writeNullableBoolean(Boolean value);

    BinaryWriter writeNullableLongLE(Long value);

    BinaryWriter writeNullableLongBE(Long value);

    BinaryWriter writeNullableString(String value, MemorySegment scratchBuffer);

    BinaryWriter writeNullableBytes(byte[] value);

    // --- Zero-Copy Bulk Writing ---

    /**
     * Writes all bytes from the source MemorySegment to this writer's underlying sink.
     *
     * @param source The MemorySegment to read from.
     * @return This writer instance for chaining.
     */
    BinaryWriter writeSegment(MemorySegment source);

    /**
     * Writes raw bytes from the source MemorySegment without a length prefix. This is useful when
     * the length is already known or stored elsewhere.
     *
     * @param source The MemorySegment to copy from.
     * @param offset Starting offset in the source segment.
     * @param length Number of bytes to copy.
     * @return This writer instance for chaining.
     */
    BinaryWriter writeSegmentRaw(MemorySegment source, long offset, long length);

    /**
     * Advances the writer's position by the given number of bytes.
     *
     * @param bytesToSkip The number of bytes to skip.
     * @return This writer instance, for chaining.
     */
    BinaryWriter skip(long bytesToSkip);
}
