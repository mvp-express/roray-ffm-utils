package express.mvp.roray.utils.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive round-trip tests for BinaryReader/BinaryWriter. Tests serialization and
 * deserialization of all data types including primitive types, arrays, strings, and MemorySegments.
 */
class BinaryReaderWriterRoundTripTest {

    private Arena arena;
    private SegmentBinaryWriter writer;
    private SegmentBinaryReader reader;
    private MemorySegment buffer;

    @BeforeEach
    void setUp() {
        arena = Arena.ofConfined();
        buffer = arena.allocate(4096);
        writer = new SegmentBinaryWriter().wrap(buffer);
        reader = new SegmentBinaryReader().wrap(buffer);
    }

    @AfterEach
    void tearDown() {
        arena.close();
    }

    // =================================================================
    // Primitive Round-Trip Tests
    // =================================================================

    @Test
    void testByteRoundTrip() {
        writer.writeByte((byte) 42);
        writer.writeByte((byte) -128);
        writer.writeByte((byte) 127);
        writer.writeByte((byte) 0);

        assertEquals(42, reader.readByte());
        assertEquals(-128, reader.readByte());
        assertEquals(127, reader.readByte());
        assertEquals(0, reader.readByte());
    }

    @Test
    void testShortRoundTrip() {
        writer.writeShortLE((short) 12345);
        writer.writeShortLE((short) -32768);
        writer.writeShortLE((short) 32767);
        writer.writeShortLE((short) 0);

        assertEquals(12345, reader.readShortLE());
        assertEquals(-32768, reader.readShortLE());
        assertEquals(32767, reader.readShortLE());
        assertEquals(0, reader.readShortLE());
    }

    @Test
    void testIntRoundTrip() {
        writer.writeIntLE(123456789);
        writer.writeIntLE(-2147483648);
        writer.writeIntLE(2147483647);
        writer.writeIntLE(0);

        assertEquals(123456789, reader.readIntLE());
        assertEquals(-2147483648, reader.readIntLE());
        assertEquals(2147483647, reader.readIntLE());
        assertEquals(0, reader.readIntLE());
    }

    @Test
    void testLongRoundTrip() {
        writer.writeLongLE(9876543210L);
        writer.writeLongLE(-9223372036854775808L);
        writer.writeLongLE(9223372036854775807L);
        writer.writeLongLE(0L);

        assertEquals(9876543210L, reader.readLongLE());
        assertEquals(-9223372036854775808L, reader.readLongLE());
        assertEquals(9223372036854775807L, reader.readLongLE());
        assertEquals(0L, reader.readLongLE());
    }

    @Test
    void testFloatRoundTrip() {
        writer.writeFloatLE(3.14159f);
        writer.writeFloatLE(-2.71828f);
        writer.writeFloatLE(0.0f);
        writer.writeFloatLE(Float.MAX_VALUE);
        writer.writeFloatLE(Float.MIN_VALUE);

        assertEquals(3.14159f, reader.readFloatLE(), 0.00001f);
        assertEquals(-2.71828f, reader.readFloatLE(), 0.00001f);
        assertEquals(0.0f, reader.readFloatLE());
        assertEquals(Float.MAX_VALUE, reader.readFloatLE());
        assertEquals(Float.MIN_VALUE, reader.readFloatLE());
    }

    @Test
    void testDoubleRoundTrip() {
        writer.writeDoubleLE(3.141592653589793);
        writer.writeDoubleLE(-2.718281828459045);
        writer.writeDoubleLE(0.0);
        writer.writeDoubleLE(Double.MAX_VALUE);
        writer.writeDoubleLE(Double.MIN_VALUE);

        assertEquals(3.141592653589793, reader.readDoubleLE(), 0.000000000001);
        assertEquals(-2.718281828459045, reader.readDoubleLE(), 0.000000000001);
        assertEquals(0.0, reader.readDoubleLE());
        assertEquals(Double.MAX_VALUE, reader.readDoubleLE());
        assertEquals(Double.MIN_VALUE, reader.readDoubleLE());
    }

    @Test
    void testBooleanRoundTrip() {
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeBoolean(false);

        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
    }

    // =================================================================
    // MemorySegment Round-Trip Tests
    // =================================================================

    @Test
    void testMemorySegmentRoundTrip() {
        MemorySegment source = arena.allocate(16);
        source.set(ValueLayout.JAVA_LONG, 0, 123456789012345L);
        source.set(ValueLayout.JAVA_LONG, 8, 987654321098765L);

        writer.writeSegmentRaw(source, 0, source.byteSize());

        MemorySegment dest = reader.readSegment(16);
        assertEquals(123456789012345L, dest.get(ValueLayout.JAVA_LONG, 0));
        assertEquals(987654321098765L, dest.get(ValueLayout.JAVA_LONG, 8));
    }

    @Test
    void testMemorySegmentRawWriteRoundTrip() {
        MemorySegment source = arena.allocate(24);
        for (int i = 0; i < 24; i++) {
            source.set(ValueLayout.JAVA_BYTE, i, (byte) (i + 100));
        }

        writer.writeSegmentRaw(source, 0, source.byteSize());

        MemorySegment dest = reader.readSegment(24);
        for (int i = 0; i < 24; i++) {
            assertEquals((byte) (i + 100), dest.get(ValueLayout.JAVA_BYTE, i));
        }
    }

