package express.mvp.roray.utils.memory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A specialized writer for building messages with variable-length fields in a flyweight-compatible
 * format.
 *
 * <p><b>Layout:</b>
 *
 * <pre>
 * [Fixed Header]
 * [Variable Field Headers: offset:int32, length:int32 pairs]
 * [Variable Field Data]
 * </pre>
 *
 * <p><b>Zero-Copy Design:</b> This writer produces data that can be read via flyweight accessors
 * without any heap allocation or copying. Variable fields are accessed via offset/length pointers
 * stored in the header section.
 *
 * <p><b>Usage Pattern:</b>
 *
 * <pre>
 * // Setup
 * VarFieldWriter writer = new VarFieldWriter(segment, fixedHeaderSize, maxVarFields);
 *
 * // Write fixed header fields
 * writer.writeByte(0, messageType);
 * writer.writeIntLE(1, requestId);
 *
 * // Reserve slots for variable fields
 * int keySlot = writer.reserveVarField();
 * int valueSlot = writer.reserveVarField();
 *
 * // Write variable field data
 * writer.writeVarField(keySlot, keyBytes);
 * writer.writeVarField(valueSlot, valueBytes);
 *
 * // Get final message segment
 * MemorySegment message = writer.finish();
 * </pre>
 *
 * <p><b>Thread Safety:</b> Not thread-safe. Use one instance per thread.
 *
 * <p><b>Zero-GC:</b> All operations avoid heap allocation.
 */
public final class VarFieldWriter {

    private final MemorySegment segment;
    private final long fixedHeaderSize;
    private final int maxVarFields;
    private final long varHeadersOffset;
    private final long dataOffset;

    private int nextVarFieldSlot;
    private long currentDataPosition;

    /**
     * Creates a VarFieldWriter for building structured messages.
     *
     * @param segment The backing memory segment (must be large enough).
     * @param fixedHeaderSize Size of the fixed header in bytes.
     * @param maxVarFields Maximum number of variable-length fields.
     * @throws IllegalArgumentException if parameters are invalid.
     */
    public VarFieldWriter(MemorySegment segment, long fixedHeaderSize, int maxVarFields) {
        if (segment == null) {
            throw new IllegalArgumentException("Segment cannot be null");
        }
        if (fixedHeaderSize < 0) {
            throw new IllegalArgumentException(
                    "Fixed header size cannot be negative: " + fixedHeaderSize);
        }
        if (maxVarFields < 0) {
            throw new IllegalArgumentException(
                    "Max var fields cannot be negative: " + maxVarFields);
        }

        this.segment = segment;
        this.fixedHeaderSize = fixedHeaderSize;
        this.maxVarFields = maxVarFields;
        this.varHeadersOffset = fixedHeaderSize;
        this.dataOffset =
                fixedHeaderSize + (maxVarFields * 8L); // Each var field: offset(4) + length(4)

        // Validate segment is large enough for headers
        if (dataOffset > segment.byteSize()) {
            throw new IllegalArgumentException(
                    "Segment too small: need at least "
                            + dataOffset
                            + " bytes for headers, got "
                            + segment.byteSize());
        }

        this.nextVarFieldSlot = 0;
        this.currentDataPosition = dataOffset;
    }

    // --- Fixed Header Writing ---

