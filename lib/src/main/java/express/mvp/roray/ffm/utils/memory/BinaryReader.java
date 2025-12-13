package express.mvp.roray.ffm.utils.memory;

/**
 * An interface for reading binary data sequentially from an underlying source. This provides a
 * high-level abstraction for zero-copy decoding operations.
 */
// LE/BE are standard suffixes for endianness
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public interface BinaryReader {

    // --- Core Buffer Operations ---

    /** Gets the current read offset in bytes. */
    long position();

    /** Sets the current read offset in bytes. */
    void position(long newPosition);

    /** Skips a specified number of bytes. */
    void skip(long bytesToSkip);

    /** Gets the number of remaining readable bytes. */
    long remaining();

    // --- Primitive Reading (Big Endian - Network Byte Order) ---

    byte readByte();

    short readShortBE();

    int readIntBE();

    long readLongBE();

    float readFloatBE();

    double readDoubleBE();

    boolean readBoolean(); // Typically a single byte

    // --- Primitive Reading (Little Endian) ---

    short readShortLE();

    int readIntLE();

    long readLongLE();

    float readFloatLE();

    double readDoubleLE();

    // --- Variable-Length Integer Reading ---

    /** Reads a variable-length 32-bit integer (efficient for small numbers). */
    int readVarInt();

    /** Reads a variable-length 64-bit integer (efficient for small numbers). */
    long readVarLong();

    // --- String & Byte Array Reading (prefixed with a VarInt length) ---

    // String readString();
    void readString(Utf8View viewToPopulate);

    byte[] readBytes();

    // --- Nullable Variants (prefixed with a presence byte) ---

    Byte readNullableByte();

    Short readNullableShortBE();

    Integer readNullableIntBE();

    Long readNullableLongBE();

    Short readNullableShortLE();

    Integer readNullableIntLE();

    Long readNullableLongLE();

    Float readNullableFloatLE();

    Double readNullableDoubleLE();

    // ... etc for other types ...
    // String readNullableString();
    boolean readNullableString(Utf8View viewToPopulate);

    byte[] readNullableBytes();

    // --- Zero-Copy Bulk Reading ---

    /**
     * Reads a slice of the underlying data without copying it to the heap. This is a highly
     * efficient way to process a sub-section of the data.
     *
     * @param length The number of bytes in the slice.
     * @return A MemorySegment view of the requested data.
     */
    java.lang.foreign.MemorySegment readSegment(long length);
}
