package express.mvp.roray.utils.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive round-trip tests for SegmentBinaryWriter and SegmentBinaryReader. It ensures that
 * data written by the writer is read back correctly by the reader.
 */
@SuppressWarnings({
    "checkstyle:VariableDeclarationUsageDistance",
    "checkstyle:AvoidEscapedUnicodeCharacters"
}) // Test patterns require these
class SegmentBinaryReaderWriterTest {

    private Arena arena;
    private MemorySegment segment;
    private MemorySegment scratchBuffer; // For HFT string writing
    private SegmentBinaryWriter writer;
    private SegmentBinaryReader reader;
    private Utf8View utf8View; // Reusable view for HFT string reading

    @BeforeEach
    void setUp() {
        // Setup all reusable components before each test
        arena = Arena.ofConfined();
        segment = arena.allocate(8192); // 8KB main buffer for testing
        scratchBuffer = arena.allocate(8192); // 8KB scratch buffer
        writer = new SegmentBinaryWriter();
        reader = new SegmentBinaryReader();
        utf8View = new Utf8View();
    }

    @AfterEach
    void tearDown() {
        // Ensure off-heap memory is always released after each test
        arena.close();
    }

    // =================================================================
    // Primitive Round-trip Tests
    // =================================================================

    @Test
    void testPrimitives_Roundtrip_BigEndian() {
        writer.wrap(segment)
                .writeByte((byte) -10)
                .writeShortBE((short) -30000)
                .writeIntBE(Integer.MAX_VALUE)
                .writeLongBE(Long.MIN_VALUE)
                .writeFloatBE(-123.45f)
                .writeDoubleBE(9876.54321)
                .writeBoolean(true)
                .writeBoolean(false);

        reader.wrap(segment);
        assertEquals((byte) -10, reader.readByte());
        assertEquals((short) -30000, reader.readShortBE());
        assertEquals(Integer.MAX_VALUE, reader.readIntBE());
        assertEquals(Long.MIN_VALUE, reader.readLongBE());
        assertEquals(-123.45f, reader.readFloatBE());
        assertEquals(9876.54321, reader.readDoubleBE());
        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
    }

    @Test
    void testPrimitives_Roundtrip_LittleEndian() {
        writer.wrap(segment)
                .writeShortLE((short) 12345)
                .writeIntLE(-98765)
                .writeLongLE(1234567890123L)
                .writeFloatLE(Float.MIN_VALUE)
                .writeDoubleLE(Double.MAX_VALUE);

        reader.wrap(segment);
        assertEquals((short) 12345, reader.readShortLE());
        assertEquals(-98765, reader.readIntLE());
        assertEquals(1234567890123L, reader.readLongLE());
        assertEquals(Float.MIN_VALUE, reader.readFloatLE());
        assertEquals(Double.MAX_VALUE, reader.readDoubleLE());
    }

    @Test
    void testVarIntAndVarLong_Roundtrip() {
        writer.wrap(segment)
                .writeVarInt(0)
                .writeVarInt(127) // 1 byte
                .writeVarInt(500) // 2 bytes
                .writeVarInt(Integer.MAX_VALUE) // 5 bytes
                .writeVarLong(0L)
                .writeVarLong(123456789L);

        reader.wrap(segment);
        assertEquals(0, reader.readVarInt());
        assertEquals(127, reader.readVarInt());
        assertEquals(500, reader.readVarInt());
        assertEquals(Integer.MAX_VALUE, reader.readVarInt());
        assertEquals(0L, reader.readVarLong());
        assertEquals(123456789L, reader.readVarLong());
    }