    /**
     * Writes a byte to the fixed header at the specified offset.
     *
     * @param offset Offset within the fixed header (0 to fixedHeaderSize-1).
     * @param value The byte value to write.
     * @throws IndexOutOfBoundsException if offset is out of bounds.
     */
    public void writeByte(long offset, byte value) {
        validateFixedHeaderOffset(offset, 1);
        segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, offset, value);
    }

    /** Writes a short (16-bit) to the fixed header in little-endian format. */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public void writeShortLE(long offset, short value) {
        validateFixedHeaderOffset(offset, 2);
        segment.set(
                java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(
                        java.nio.ByteOrder.LITTLE_ENDIAN),
                offset,
                value);
    }

    /** Writes an int (32-bit) to the fixed header in little-endian format. */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public void writeIntLE(long offset, int value) {
        validateFixedHeaderOffset(offset, 4);
        segment.set(
                java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED.withOrder(
                        java.nio.ByteOrder.LITTLE_ENDIAN),
                offset,
                value);
    }

    /** Writes a long (64-bit) to the fixed header in little-endian format. */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public void writeLongLE(long offset, long value) {
        validateFixedHeaderOffset(offset, 8);
        segment.set(
                java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED.withOrder(
                        java.nio.ByteOrder.LITTLE_ENDIAN),
                offset,
                value);
    }

    /** Writes a float to the fixed header in little-endian format. */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public void writeFloatLE(long offset, float value) {
        validateFixedHeaderOffset(offset, 4);
        segment.set(
                java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(
                        java.nio.ByteOrder.LITTLE_ENDIAN),
                offset,
                value);
    }

    /** Writes a double to the fixed header in little-endian format. */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public void writeDoubleLE(long offset, double value) {
        validateFixedHeaderOffset(offset, 8);
        segment.set(
                java.lang.foreign.ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(
                        java.nio.ByteOrder.LITTLE_ENDIAN),
                offset,
                value);
    }

    /** Writes a boolean to the fixed header as a single byte (0 or 1). */
    public void writeBoolean(long offset, boolean value) {
        writeByte(offset, value ? (byte) 1 : (byte) 0);
    }

    // --- Variable Field Management ---

    /**
     * Reserves a slot for a variable-length field.
     *
     * @return The slot index (use this with writeVarField).
     * @throws IllegalStateException if all slots are exhausted.
     */
    public int reserveVarField() {
        if (nextVarFieldSlot >= maxVarFields) {
            throw new IllegalStateException(
                    "Cannot reserve more than " + maxVarFields + " variable fields");
        }
        return nextVarFieldSlot++;
    }

    /**
     * Writes data to a reserved variable field slot.
     *
     * @param slot The slot index from reserveVarField().
     * @param data The byte array containing the data.
     * @throws IllegalArgumentException if slot is invalid or data is null.
     * @throws IndexOutOfBoundsException if segment is too small.
     */
    public void writeVarField(int slot, byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        writeVarFieldInternal(slot, data, 0, data.length);
    }

    /**
     * Writes a portion of a byte array to a reserved variable field slot.
     *
     * @param slot The slot index from reserveVarField().
     * @param data The byte array containing the data.
     * @param offset Starting offset in the data array.
     * @param length Number of bytes to write.
     */
    public void writeVarField(int slot, byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException(
                    "Invalid range: offset="
                            + offset
                            + ", length="
                            + length
                            + ", data.length="
                            + data.length);
        }
        writeVarFieldInternal(slot, data, offset, length);
    }

    /**
     * Writes a UTF-8 string to a reserved variable field slot using the provided scratch buffer for
     * encoding. The scratch buffer is reused, avoiding any heap allocation while encoding the
     * string.
     *
     * @param slot The slot index from {@link #reserveVarField()}.
     * @param str The string to encode and write.
     * @param scratchBuffer A reusable off-heap buffer large enough to hold the UTF-8 bytes.
     */
    public void writeVarField(int slot, String str, MemorySegment scratchBuffer) {
        if (str == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        if (scratchBuffer == null) {
            throw new IllegalArgumentException("Scratch buffer cannot be null");
        }

        try {
            int bytesWritten = encodeUtf8(str, scratchBuffer);
            writeVarField(slot, scratchBuffer, 0, bytesWritten);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(
                    "Scratch buffer too small for string: " + str.length() + " chars", e);
        }
    }

    /**
     * Writes a MemorySegment to a reserved variable field slot.
     *
     * @param slot The slot index from reserveVarField().
     * @param source The source segment to copy from.
     */
    public void writeVarField(int slot, MemorySegment source) {
        if (source == null) {
            throw new IllegalArgumentException("Source segment cannot be null");
        }
        writeVarFieldFromSegment(slot, source, 0, source.byteSize());
    }

    /**
     * Writes a portion of a MemorySegment to a reserved variable field slot.
     *
     * @param slot The slot index from reserveVarField().
     * @param source The source segment to copy from.
     * @param offset Starting offset in the source segment.
     * @param length Number of bytes to copy.
     */
    public void writeVarField(int slot, MemorySegment source, long offset, long length) {
        if (source == null) {
            throw new IllegalArgumentException("Source segment cannot be null");
        }
        if (offset < 0 || length < 0 || offset + length > source.byteSize()) {
            throw new IllegalArgumentException(
                    "Invalid range: offset="
                            + offset
                            + ", length="
                            + length
                            + ", source.byteSize="
                            + source.byteSize());
        }
        writeVarFieldFromSegment(slot, source, offset, length);
    }

    // --- Internal Helpers ---

    private void validateFixedHeaderOffset(long offset, long size) {
        if (offset < 0 || offset + size > fixedHeaderSize) {
            throw new IndexOutOfBoundsException(
                    "Fixed header access out of bounds: offset="
                            + offset
                            + ", size="
                            + size
                            + ", fixedHeaderSize="
                            + fixedHeaderSize);
        }
    }

    private void writeVarFieldInternal(int slot, byte[] data, int offset, int length) {
        validateSlot(slot);

        // Check if segment has enough space for the data
        long dataEnd = currentDataPosition + length;
        if (dataEnd > segment.byteSize()) {
            throw new IndexOutOfBoundsException(
                    "Segment too small: need " + dataEnd + " bytes, have " + segment.byteSize());
        }

        // Copy data to the data section
        MemorySegment.copy(
                MemorySegment.ofArray(data), offset, segment, currentDataPosition, length);

        // Write offset/length header using big-endian layout to match flyweights
        long headerOffset = varHeadersOffset + (slot * 8L);
        segment.set(Layouts.INT_BE, headerOffset, (int) currentDataPosition);
        segment.set(Layouts.INT_BE, headerOffset + 4, length);

        // Advance data position
        currentDataPosition += length;
    }

    private void writeVarFieldFromSegment(
            int slot, MemorySegment source, long offset, long length) {
        validateSlot(slot);

        // Check if segment has enough space for the data
        long dataEnd = currentDataPosition + length;
        if (dataEnd > segment.byteSize()) {
            throw new IndexOutOfBoundsException(
                    "Segment too small: need " + dataEnd + " bytes, have " + segment.byteSize());
        }

        // Copy data to the data section
        MemorySegment sourceSlice = source.asSlice(offset, length);
        MemorySegment.copy(sourceSlice, 0, segment, currentDataPosition, length);

        // Write offset/length header using big-endian layout to align with flyweight reads
        long headerOffset = varHeadersOffset + (slot * 8L);
        segment.set(Layouts.INT_BE, headerOffset, (int) currentDataPosition);
        segment.set(Layouts.INT_BE, headerOffset + 4, (int) length);

        // Advance data position
        currentDataPosition += length;
    }

    /**
     * Begin streaming bytes for a nested or composite field directly into the backing segment. Call
     * {@link NestedFieldHandle#finish(long)} once all bytes have been written so offsets and
     * lengths can be committed to the header. The caller is responsible for ensuring bytes are
     * written sequentially starting at the absolute offset provided by the handle and for never
     * overlapping concurrent nested writes.
     */
    public NestedFieldHandle beginNestedField(int slot) {
        validateSlot(slot);
        long start = currentDataPosition;
        return new NestedFieldHandle(slot, start, this);
    }

    private void commitNestedField(NestedFieldHandle handle, long length) {
        long headerOffset = varHeadersOffset + (handle.slot * 8L);
        segment.set(Layouts.INT_BE, headerOffset, (int) handle.startOffset);
        segment.set(Layouts.INT_BE, headerOffset + 4, (int) length);
        currentDataPosition = handle.startOffset + length;
    }

    /** A handle for writing nested or composite fields directly into the backing segment. */
    public static final class NestedFieldHandle {
        private final int slot;
        private final long startOffset;
        private final VarFieldWriter owner;
        private boolean finished;

        private NestedFieldHandle(int slot, long startOffset, VarFieldWriter owner) {
            this.slot = slot;
            this.startOffset = startOffset;
            this.owner = owner;
        }

        /**
         * Returns the relative offset where this nested field starts.
         *
         * @return the starting offset
         */
        public long relativeOffset() {
            return startOffset;
        }

        /**
         * Finalize the nested field by writing the length into the reserved header slot.
         *
         * @param length number of bytes written for this nested field
         */
        public void finish(long length) {
            if (finished) {
                throw new IllegalStateException("Nested field already finished");
            }
            owner.commitNestedField(this, length);
            finished = true;
        }
    }

    private void validateSlot(int slot) {
        if (slot < 0 || slot >= nextVarFieldSlot) {
            throw new IllegalArgumentException(
                    "Invalid slot: "
                            + slot
                            + " (must be between 0 and "
                            + (nextVarFieldSlot - 1)
                            + ")");
        }
    }

    /**
     * Calculates the UTF-8 encoded byte length of a string.
     *
     * @param value the string to measure
     * @return the number of bytes required for UTF-8 encoding
     */
    public static int utf8Length(String value) {
        int byteLength = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x80) {
                byteLength += 1;
            } else if (c < 0x800) {
                byteLength += 2;
            } else if (Character.isSurrogate(c)) {
                if (i + 1 >= value.length()) {
                    throw new IllegalArgumentException("Unpaired surrogate at index " + i);
                }
                byteLength += 4;
                i++; // Skip the low surrogate
            } else {
                byteLength += 3;
            }
        }
        return byteLength;
    }

    /**
     * Encodes a string as UTF-8 directly into a memory segment.
     *
     * @param value the string to encode
     * @param target the target segment to write to
     * @return the number of bytes written
     * @throws IndexOutOfBoundsException if the target is too small
     */
    public static int encodeUtf8(String value, MemorySegment target) {
        long pos = 0;
        long limit = target.byteSize();
        int len = value.length();

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (c < 0x80) {
                if (pos >= limit) {
                    throw new IndexOutOfBoundsException();
                }
                target.set(ValueLayout.JAVA_BYTE, pos++, (byte) c);
            } else if (c < 0x800) {
                if (pos + 2 > limit) {
                    throw new IndexOutOfBoundsException();
                }
                target.set(ValueLayout.JAVA_BYTE, pos++, (byte) (0xC0 | (c >> 6)));
                target.set(ValueLayout.JAVA_BYTE, pos++, (byte) (0x80 | (c & 0x3F)));
            } else if (Character.isSurrogate(c)) {
                if (pos + 4 > limit) {
                    throw new IndexOutOfBoundsException();
                }
                if (i + 1 >= len) {
                    throw new IllegalArgumentException("Unpaired surrogate at index " + i);
                }
                int codePoint = Character.toCodePoint(c, value.charAt(++i));
                target.set(ValueLayout.JAVA_BYTE, pos++, (byte) (0xF0 | (codePoint >> 18)));
                target.set(
                        ValueLayout.JAVA_BYTE, pos++, (byte) (0x80 | ((codePoint >> 12) & 0x3F)));
                target.set(ValueLayout.JAVA_BYTE, pos++, (byte) (0x80 | ((codePoint >> 6) & 0x3F)));
                target.set(ValueLayout.JAVA_BYTE, pos++, (byte) (0x80 | (codePoint & 0x3F)));
            } else {
                if (pos + 3 > limit) {
                    throw new IndexOutOfBoundsException();
                }
                target.set(ValueLayout.JAVA_BYTE, pos++, (byte) (0xE0 | (c >> 12)));
                target.set(ValueLayout.JAVA_BYTE, pos++, (byte) (0x80 | ((c >> 6) & 0x3F)));
                target.set(ValueLayout.JAVA_BYTE, pos++, (byte) (0x80 | (c & 0x3F)));
            }
        }
        return (int) pos;
    }

    // --- Finalization ---

    /**
     * Returns the final message segment with all data written. The returned segment is a slice from
     * offset 0 to the end of the last written data.
     *
     * @return A MemorySegment view of the complete message.
     */
    public MemorySegment finish() {
        return segment.asSlice(0, currentDataPosition);
    }

    /**
     * Returns the total number of bytes written (headers + data).
     *
     * @return The byte size of the complete message.
     */
    public long bytesWritten() {
        return currentDataPosition;
    }

    /**
     * Returns the number of variable fields written.
     *
     * @return The count of variable fields.
     */
    public int varFieldCount() {
        return nextVarFieldSlot;
    }

    /**
     * Resets the writer to start building a new message. The backing segment is reused. Fixed
     * header size and max var fields remain unchanged.
     */
    public void reset() {
        nextVarFieldSlot = 0;
        currentDataPosition = dataOffset;
    }

    /**
     * Returns the offset where variable field data begins. Useful for debugging or advanced use
     * cases.
     *
     * @return The data section offset.
     */
    public long dataOffset() {
        return dataOffset;
    }
}
