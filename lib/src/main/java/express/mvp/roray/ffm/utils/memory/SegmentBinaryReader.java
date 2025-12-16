package express.mvp.roray.ffm.utils.memory;

import java.lang.foreign.MemorySegment;

/**
 * A high-performance, zero-copy BinaryReader implementation using MemorySegment.
 * This class is stateful and not thread-safe. A single instance should be used by a single
 * thread at a time. It can be reused by calling the wrap() method.
 */
public final class SegmentBinaryReader implements BinaryReader {

    private MemorySegment segment;
    private long position;

    /**
     * Creates a reader that is not yet backed by a segment. Call wrap() before use.
     */
    public SegmentBinaryReader() {
    }

    /**
     * Wraps a MemorySegment, preparing the reader for use and resetting its
     * position. This allows a
     * single reader instance to be reused for multiple segments.
     *
     * @param segment The segment to read from.
     * @return This reader instance for chaining.
     */
    public SegmentBinaryReader wrap(MemorySegment segment) {
        this.segment = segment;
        this.position = 0;
        return this;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public void setPosition(long newPosition) {
        if (newPosition < 0 || newPosition > segment.byteSize()) {
            throw new IndexOutOfBoundsException("New position is out of bounds");
        }
        this.position = newPosition;
    }

    @Override
    public void skip(long bytesToSkip) {
        setPosition(this.position + bytesToSkip);
    }

    @Override
    public long remainingBytes() {
        return segment.byteSize() - position;
    }

    @Override
    public byte readByte() {
        byte value = segment.get(Layouts.BYTE, position);
        position++;
        return value;
    }

    @Override
    public boolean readBoolean() {
        return readByte() != 0;
    }

    @Override
    public short readShortBE() {
        short value = segment.get(Layouts.SHORT_BE, position);
        position += 2;
        return value;
    }

    @Override
    public int readIntBE() {
        int value = segment.get(Layouts.INT_BE, position);
        position += 4;
        return value;
    }

    @Override
    public long readLongBE() {
        long value = segment.get(Layouts.LONG_BE, position);
        position += 8;
        return value;
    }

    @Override
    public float readFloatBE() {
        float value = segment.get(Layouts.FLOAT_BE, position);
        position += 4;
        return value;
    }

    @Override
    public double readDoubleBE() {
        double value = segment.get(Layouts.DOUBLE_BE, position);
        position += 8;
        return value;
    }

    @Override
    public short readShortLE() {
        short value = segment.get(Layouts.SHORT_LE, position);
        position += 2;
        return value;
    }

    @Override
    public int readIntLE() {
        int value = segment.get(Layouts.INT_LE, position);
        position += 4;
        return value;
    }

    @Override
    public long readLongLE() {
        long value = segment.get(Layouts.LONG_LE, position);
        position += 8;
        return value;
    }

    @Override
    public float readFloatLE() {
        float value = segment.get(Layouts.FLOAT_LE, position);
        position += 4;
        return value;
    }

    @Override
    public double readDoubleLE() {
        double value = segment.get(Layouts.DOUBLE_LE, position);
        position += 8;
        return value;
    }

    @Override
    public int readVarInt() {
        int value = 0;
        int shift = 0;
        byte b;
        do {
            b = readByte();
            value |= (b & 0x7F) << shift;
            if (shift > 28) {
                throw new IllegalStateException("VarInt is too long");
            }
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    /**
     * Decodes a 64-bit integer using the Unsigned LEB128 (Little Endian Base 128)
     * format.
     *
     * @return the decoded long value.
     * @throws IllegalStateException if the variable-length value exceeds 10 bytes.
     * @see <a href="https://en.wikipedia.org/wiki/LEB128#Unsigned_LEB128">Unsigned
     *      LEB128</a>
     */
    @Override
    public long readVarLong() {
        long value = 0;
        int shift = 0;
        byte b;
        do {
            b = readByte();
            value |= (long) (b & 0x7F) << shift;
            if (shift > 63) {
                throw new IllegalStateException("VarLong is too long");
            }
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    /**
     * Reads a VarInt-prefixed UTF-8 string into the provided Utf8View without
     * allocating any objects on the heap.
     *
     * @param viewToPopulate A reusable view object that will be configured to point
     *                       to the string
     *                       data within the reader's segment.
     */
    public void readString(Utf8View viewToPopulate) {
        if (viewToPopulate == null) {
            throw new IllegalArgumentException("Utf8View cannot be null");
        }

        int length = readVarInt();
        if (length < 0) {
            throw new IllegalArgumentException("String length cannot be negative: " + length);
        }
        if (length > remainingBytes()) {
            throw new IndexOutOfBoundsException(
                    "String length " + length + " exceeds remaining bytes " + remainingBytes());
        }

        long start = this.position;
        if (length > 0) {
            validateUtf8(start, length);
        }

        viewToPopulate.wrap(this.segment, start, length);
        this.position += length;
    }

    /**
     * Reads a nullable, presence-bit prefixed UTF-8 string.
     *
     * @param viewToPopulate A reusable view object.
     * @return true if the string was present (and the view was populated),
     *         false if the string was null.
     */
    public boolean readNullableString(Utf8View viewToPopulate) {
        if (viewToPopulate == null) {
            throw new IllegalArgumentException("Utf8View cannot be null");
        }
        if (!readBoolean()) {
            return false;
        }
        readString(viewToPopulate);
        return true;
    }

    /**
     * Reads a fixed-length prefixed UTF-8 string into the provided Utf8View without
     * allocating any objects on the heap. This method is the counterpart to {@link
     * SegmentBinaryWriter#writeStringFixedLength(String)}.
     *
     * <p>
     * <b>Wire format:</b> 4-byte big-endian length prefix followed by UTF-8 encoded
     * bytes.
     *
     * @param viewToPopulate A reusable view object that will be configured to point
     *                       to the string data within the reader's segment.
     * @throws IllegalArgumentException  if viewToPopulate is null or length is
     *                                   negative
     * @throws IndexOutOfBoundsException if string length exceeds remaining bytes
     * @see SegmentBinaryWriter#writeStringFixedLength(String)
     */
    public void readStringFixedLength(Utf8View viewToPopulate) {
        if (viewToPopulate == null) {
            throw new IllegalArgumentException("Utf8View cannot be null");
        }

        int length = readIntBE();
        if (length < 0) {
            throw new IllegalArgumentException("String length cannot be negative: " + length);
        }
        if (length > remainingBytes()) {
            throw new IndexOutOfBoundsException(
                    "String length " + length + " exceeds remaining bytes " + remainingBytes());
        }

        long start = this.position;
        if (length > 0) {
            validateUtf8(start, length);
        }

        viewToPopulate.wrap(this.segment, start, length);
        this.position += length;
    }

    /**
     * Reads a nullable, presence-bit prefixed UTF-8 string with fixed-length
     * encoding.
     *
     * @param viewToPopulate A reusable view object.
     * @return true if the string was present (and the view was populated), false if
     *         the string was null.
     * @see #readStringFixedLength(Utf8View)
     */
    public boolean readNullableStringFixedLength(Utf8View viewToPopulate) {
        if (viewToPopulate == null) {
            throw new IllegalArgumentException("Utf8View cannot be null");
        }
        if (!readBoolean()) {
            return false;
        }
        readStringFixedLength(viewToPopulate);
        return true;
    }

    @Override
    public byte[] readBytes() {
        int length = readVarInt();
        if (length == 0) {
            return new byte[0];
        }
        byte[] bytes = segment.asSlice(position, length).toArray(Layouts.BYTE);
        position += length;
        return bytes;
    }

    @Override
    public Byte readNullableByte() {
        return readBoolean() ? readByte() : null;
    }

    @Override
    public Short readNullableShortBE() {
        return readBoolean() ? readShortBE() : null;
    }

    @Override
    public Integer readNullableIntBE() {
        return readBoolean() ? readIntBE() : null;
    }

    @Override
    public Long readNullableLongBE() {
        return readBoolean() ? readLongBE() : null;
    }

    @Override
    public Short readNullableShortLE() {
        return readBoolean() ? readShortLE() : null;
    }

    @Override
    public Integer readNullableIntLE() {
        return readBoolean() ? readIntLE() : null;
    }

    @Override
    public Long readNullableLongLE() {
        return readBoolean() ? readLongLE() : null;
    }

    @Override
    public Float readNullableFloatLE() {
        return readBoolean() ? readFloatLE() : null;
    }

    @Override
    public Double readNullableDoubleLE() {
        return readBoolean() ? readDoubleLE() : null;
    }

    @Override
    public byte[] readNullableBytes() {
        return readBoolean() ? readBytes() : null;
    }

    @Override
    public MemorySegment readSegment(long length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative: " + length);
        }

        if (length > remainingBytes()) {
            throw new IndexOutOfBoundsException(
                    "Cannot read "
                            + length
                            + " bytes, only "
                            + remainingBytes()
                            + " bytes remaining at position "
                            + position);
        }

        MemorySegment slice = segment.asSlice(position, length);
        position += length;
        return slice;
    }

    private void validateUtf8(long offset, int length) {
        long pos = offset;
        long end = offset + length;

        while (pos < end) {
            int b1 = segment.get(Layouts.BYTE, pos++) & 0xFF;
            if (b1 < 0x80) {
                continue;
            }

            if ((b1 & 0xE0) == 0xC0) {
                if (pos >= end) {

                    throw new IllegalArgumentException(
                            "Malformed UTF-8: truncated 2-byte sequence");
                }
                int b2 = segment.get(Layouts.BYTE, pos++) & 0xFF;
                if ((b2 & 0xC0) != 0x80) {
                    throw new IllegalArgumentException(
                            "Malformed UTF-8: invalid continuation byte");
                }
                int codePoint = ((b1 & 0x1F) << 6) | (b2 & 0x3F);
                if (codePoint < 0x80) {
                    throw new IllegalArgumentException("Malformed UTF-8: overlong encoding");
                }
                continue;
            }

            if ((b1 & 0xF0) == 0xE0) {
                if (pos + 1 >= end) {
                    throw new IllegalArgumentException(
                            "Malformed UTF-8: truncated 3-byte sequence");
                }
                int b2 = segment.get(Layouts.BYTE, pos++) & 0xFF;
                int b3 = segment.get(Layouts.BYTE, pos++) & 0xFF;
                if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) {
                    throw new IllegalArgumentException(
                            "Malformed UTF-8: invalid continuation byte");
                }
                int codePoint = ((b1 & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
                if (codePoint < 0x800) {
                    throw new IllegalArgumentException("Malformed UTF-8: overlong encoding");
                }
                if (codePoint >= 0xD800 && codePoint <= 0xDFFF) {
                    throw new IllegalArgumentException("Malformed UTF-8: surrogate code point");
                }
                continue;
            }

            if ((b1 & 0xF8) == 0xF0) {
                if (pos + 2 >= end) {
                    throw new IllegalArgumentException(
                            "Malformed UTF-8: truncated 4-byte sequence");
                }
                int b2 = segment.get(Layouts.BYTE, pos++) & 0xFF;
                int b3 = segment.get(Layouts.BYTE, pos++) & 0xFF;
                int b4 = segment.get(Layouts.BYTE, pos++) & 0xFF;
                if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80 || (b4 & 0xC0) != 0x80) {
                    throw new IllegalArgumentException(
                            "Malformed UTF-8: invalid continuation byte");
                }
                int codePoint = ((b1 & 0x07) << 18)
                        | ((b2 & 0x3F) << 12)
                        | ((b3 & 0x3F) << 6)
                        | (b4 & 0x3F);
                if (codePoint < 0x10000 || codePoint > 0x10FFFF) {
                    throw new IllegalArgumentException("Malformed UTF-8: code point out of range");
                }
                continue;
            }

            throw new IllegalArgumentException("Malformed UTF-8: illegal start byte");
        }
    }
}