    @Test
    void testVarIntFast_Roundtrip() {
        // Test various value ranges that exercise different byte counts
        writer.wrap(segment);
        writer.writeVarIntFast(0); // 1 byte
        writer.writeVarIntFast(1); // 1 byte
        writer.writeVarIntFast(127); // 1 byte (max single byte)
        writer.writeVarIntFast(128); // 2 bytes (min two bytes)
        writer.writeVarIntFast(16383); // 2 bytes (max two bytes)
        writer.writeVarIntFast(16384); // 3 bytes
        writer.writeVarIntFast(2097151); // 3 bytes (max three bytes)
        writer.writeVarIntFast(2097152); // 4 bytes
        writer.writeVarIntFast(268435455); // 4 bytes (max four bytes)
        writer.writeVarIntFast(268435456); // 5 bytes
        writer.writeVarIntFast(Integer.MAX_VALUE); // 5 bytes

        reader.wrap(segment);
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(127, reader.readVarInt());
        assertEquals(128, reader.readVarInt());
        assertEquals(16383, reader.readVarInt());
        assertEquals(16384, reader.readVarInt());
        assertEquals(2097151, reader.readVarInt());
        assertEquals(2097152, reader.readVarInt());
        assertEquals(268435455, reader.readVarInt());
        assertEquals(268435456, reader.readVarInt());
        assertEquals(Integer.MAX_VALUE, reader.readVarInt());
    }

    @Test
    void testVarIntFast_MatchesVarInt() {
        // Verify that writeVarIntFast produces identical output to writeVarInt
        int[] testValues = {
            0,
            1,
            63,
            64,
            127,
            128,
            255,
            256,
            1000,
            10000,
            100000,
            1000000,
            10000000,
            100000000,
            Integer.MAX_VALUE
        };

        for (int value : testValues) {
            // Write with standard method
            writer.wrap(segment);
            writer.writeVarInt(value);
            long standardLength = writer.position();
            byte[] standardBytes = new byte[(int) standardLength];
            for (int i = 0; i < standardLength; i++) {
                standardBytes[i] = segment.get(Layouts.BYTE, i);
            }

            // Write with fast method
            writer.wrap(segment);
            writer.writeVarIntFast(value);
            long fastLength = writer.position();
            byte[] fastBytes = new byte[(int) fastLength];
            for (int i = 0; i < fastLength; i++) {
                fastBytes[i] = segment.get(Layouts.BYTE, i);
            }

            assertEquals(standardLength, fastLength, "Length mismatch for value " + value);
            assertArrayEquals(standardBytes, fastBytes, "Bytes mismatch for value " + value);
        }
    }

    @Test
    void testVarLongFast_Roundtrip() {
        writer.wrap(segment);
        writer.writeVarLongFast(0L);
        writer.writeVarLongFast(127L);
        writer.writeVarLongFast(128L);
        writer.writeVarLongFast(16384L);
        writer.writeVarLongFast(123456789L);
        writer.writeVarLongFast(Long.MAX_VALUE / 2);
        writer.writeVarLongFast(Long.MAX_VALUE);

        reader.wrap(segment);
        assertEquals(0L, reader.readVarLong());
        assertEquals(127L, reader.readVarLong());
        assertEquals(128L, reader.readVarLong());
        assertEquals(16384L, reader.readVarLong());
        assertEquals(123456789L, reader.readVarLong());
        assertEquals(Long.MAX_VALUE / 2, reader.readVarLong());
        assertEquals(Long.MAX_VALUE, reader.readVarLong());
    }

    @Test
    void testVarLongFast_MatchesVarLong() {
        long[] testValues = {
            0L,
            1L,
            127L,
            128L,
            16383L,
            16384L,
            2097151L,
            2097152L,
            123456789L,
            9876543210L,
            Long.MAX_VALUE / 256,
            Long.MAX_VALUE
        };

        for (long value : testValues) {
            writer.wrap(segment);
            writer.writeVarLong(value);
            long standardLength = writer.position();
            byte[] standardBytes = new byte[(int) standardLength];
            for (int i = 0; i < standardLength; i++) {
                standardBytes[i] = segment.get(Layouts.BYTE, i);
            }

            writer.wrap(segment);
            writer.writeVarLongFast(value);
            long fastLength = writer.position();
            byte[] fastBytes = new byte[(int) fastLength];
            for (int i = 0; i < fastLength; i++) {
                fastBytes[i] = segment.get(Layouts.BYTE, i);
            }

            assertEquals(standardLength, fastLength, "Length mismatch for value " + value);
            assertArrayEquals(standardBytes, fastBytes, "Bytes mismatch for value " + value);
        }
    }

    // =================================================================
    // Array, String, and Segment Round-trip Tests
    // =================================================================