    // =================================================================
    // Mixed Type Round-Trip Tests
    // =================================================================

    @Test
    void testMixedTypesRoundTrip() {
        // Write mixed types
        writer.writeByte((byte) 42);
        writer.writeShortLE((short) 12345);
        writer.writeIntLE(987654321);
        writer.writeLongLE(123456789012345L);
        writer.writeFloatLE(3.14f);
        writer.writeDoubleLE(2.718);
        writer.writeBoolean(true);

        // Read back in same order
        assertEquals(42, reader.readByte());
        assertEquals(12345, reader.readShortLE());
        assertEquals(987654321, reader.readIntLE());
        assertEquals(123456789012345L, reader.readLongLE());
        assertEquals(3.14f, reader.readFloatLE(), 0.001f);
        assertEquals(2.718, reader.readDoubleLE(), 0.0001);
        assertTrue(reader.readBoolean());
    }

    @Test
    void testStructLikeDataRoundTrip() {
        // Simulate struct: { id: int, value: double, flag: boolean }
        int id = 12345;
        double value = 99.99;
        boolean flag = true;

        writer.writeIntLE(id);
        writer.writeDoubleLE(value);
        writer.writeBoolean(flag);

        // Read back
        assertEquals(id, reader.readIntLE());
        assertEquals(value, reader.readDoubleLE(), 0.001);
        assertTrue(reader.readBoolean());
    }

    // =================================================================
    // Skip and Position Tests
    // =================================================================

    @Test
    void testSkipRoundTrip() {
        writer.writeIntLE(100);
        writer.skip(10); // Skip 10 bytes
        writer.writeIntLE(200);

        assertEquals(100, reader.readIntLE());
        reader.skip(10);
        assertEquals(200, reader.readIntLE());
    }

    @Test
    void testPositionResetRoundTrip() {
        writer.writeIntLE(111);
        writer.writeIntLE(222);
        writer.writeIntLE(333);

        reader.position(0);
        assertEquals(111, reader.readIntLE());

        reader.position(4);
        assertEquals(222, reader.readIntLE());

        reader.position(0);
        assertEquals(111, reader.readIntLE());
        assertEquals(222, reader.readIntLE());
        assertEquals(333, reader.readIntLE());
    }

    // =================================================================
    // Large Data Round-Trip Tests
    // =================================================================

    @Test
    void testManyPrimitivesRoundTrip() {
        int count = 100;

        // Write 100 integers
        for (int i = 0; i < count; i++) {
            writer.writeIntLE(i * 1000);
        }

        // Read back
        for (int i = 0; i < count; i++) {
            assertEquals(i * 1000, reader.readIntLE());
        }
    }

    // =================================================================
    // Edge Case Round-Trip Tests
    // =================================================================

    @Test
    void testAllZeroesRoundTrip() {
        writer.writeIntLE(0);
        writer.writeLongLE(0L);
        writer.writeFloatLE(0.0f);
        writer.writeDoubleLE(0.0);
        writer.writeBoolean(false);

        assertEquals(0, reader.readIntLE());
        assertEquals(0L, reader.readLongLE());
        assertEquals(0.0f, reader.readFloatLE());
        assertEquals(0.0, reader.readDoubleLE());
        assertFalse(reader.readBoolean());
    }

    @Test
    void testAllMaxValuesRoundTrip() {
        writer.writeByte(Byte.MAX_VALUE);
        writer.writeShortLE(Short.MAX_VALUE);
        writer.writeIntLE(Integer.MAX_VALUE);
        writer.writeLongLE(Long.MAX_VALUE);

        assertEquals(Byte.MAX_VALUE, reader.readByte());
        assertEquals(Short.MAX_VALUE, reader.readShortLE());
        assertEquals(Integer.MAX_VALUE, reader.readIntLE());
        assertEquals(Long.MAX_VALUE, reader.readLongLE());
    }

    @Test
    void testAllMinValuesRoundTrip() {
        writer.writeByte(Byte.MIN_VALUE);
        writer.writeShortLE(Short.MIN_VALUE);
        writer.writeIntLE(Integer.MIN_VALUE);
        writer.writeLongLE(Long.MIN_VALUE);

        assertEquals(Byte.MIN_VALUE, reader.readByte());
        assertEquals(Short.MIN_VALUE, reader.readShortLE());
        assertEquals(Integer.MIN_VALUE, reader.readIntLE());
        assertEquals(Long.MIN_VALUE, reader.readLongLE());
    }

    @Test
    void testAlternatingSignsRoundTrip() {
        writer.writeIntLE(100);
        writer.writeIntLE(-100);
        writer.writeIntLE(200);
        writer.writeIntLE(-200);
        writer.writeIntLE(0);

        assertEquals(100, reader.readIntLE());
        assertEquals(-100, reader.readIntLE());
        assertEquals(200, reader.readIntLE());
        assertEquals(-200, reader.readIntLE());
        assertEquals(0, reader.readIntLE());
    }
}
