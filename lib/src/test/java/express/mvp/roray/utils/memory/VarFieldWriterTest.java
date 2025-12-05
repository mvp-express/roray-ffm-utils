package express.mvp.roray.utils.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for VarFieldWriter covering message building, variable-length fields, and
 * flyweight-compatible layouts.
 */
class VarFieldWriterTest {

    private Arena arena;
    private VarFieldWriter writer;

    @BeforeEach
    void setUp() {
        arena = Arena.ofConfined();
    }

    @AfterEach
    void tearDown() {
        arena.close();
    }

    // =================================================================
    // Basic Functionality Tests
    // =================================================================

    @Test
    void testFixedHeaderWriting() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 16, 5);

        writer.writeByte(0, (byte) 42);
        writer.writeShortLE(1, (short) 1000);
        writer.writeIntLE(3, 123456);
        writer.writeLongLE(7, 9876543210L);
        writer.writeBoolean(15, true);

        MemorySegment result = writer.finish();

        assertEquals(42, result.get(ValueLayout.JAVA_BYTE, 0));
        assertEquals(
                1000,
                result.get(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 1));
        assertEquals(
                123456,
                result.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 3));
        assertEquals(
                9876543210L,
                result.get(ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 7));
        assertEquals(1, result.get(ValueLayout.JAVA_BYTE, 15));
    }

    @Test
    void testVarFieldWriting_ByteArray() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 8, 3);

        byte[] data1 = "Hello".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "World".getBytes(StandardCharsets.UTF_8);

        int slot1 = writer.reserveVarField();
        int slot2 = writer.reserveVarField();

        writer.writeVarField(slot1, data1);
        writer.writeVarField(slot2, data2);

        MemorySegment result = writer.finish();

        // Check var field headers (offset + length pairs)
        long headerOffset = 8; // After fixed header
        int offset1 = result.get(Layouts.INT_BE, headerOffset);
        int length1 = result.get(Layouts.INT_BE, headerOffset + 4);

        assertEquals(data1.length, length1);
        assertEquals("Hello", readStringFromSegment(result, offset1, length1));

        int offset2 = result.get(Layouts.INT_BE, headerOffset + 8);
        int length2 = result.get(Layouts.INT_BE, headerOffset + 12);

        assertEquals(data2.length, length2);
        assertEquals("World", readStringFromSegment(result, offset2, length2));
    }

    @Test
    void testVarFieldWriting_String() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 4, 2);

        int slot1 = writer.reserveVarField();
        int slot2 = writer.reserveVarField();

        MemorySegment scratch = scratch(32);
        writer.writeVarField(slot1, "Hello", scratch);
        writer.writeVarField(slot2, "你好", scratch);

        MemorySegment result = writer.finish();

        long headerOffset = 4;
        int offset1 = result.get(Layouts.INT_BE, headerOffset);
        int length1 = result.get(Layouts.INT_BE, headerOffset + 4);

        assertEquals("Hello", readStringFromSegment(result, offset1, length1));

        int offset2 = result.get(Layouts.INT_BE, headerOffset + 8);
        int length2 = result.get(Layouts.INT_BE, headerOffset + 12);

        assertEquals("你好", readStringFromSegment(result, offset2, length2));
    }

    @Test
    void testVarFieldWriting_SurrogatePair() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 4, 1);

        int slot = writer.reserveVarField();
        MemorySegment scratch = scratch(16);
        writer.writeVarField(slot, "🚀", scratch);

        MemorySegment result = writer.finish();

        long headerOffset = 4;
        int offset = result.get(Layouts.INT_BE, headerOffset);
        int length = result.get(Layouts.INT_BE, headerOffset + 4);

        assertEquals(4, length); // emoji encodes to 4 bytes
        assertEquals("🚀", readStringFromSegment(result, offset, length));
    }

    @Test
    void testVarFieldWriting_UnpairedSurrogateFails() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 4, 1);

        int slot = writer.reserveVarField();
        MemorySegment scratch = scratch(8);

        String invalid = "\uD83D"; // Leading surrogate without pair
        assertThrows(
                IllegalArgumentException.class, () -> writer.writeVarField(slot, invalid, scratch));
    }

    @Test
    void testVarFieldWriting_MemorySegment() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 4, 2);

        byte[] data = "Test Data".getBytes(StandardCharsets.UTF_8);
        MemorySegment sourceSegment = arena.allocate(data.length);
        MemorySegment.copy(MemorySegment.ofArray(data), 0, sourceSegment, 0, data.length);

        int slot = writer.reserveVarField();
        writer.writeVarField(slot, sourceSegment);

        MemorySegment result = writer.finish();

        long headerOffset = 4;
        int offset = result.get(Layouts.INT_BE, headerOffset);
        int length = result.get(Layouts.INT_BE, headerOffset + 4);

        assertEquals(data.length, length);
        assertEquals("Test Data", readStringFromSegment(result, offset, length));
    }

    @Test
    void testVarFieldWriting_Partial() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 4, 2);

        byte[] data = "0123456789".getBytes(StandardCharsets.UTF_8);

        int slot = writer.reserveVarField();
        writer.writeVarField(slot, data, 2, 5); // "23456"

        MemorySegment result = writer.finish();

        long headerOffset = 4;
        int offset = result.get(Layouts.INT_BE, headerOffset);
        int length = result.get(Layouts.INT_BE, headerOffset + 4);

        assertEquals(5, length);
        assertEquals("23456", readStringFromSegment(result, offset, length));
    }

    // =================================================================
    // Complex Message Tests
    // =================================================================

    @Test
    void testCompleteMessage() {
        MemorySegment segment = arena.allocate(2048);
        writer = new VarFieldWriter(segment, 16, 5);

        // Write fixed header
        writer.writeByte(0, (byte) 1); // message type
        writer.writeIntLE(1, 42); // message id
        writer.writeLongLE(5, System.currentTimeMillis()); // timestamp
        writer.writeShortLE(13, (short) 3); // field count
        writer.writeBoolean(15, true); // active flag

        // Reserve and write variable fields
        int keySlot = writer.reserveVarField();
        int valueSlot = writer.reserveVarField();
        int metadataSlot = writer.reserveVarField();

        MemorySegment scratch = scratch(128);
        writer.writeVarField(keySlot, "user.name", scratch);
        writer.writeVarField(valueSlot, "John Doe", scratch);
        writer.writeVarField(metadataSlot, "metadata", scratch);

        MemorySegment result = writer.finish();

        // Verify fixed header
        assertEquals(1, result.get(ValueLayout.JAVA_BYTE, 0));
        assertEquals(
                42,
                result.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 1));
        assertEquals(
                3,
                result.get(
                        ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 13));
        assertEquals(1, result.get(ValueLayout.JAVA_BYTE, 15));

        // Verify variable fields
        long headerOffset = 16;

        int keyOffset = result.get(Layouts.INT_BE, headerOffset);
        int keyLength = result.get(Layouts.INT_BE, headerOffset + 4);
        assertEquals("user.name", readStringFromSegment(result, keyOffset, keyLength));

        int valueOffset = result.get(Layouts.INT_BE, headerOffset + 8);
        int valueLength = result.get(Layouts.INT_BE, headerOffset + 12);
        assertEquals("John Doe", readStringFromSegment(result, valueOffset, valueLength));

        int metadataOffset = result.get(Layouts.INT_BE, headerOffset + 16);
        int metadataLength = result.get(Layouts.INT_BE, headerOffset + 20);
        assertEquals("metadata", readStringFromSegment(result, metadataOffset, metadataLength));
    }

    @Test
    void testMultipleMessages_WithReset() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 8, 2);

        MemorySegment scratch = scratch(64);

        // First message
        writer.writeIntLE(0, 1);
        int slot1 = writer.reserveVarField();
        writer.writeVarField(slot1, "Message 1", scratch);

        MemorySegment result1 = writer.finish();
        long size1 = writer.bytesWritten();
        assertTrue(size1 > 0);

        // Copy first message to independent storage before reset
        MemorySegment message1Copy = arena.allocate(size1);
        message1Copy.copyFrom(result1);

        // Reset and write second message
        writer.reset();
        writer.writeIntLE(0, 2);
        int slot2 = writer.reserveVarField();
        writer.writeVarField(slot2, "Message 2", scratch);

        MemorySegment result2 = writer.finish();
        long size2 = writer.bytesWritten();
        assertTrue(size2 > 0);

        // Verify first message from copy
        long headerOffset = 8;
        int offset1 = message1Copy.get(Layouts.INT_BE, headerOffset);
        int length1 = message1Copy.get(Layouts.INT_BE, headerOffset + 4);
        assertEquals("Message 1", readStringFromSegment(message1Copy, offset1, length1));

        // Verify second message
        int offset2 = result2.get(Layouts.INT_BE, headerOffset);
        int length2 = result2.get(Layouts.INT_BE, headerOffset + 4);
        assertEquals("Message 2", readStringFromSegment(result2, offset2, length2));
    }

    @Test
    void testCustomProtocol_OrderEntryFrame() {
        // Simulate an order-entry frame with a 24-byte fixed header and three variable fields:
        // symbol, client order id, and additional execution instructions.
        MemorySegment segment = arena.allocate(2048);
        writer = new VarFieldWriter(segment, 24, 4);

        writer.writeByte(0, (byte) 0xD); // message type 'D' (New Order)
        writer.writeShortLE(1, (short) 25); // protocol version
        writer.writeIntLE(3, 77); // sequence number
        writer.writeLongLE(7, 1_734_000_123_456L); // timestamp nanos
        writer.writeLongLE(15, 9_999_001L); // correlation id
        writer.writeBoolean(23, true); // risk checks already done

        int symbolSlot = writer.reserveVarField();
        int clOrdSlot = writer.reserveVarField();
        int instructionsSlot = writer.reserveVarField();

        MemorySegment scratch = scratch(256);
        writer.writeVarField(symbolSlot, "ESZ5", scratch);
        writer.writeVarField(clOrdSlot, "ABC123456789", scratch);
        writer.writeVarField(instructionsSlot, "IOC|DMA", scratch);

        MemorySegment frame = writer.finish();

        // Validate header values
        assertEquals(0xD, frame.get(ValueLayout.JAVA_BYTE, 0));
        assertEquals(
                25,
                frame.get(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 1));
        assertEquals(
                77,
                frame.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 3));
        assertEquals(true, frame.get(ValueLayout.JAVA_BYTE, 23) == 1);

        long headerOffset = 24;
        int symbolOffset = frame.get(Layouts.INT_BE, headerOffset);
        int symbolLen = frame.get(Layouts.INT_BE, headerOffset + 4);
        assertEquals("ESZ5", readStringFromSegment(frame, symbolOffset, symbolLen));

        int clOrdOffset = frame.get(Layouts.INT_BE, headerOffset + 8);
        int clOrdLen = frame.get(Layouts.INT_BE, headerOffset + 12);
        assertEquals("ABC123456789", readStringFromSegment(frame, clOrdOffset, clOrdLen));

        int instructionsOffset = frame.get(Layouts.INT_BE, headerOffset + 16);
        int instructionsLen = frame.get(Layouts.INT_BE, headerOffset + 20);
        assertEquals("IOC|DMA", readStringFromSegment(frame, instructionsOffset, instructionsLen));
    }

    @Test
    void testCustomProtocol_MarketDataSnapshotFrame() {
        // A hypothetical market-data snapshot frame with structured header followed by variable
        // payloads
        MemorySegment segment = arena.allocate(4096);
        writer = new VarFieldWriter(segment, 40, 5);

        writer.writeByte(0, (byte) 'W'); // message type (e.g., snapshot)
        writer.writeIntLE(1, 5); // depth levels
        writer.writeLongLE(5, 20241111001L); // snapshot id
        writer.writeLongLE(13, 1_734_000_555_000L); // capture timestamp nanos
        writer.writeIntLE(21, 17); // feed id / partition
        writer.writeBoolean(25, false); // incremental flag
        writer.writeIntLE(26, 2); // number of books bundled
        writer.writeIntLE(30, 0); // reserved / checksum placeholder

        int instrumentSlot = writer.reserveVarField();
        int topOfBookSlot = writer.reserveVarField();
        int depthSlot = writer.reserveVarField();
        int metadataSlot = writer.reserveVarField();

        MemorySegment scratch = scratch(1024);
        writer.writeVarField(instrumentSlot, "NQZ5", scratch);
        writer.writeVarField(topOfBookSlot, "{\"bid\":17999.75,\"ask\":18000.0}", scratch);
        writer.writeVarField(depthSlot, "[[17999.75,5],[17999.50,7]]", scratch);
        writer.writeVarField(metadataSlot, "schema=v2|venue=XCME", scratch);

        MemorySegment frame = writer.finish();

        long headerOffset = 40;
        int instrumentOffset = frame.get(Layouts.INT_BE, headerOffset);
        int instrumentLen = frame.get(Layouts.INT_BE, headerOffset + 4);
        assertEquals("NQZ5", readStringFromSegment(frame, instrumentOffset, instrumentLen));

        int topOffset = frame.get(Layouts.INT_BE, headerOffset + 8);
        int topLen = frame.get(Layouts.INT_BE, headerOffset + 12);
        assertEquals(
                "{\"bid\":17999.75,\"ask\":18000.0}",
                readStringFromSegment(frame, topOffset, topLen));

        int depthOffset = frame.get(Layouts.INT_BE, headerOffset + 16);
        int depthLen = frame.get(Layouts.INT_BE, headerOffset + 20);
        assertEquals(
                "[[17999.75,5],[17999.50,7]]", readStringFromSegment(frame, depthOffset, depthLen));

        int metaOffset = frame.get(Layouts.INT_BE, headerOffset + 24);
        int metaLen = frame.get(Layouts.INT_BE, headerOffset + 28);
        assertEquals("schema=v2|venue=XCME", readStringFromSegment(frame, metaOffset, metaLen));
    }

    // =================================================================
    // Metrics Tests
    // =================================================================

    @Test
    void testBytesWritten() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 8, 3);

        long initialBytes = writer.bytesWritten();
        assertEquals(8 + (3 * 8), initialBytes); // fixed header + var field headers

        int slot1 = writer.reserveVarField();
        MemorySegment scratch = scratch(64);
        writer.writeVarField(slot1, "Hello", scratch);

        long afterFirstWrite = writer.bytesWritten();
        assertEquals(initialBytes + 5, afterFirstWrite);

        int slot2 = writer.reserveVarField();
        writer.writeVarField(slot2, "World!", scratch);

        long afterSecondWrite = writer.bytesWritten();
        assertEquals(afterFirstWrite + 6, afterSecondWrite);
    }

    @Test
    void testVarFieldCount() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 8, 5);

        assertEquals(0, writer.varFieldCount());

        writer.reserveVarField();
        assertEquals(1, writer.varFieldCount());

        writer.reserveVarField();
        writer.reserveVarField();
        assertEquals(3, writer.varFieldCount());
    }

    @Test
    void testDataOffset() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 16, 5);

        long expectedOffset = 16 + (5 * 8); // fixed header + var field headers
        assertEquals(expectedOffset, writer.dataOffset());
    }

    // =================================================================
    // Error Handling Tests
    // =================================================================

    @Test
    void testInvalidConstruction() {
        MemorySegment segment = arena.allocate(1024);

        assertThrows(IllegalArgumentException.class, () -> new VarFieldWriter(null, 8, 2));
        assertThrows(IllegalArgumentException.class, () -> new VarFieldWriter(segment, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new VarFieldWriter(segment, 8, -1));
    }

    @Test
    void testSegmentTooSmall() {
        MemorySegment segment = arena.allocate(10); // Too small for headers

        assertThrows(
                IllegalArgumentException.class,
                () -> new VarFieldWriter(segment, 8, 5)); // Would need 8 + (5*8) = 48 bytes
    }

    @Test
    void testMaxVarFieldsExceeded() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 8, 2);

        writer.reserveVarField();
        writer.reserveVarField();

        assertThrows(IllegalStateException.class, () -> writer.reserveVarField());
    }

    @Test
    void testInvalidSlot() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 8, 3);

        int slot = writer.reserveVarField();
        MemorySegment scratch = scratch(16);

        // Slot beyond reserved
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.writeVarField(slot + 1, "test", scratch));

        // Negative slot
        assertThrows(
                IllegalArgumentException.class, () -> writer.writeVarField(-1, "test", scratch));
    }

    @Test
    void testNullData() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 8, 2);

        int slot = writer.reserveVarField();

        assertThrows(
                IllegalArgumentException.class, () -> writer.writeVarField(slot, (byte[]) null));
        MemorySegment scratch = scratch(16);
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.writeVarField(slot, (String) null, scratch));
        assertThrows(
                IllegalArgumentException.class, () -> writer.writeVarField(slot, "data", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.writeVarField(slot, (MemorySegment) null));
    }

    @Test
    void testInvalidPartialWrite() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 8, 2);

        int slot = writer.reserveVarField();
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> writer.writeVarField(slot, data, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> writer.writeVarField(slot, data, 0, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.writeVarField(slot, data, 0, 100)); // Beyond array
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.writeVarField(slot, data, 2, 10)); // offset + length > array length
    }

    @Test
    void testDataTooLarge() {
        MemorySegment segment = arena.allocate(100);
        writer = new VarFieldWriter(segment, 8, 2);

        int slot = writer.reserveVarField();
        byte[] largeData = new byte[200]; // Too large for segment

        assertThrows(IndexOutOfBoundsException.class, () -> writer.writeVarField(slot, largeData));
    }

    @Test
    void testScratchBufferTooSmall() {
        MemorySegment segment = arena.allocate(256);
        writer = new VarFieldWriter(segment, 8, 1);

        int slot = writer.reserveVarField();
        MemorySegment tinyScratch = scratch(2);

        assertThrows(
                IllegalArgumentException.class,
                () -> writer.writeVarField(slot, "超爆", tinyScratch));
    }

    @Test
    void testFixedHeaderOutOfBounds() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 8, 2);

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> writer.writeByte(8, (byte) 0)); // Beyond fixed header

        assertThrows(
                IndexOutOfBoundsException.class,
                () -> writer.writeLongLE(2, 0L)); // Would write past end
    }

    // =================================================================
    // Edge Cases
    // =================================================================

    @Test
    void testEmptyVarField() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 8, 2);

        int slot = writer.reserveVarField();
        MemorySegment scratch = scratch(1);
        writer.writeVarField(slot, "", scratch);

        MemorySegment result = writer.finish();

        long headerOffset = 8;
        int length =
                result.get(
                        ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN),
                        headerOffset + 4);
        assertEquals(0, length);
    }

    @Test
    void testNoVarFields() {
        MemorySegment segment = arena.allocate(1024);
        writer = new VarFieldWriter(segment, 16, 0);

        writer.writeIntLE(0, 42);
        writer.writeLongLE(4, 123L);

        MemorySegment result = writer.finish();

        assertEquals(
                42,
                result.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 0));
        assertEquals(
                123L,
                result.get(ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 4));
        assertEquals(16, writer.bytesWritten());
    }

    @Test
    void testLargeVarFields() {
        MemorySegment segment = arena.allocate(64 * 1024); // 64KB
        writer = new VarFieldWriter(segment, 8, 3);

        byte[] largeData = new byte[10000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        int slot1 = writer.reserveVarField();
        int slot2 = writer.reserveVarField();
        int slot3 = writer.reserveVarField();

        writer.writeVarField(slot1, largeData);
        writer.writeVarField(slot2, largeData);
        writer.writeVarField(slot3, largeData);

        writer.finish();
        assertEquals(8 + (3 * 8) + (3 * 10000), writer.bytesWritten());
    }

    // =================================================================
    // Encode UTF-8 Tests
    // =================================================================

    @Test
    void testEncodeUtf8_Direct() {
        MemorySegment segment = arena.allocate(100);
        String testStr = "Hello World";
        int bytesWritten = VarFieldWriter.encodeUtf8(testStr, segment);
        assertEquals(testStr.length(), bytesWritten);
        assertEquals(testStr, readStringFromSegment(segment, 0, bytesWritten));

        // Test with multi-byte chars
        String multiByte = "Hello \uD83D\uDE80"; // Hello 🚀
        int mbBytes = VarFieldWriter.encodeUtf8(multiByte, segment);
        assertEquals(6 + 4, mbBytes); // "Hello " is 6, rocket is 4
        assertEquals(multiByte, readStringFromSegment(segment, 0, mbBytes));
    }

    @Test
    void testEncodeUtf8_BoundsCheck() {
        MemorySegment segment = arena.allocate(5);
        String testStr = "Hello World"; // 11 bytes
        assertThrows(
                IndexOutOfBoundsException.class, () -> VarFieldWriter.encodeUtf8(testStr, segment));
    }

    // =================================================================
    // Helper Methods
    // =================================================================

    private MemorySegment scratch(int capacity) {
        return arena.allocate(capacity);
    }

    private String readStringFromSegment(MemorySegment segment, int offset, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = segment.get(ValueLayout.JAVA_BYTE, offset + i);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