    @Test
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName") // HFT is intentional abbreviation
    void testBytesAndString_HFT_Roundtrip() {
        byte[] originalBytes = new byte[] {1, 2, 3, 4, 5};
        String asciiStr = "Hello, Roray-FFM-Utils!";
        String multiByteStr = "你好, world!";
        String emojiStr = "🚀"; // Surrogate pair
        String emptyStr = "";

        writer.wrap(segment)
                .writeBytes(originalBytes)
                .writeString(asciiStr, scratchBuffer)
                .writeString(multiByteStr, scratchBuffer)
                .writeString(emojiStr, scratchBuffer)
                .writeString(emptyStr, scratchBuffer);

        reader.wrap(segment);
        assertArrayEquals(originalBytes, reader.readBytes());

        reader.readString(utf8View);
        assertTrue(utf8View.equalsString(asciiStr));

        reader.readString(utf8View);
        assertTrue(utf8View.equalsString(multiByteStr));

        reader.readString(utf8View);
        assertTrue(utf8View.equalsString(emojiStr));

        reader.readString(utf8View);
        assertTrue(utf8View.equalsString(emptyStr));
    }

    @Test
    void testWriteBytes_EmptyArrayRoundtrip() {
        byte[] empty = new byte[0];

        writer.wrap(segment).writeBytes(empty);

        reader.wrap(segment);
        assertArrayEquals(empty, reader.readBytes());
    }

    @Test
    void testWriteString_NullValueThrows() {
        writer.wrap(segment);
        assertThrows(IllegalArgumentException.class, () -> writer.writeString(null, scratchBuffer));
    }

    @Test
    void testWriteString_NullScratchThrows() {
        writer.wrap(segment);
        assertThrows(IllegalArgumentException.class, () -> writer.writeString("data", null));
    }

    @Test
    void testWriteString_ScratchTooSmallAsciiThrows() {
        writer.wrap(segment);
        MemorySegment tinyScratch = arena.allocate(2);
        assertThrows(IllegalArgumentException.class, () -> writer.writeString("abc", tinyScratch));
    }

    @Test
    void testWriteString_ScratchTooSmallMultiByteThrows() {
        writer.wrap(segment);
        MemorySegment tinyScratch = arena.allocate(3);
        assertThrows(IllegalArgumentException.class, () -> writer.writeString("你好", tinyScratch));
    }

    @Test
    void testWriteString_ScratchTooSmallEmojiThrows() {
        writer.wrap(segment);
        MemorySegment tinyScratch = arena.allocate(3);
        assertThrows(IllegalArgumentException.class, () -> writer.writeString("🚀", tinyScratch));
    }

    @Test
    void testWriteString_UnpairedSurrogatesThrow() {
        writer.wrap(segment);
        assertThrows(
                IllegalArgumentException.class, () -> writer.writeString("\uD83D", scratchBuffer));
        assertThrows(
                IllegalArgumentException.class, () -> writer.writeString("\uDC00", scratchBuffer));
    }

    @Test
    void testReadString_NullViewThrows() {
        reader.wrap(segment);
        assertThrows(IllegalArgumentException.class, () -> reader.readString(null));
    }

    @Test
    void testReadString_LengthExceedsRemainingThrows() {
        MemorySegment tinySegment = arena.allocate(3);
        SegmentBinaryWriter tempWriter = new SegmentBinaryWriter();
        tempWriter.wrap(tinySegment).writeVarInt(5);

        SegmentBinaryReader tempReader = new SegmentBinaryReader();
        tempReader.wrap(tinySegment);
        Utf8View tempView = new Utf8View();

        assertThrows(IndexOutOfBoundsException.class, () -> tempReader.readString(tempView));
    }

    @Test
    void testReadString_InvalidUtf8SequencesThrow() {
        assertReadStringFails((byte) 0xC2); // Truncated 2-byte sequence
        assertReadStringFails((byte) 0xC2, (byte) 0x41); // Invalid continuation
        assertReadStringFails((byte) 0xC0, (byte) 0x80); // Overlong encoding of ASCII NUL
        assertReadStringFails((byte) 0xED, (byte) 0xA0, (byte) 0x80); // Surrogate code point
        assertReadStringFails((byte) 0xE0, (byte) 0x80); // Truncated 3-byte sequence
        assertReadStringFails((byte) 0xF0, (byte) 0x80, (byte) 0x80); // Truncated 4-byte sequence
        assertReadStringFails(
                (byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80); // Code point above U+10FFFF
        assertReadStringFails((byte) 0xF8); // Illegal start byte
    }

    @Test
    void testReadNullableString_LeavesViewUntouchedOnNull() {
        writer.wrap(segment).writeBoolean(false);

        utf8View.wrap(segment, 123, 3);

        reader.wrap(segment);
        assertFalse(reader.readNullableString(utf8View));
        assertEquals(segment, utf8View.segment());
        assertEquals(123, utf8View.offset());
        assertEquals(3, utf8View.byteSize());
    }

    @Test
    void testSegment_Roundtrip() {
        MemorySegment sourceSegment = arena.allocate(16);
        sourceSegment.set(ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN), 0, 12345L);
        sourceSegment.set(ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN), 8, 67890L);

        writer.wrap(segment).writeSegment(sourceSegment);

        reader.wrap(segment);
        byte[] readBytes = reader.readBytes(); // readBytes reads a VarLong prefix now

        assertArrayEquals(sourceSegment.toArray(ValueLayout.JAVA_BYTE), readBytes);
    }

    @Test
    void testReadSegment_ReturnsSlice() {
        writer.wrap(segment)
                .writeByte((byte) 0x11)
                .writeByte((byte) 0x22)
                .writeByte((byte) 0x33)
                .writeByte((byte) 0x44);

        reader.wrap(segment);
        MemorySegment slice = reader.readSegment(4);

        assertEquals((byte) 0x11, slice.get(ValueLayout.JAVA_BYTE, 0));
        assertEquals((byte) 0x44, slice.get(ValueLayout.JAVA_BYTE, 3));
        assertEquals(4, reader.position());
    }

    @Test
    void testReadSegment_NegativeLengthThrows() {
        reader.wrap(segment);
        assertThrows(IllegalArgumentException.class, () -> reader.readSegment(-1));
    }

    @Test
    void testReadSegment_LengthExceedsRemainingThrows() {
        reader.wrap(segment);
        reader.position(segment.byteSize() - 4);
        assertThrows(IndexOutOfBoundsException.class, () -> reader.readSegment(8));
    }

    @Test
    void testWriteSegmentRaw_InvalidRangeThrows() {
        MemorySegment source = arena.allocate(8);
        writer.wrap(segment);
        assertThrows(IllegalArgumentException.class, () -> writer.writeSegmentRaw(source, 6, 4));
    }

    @Test
    void testWriteSegmentRaw_TargetOverflowThrows() {
        MemorySegment source = arena.allocate(8);
        writer.wrap(segment);
        writer.position(segment.byteSize() - 2);
        assertThrows(IndexOutOfBoundsException.class, () -> writer.writeSegmentRaw(source, 0, 8));
    }

    // =================================================================
    // Nullable Round-trip Tests
    // =================================================================

    @Test
    void testNullableValues_Roundtrip() {
        writer.wrap(segment)
                // Present values
                .writeNullableIntBE(123)
                .writeNullableString("I am here", scratchBuffer)
                // Null values
                .writeNullableIntBE(null)
                .writeNullableString(null, scratchBuffer)
                .writeNullableBytes(null);

        reader.wrap(segment);
        assertEquals(123, reader.readNullableIntBE());

        assertTrue(reader.readNullableString(utf8View));
        assertTrue(utf8View.equalsString("I am here"));

        assertNull(reader.readNullableIntBE());

        assertFalse(reader.readNullableString(utf8View)); // Should return false for null

        assertNull(reader.readNullableBytes());
    }

    @Test
    void testNullableNumericVariants_Roundtrip() {
        byte[] optionalBytes = new byte[] {9, 8, 7};

        writer.wrap(segment)
                .writeNullableByte((byte) 42)
                .writeNullableByte(null)
                .writeNullableShortBE((short) -32000)
                .writeNullableShortBE(null)
                .writeNullableIntBE(1_234_567)
                .writeNullableIntBE(null)
                .writeNullableLongBE(-9_876_543_210L)
                .writeNullableLongBE(null)
                .writeNullableIntLE(654_321)
                .writeNullableIntLE(null)
                .writeNullableLongLE(9_223_372_036_854_775_000L)
                .writeNullableLongLE(null)
                .writeNullableFloatLE(123.25f)
                .writeNullableFloatLE(null)
                .writeNullableDoubleLE(-456.75d)
                .writeNullableDoubleLE(null)
                .writeNullableBytes(optionalBytes)
                .writeNullableBytes(null);

        reader.wrap(segment);

        assertEquals(Byte.valueOf((byte) 42), reader.readNullableByte());
        assertNull(reader.readNullableByte());

        assertEquals(Short.valueOf((short) -32000), reader.readNullableShortBE());
        assertNull(reader.readNullableShortBE());

        assertEquals(Integer.valueOf(1_234_567), reader.readNullableIntBE());
        assertNull(reader.readNullableIntBE());

        assertEquals(Long.valueOf(-9_876_543_210L), reader.readNullableLongBE());
        assertNull(reader.readNullableLongBE());

        assertEquals(Integer.valueOf(654_321), reader.readNullableIntLE());
        assertNull(reader.readNullableIntLE());

        assertEquals(Long.valueOf(9_223_372_036_854_775_000L), reader.readNullableLongLE());
        assertNull(reader.readNullableLongLE());

        assertEquals(Float.valueOf(123.25f), reader.readNullableFloatLE());
        assertNull(reader.readNullableFloatLE());

        assertEquals(Double.valueOf(-456.75d), reader.readNullableDoubleLE());
        assertNull(reader.readNullableDoubleLE());

        assertArrayEquals(optionalBytes, reader.readNullableBytes());
        assertNull(reader.readNullableBytes());
    }

    // =================================================================
    // Buffer Manipulation Tests
    // =================================================================

    @Test
    void testPositionAndSkip() {
        writer.wrap(segment).writeIntBE(111).writeIntBE(222).writeIntBE(333);

        assertEquals(12, writer.position());

        reader.wrap(segment);
        assertEquals(111, reader.readIntBE());

        reader.skip(4); // Skip the '222' value
        assertEquals(8, reader.position());

        assertEquals(333, reader.readIntBE());
    }

    private void assertReadStringFails(byte... payload) {
        SegmentBinaryWriter tempWriter = new SegmentBinaryWriter();
        SegmentBinaryReader tempReader = new SegmentBinaryReader();
        Utf8View tempView = new Utf8View();

        MemorySegment localSegment = arena.allocate(Math.max(8, payload.length + 8));
        tempWriter.wrap(localSegment).writeVarInt(payload.length);
        long payloadOffset = tempWriter.position();
        for (int i = 0; i < payload.length; i++) {
            localSegment.set(ValueLayout.JAVA_BYTE, payloadOffset + i, payload[i]);
        }

        tempReader.wrap(localSegment);
        assertThrows(IllegalArgumentException.class, () -> tempReader.readString(tempView));
    }

    // =================================================================
    // Fixed-Length String Tests (Single-Pass Encoding)
    // =================================================================

    @Test
    void testStringFixedLength_AsciiRoundtrip() {
        String asciiStr = "Hello, Roray-FFM-Utils!";

        writer.wrap(segment).writeStringFixedLength(asciiStr);

        // Verify wire format: 4-byte length prefix + UTF-8 bytes
        assertEquals(4 + asciiStr.length(), writer.position());

        reader.wrap(segment);
        reader.readStringFixedLength(utf8View);
        assertTrue(utf8View.equalsString(asciiStr));
    }

    @Test
    void testStringFixedLength_EmptyStringRoundtrip() {
        String emptyStr = "";

        writer.wrap(segment).writeStringFixedLength(emptyStr);

        // Verify wire format: 4-byte length prefix (value=0) + no bytes
        assertEquals(4, writer.position());

        reader.wrap(segment);
        reader.readStringFixedLength(utf8View);
        assertTrue(utf8View.equalsString(emptyStr));
        assertEquals(0, utf8View.byteSize());
    }

    @Test
    void testStringFixedLength_MultiByteRoundtrip() {
        String multiByteStr = "你好, world!";

        writer.wrap(segment).writeStringFixedLength(multiByteStr);

        reader.wrap(segment);
        reader.readStringFixedLength(utf8View);
        assertTrue(utf8View.equalsString(multiByteStr));
    }

    @Test
    void testStringFixedLength_EmojiSurrogatePairRoundtrip() {
        String emojiStr = "🚀";

        writer.wrap(segment).writeStringFixedLength(emojiStr);

        // Verify wire format: 4-byte prefix + 4 bytes for surrogate pair UTF-8
        assertEquals(8, writer.position());

        reader.wrap(segment);
        reader.readStringFixedLength(utf8View);
        assertTrue(utf8View.equalsString(emojiStr));
    }

    @Test
    void testStringFixedLength_MixedContentRoundtrip() {
        String mixedStr = "ASCII, 日本語, 🎉 emoji, and ñ accents";

        writer.wrap(segment).writeStringFixedLength(mixedStr);

        reader.wrap(segment);
        reader.readStringFixedLength(utf8View);
        assertTrue(utf8View.equalsString(mixedStr));
    }

    @Test
    void testStringFixedLength_NullValueThrows() {
        writer.wrap(segment);
        assertThrows(IllegalArgumentException.class, () -> writer.writeStringFixedLength(null));
    }

    @Test
    void testStringFixedLength_UnpairedHighSurrogateThrows() {
        writer.wrap(segment);
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.writeStringFixedLength("\uD83D")); // High surrogate without low
    }

    @Test
    void testStringFixedLength_UnpairedLowSurrogateThrows() {
        writer.wrap(segment);
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.writeStringFixedLength("\uDC00")); // Low surrogate without high
    }

    @Test
    void testStringFixedLength_BufferOverflowThrows() {
        MemorySegment tinySegment = arena.allocate(6); // Only room for 4-byte prefix + 2 bytes
        SegmentBinaryWriter tempWriter = new SegmentBinaryWriter();
        tempWriter.wrap(tinySegment);

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> tempWriter.writeStringFixedLength("hello")); // Needs 5 bytes for data
    }

    @Test
    void testStringFixedLength_MultiByteBufferOverflowThrows() {
        MemorySegment tinySegment = arena.allocate(6); // Only room for 4-byte prefix + 2 bytes
        SegmentBinaryWriter tempWriter = new SegmentBinaryWriter();
        tempWriter.wrap(tinySegment);

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> tempWriter.writeStringFixedLength("日")); // Needs 3 bytes for 日
    }

    @Test
    void testReadStringFixedLength_NullViewThrows() {
        reader.wrap(segment);
        assertThrows(IllegalArgumentException.class, () -> reader.readStringFixedLength(null));
    }

    @Test
    void testReadStringFixedLength_LengthExceedsRemainingThrows() {
        // Write a large length value but don't provide enough data
        MemorySegment tinySegment = arena.allocate(8);
        SegmentBinaryWriter tempWriter = new SegmentBinaryWriter();
        tempWriter.wrap(tinySegment).writeIntBE(100); // Claim 100 bytes, only 4 remaining

        SegmentBinaryReader tempReader = new SegmentBinaryReader();
        tempReader.wrap(tinySegment);
        Utf8View tempView = new Utf8View();

        assertThrows(
                IndexOutOfBoundsException.class, () -> tempReader.readStringFixedLength(tempView));
    }

    @Test
    void testReadStringFixedLength_NegativeLengthThrows() {
        MemorySegment tinySegment = arena.allocate(8);
        SegmentBinaryWriter tempWriter = new SegmentBinaryWriter();
        tempWriter.wrap(tinySegment).writeIntBE(-1); // Negative length

        SegmentBinaryReader tempReader = new SegmentBinaryReader();
        tempReader.wrap(tinySegment);
        Utf8View tempView = new Utf8View();

        assertThrows(
                IllegalArgumentException.class, () -> tempReader.readStringFixedLength(tempView));
    }

    @Test
    void testReadStringFixedLength_InvalidUtf8Throws() {
        MemorySegment localSegment = arena.allocate(16);
        SegmentBinaryWriter tempWriter = new SegmentBinaryWriter();
        tempWriter.wrap(localSegment).writeIntBE(1); // Length = 1 byte
        localSegment.set(ValueLayout.JAVA_BYTE, 4, (byte) 0xC2); // Truncated 2-byte sequence

        SegmentBinaryReader tempReader = new SegmentBinaryReader();
        tempReader.wrap(localSegment);
        Utf8View tempView = new Utf8View();

        assertThrows(
                IllegalArgumentException.class, () -> tempReader.readStringFixedLength(tempView));
    }

    @Test
    void testNullableStringFixedLength_Roundtrip() {
        String presentValue = "I am here";

        writer.wrap(segment);
        writer.writeNullableStringFixedLength(presentValue);
        writer.writeNullableStringFixedLength(null);

        reader.wrap(segment);

        // Read present value
        assertTrue(reader.readNullableStringFixedLength(utf8View));
        assertTrue(utf8View.equalsString(presentValue));

        // Read null value
        assertFalse(reader.readNullableStringFixedLength(utf8View));
    }

    @Test
    void testNullableStringFixedLength_NullLeavesViewUntouched() {
        writer.wrap(segment).writeBoolean(false); // Null marker

        utf8View.wrap(segment, 123, 5);

        reader.wrap(segment);
        assertFalse(reader.readNullableStringFixedLength(utf8View));

        // View should be unchanged
        assertEquals(segment, utf8View.segment());
        assertEquals(123, utf8View.offset());
        assertEquals(5, utf8View.byteSize());
    }

    @Test
    void testReadNullableStringFixedLength_NullViewThrows() {
        writer.wrap(segment).writeBoolean(true).writeIntBE(0); // Present empty string
        reader.wrap(segment);
        assertThrows(
                IllegalArgumentException.class, () -> reader.readNullableStringFixedLength(null));
    }

    @Test
    void testStringFixedLength_MultipleStringsRoundtrip() {
        String str1 = "first";
        String str2 = "second with 日本語";
        String str3 = "";
        String str4 = "🚀 rocket";

        writer.wrap(segment);
        writer.writeStringFixedLength(str1);
        writer.writeStringFixedLength(str2);
        writer.writeStringFixedLength(str3);
        writer.writeStringFixedLength(str4);

        reader.wrap(segment);

        reader.readStringFixedLength(utf8View);
        assertTrue(utf8View.equalsString(str1));

        reader.readStringFixedLength(utf8View);
        assertTrue(utf8View.equalsString(str2));

        reader.readStringFixedLength(utf8View);
        assertTrue(utf8View.equalsString(str3));

        reader.readStringFixedLength(utf8View);
        assertTrue(utf8View.equalsString(str4));
    }

    @Test
    void testStringFixedLength_WireFormatVerification() {
        String testStr = "ABC";

        writer.wrap(segment).writeStringFixedLength(testStr);

        // Verify exact wire format: 4-byte BE length + UTF-8 bytes
        assertEquals(0, segment.get(ValueLayout.JAVA_BYTE, 0)); // Length MSB
        assertEquals(0, segment.get(ValueLayout.JAVA_BYTE, 1));
        assertEquals(0, segment.get(ValueLayout.JAVA_BYTE, 2));
        assertEquals(3, segment.get(ValueLayout.JAVA_BYTE, 3)); // Length LSB = 3

        assertEquals('A', segment.get(ValueLayout.JAVA_BYTE, 4));
        assertEquals('B', segment.get(ValueLayout.JAVA_BYTE, 5));
        assertEquals('C', segment.get(ValueLayout.JAVA_BYTE, 6));
    }

    @Test
    void testStringFixedLength_CompareWithVarIntVersion() {
        String testStr = "Hello";

        // Write with fixed-length method
        writer.wrap(segment).writeStringFixedLength(testStr);
        long fixedLengthPosition = writer.position();

        // Write with VarInt method (offset by fixedLengthPosition)
        writer.position(fixedLengthPosition);
        writer.writeString(testStr, scratchBuffer);
        long varIntPosition = writer.position() - fixedLengthPosition;

        // Fixed-length should be 4 + 5 = 9 bytes
        assertEquals(9, fixedLengthPosition);

        // VarInt for length 5 is 1 byte, so VarInt version = 1 + 5 = 6 bytes
        assertEquals(6, varIntPosition);

        // Read back and verify both produce same content
        reader.wrap(segment);
        reader.readStringFixedLength(utf8View);
        assertTrue(utf8View.equalsString(testStr));

        reader.position(fixedLengthPosition);
        reader.readString(utf8View);
        assertTrue(utf8View.equalsString(testStr));
    }
}
