package express.mvp.roray.ffm.utils.functions;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link StructAccessor}. */
class StructAccessorTest {

    private StructAccessor iovecAccessor;
    private StructAccessor sockaddrInAccessor;
    private StructAccessor epollAccessor;

    @BeforeEach
    void setUp() {
        iovecAccessor = StructAccessor.of(LinuxLayouts.IOVEC);
        sockaddrInAccessor = StructAccessor.of(LinuxLayouts.SOCKADDR_IN);
        epollAccessor = StructAccessor.of(LinuxLayouts.EPOLL_EVENT);
    }

    @Test
    @DisplayName("of() should create accessor with correct layout")
    void of_shouldCreateAccessor() {
        StructAccessor accessor = StructAccessor.of(LinuxLayouts.IOVEC);

        assertNotNull(accessor);
        assertEquals(LinuxLayouts.IOVEC, accessor.layout());
    }

    @Test
    @DisplayName("byteSize() should return correct size")
    void byteSize_shouldReturnCorrectSize() {
        assertEquals(16, iovecAccessor.byteSize());
        assertEquals(16, sockaddrInAccessor.byteSize());
        assertEquals(16, epollAccessor.byteSize());
    }

    @Test
    @DisplayName("allocate() should create zeroed segment")
    void allocate_shouldCreateZeroedSegment() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = iovecAccessor.allocate(arena);

            assertNotNull(segment);
            assertEquals(16, segment.byteSize());

            // Should be zeroed
            assertEquals(0L, segment.get(ValueLayout.JAVA_LONG, 0));
            assertEquals(0L, segment.get(ValueLayout.JAVA_LONG, 8));
        }
    }

    @Test
    @DisplayName("allocateArray() should create array of structs")
    void allocateArray_shouldCreateArray() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment array = iovecAccessor.allocateArray(arena, 10);

            assertEquals(16 * 10, array.byteSize());
        }
    }

    @Test
    @DisplayName("fieldOffset() should return correct offsets")
    void fieldOffset_shouldReturnCorrectOffsets() {
        assertEquals(0, iovecAccessor.fieldOffset("iov_base"));
        assertEquals(8, iovecAccessor.fieldOffset("iov_len"));
    }

    @Test
    @DisplayName("fieldOffset() should throw for invalid field")
    void fieldOffset_shouldThrowForInvalidField() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    iovecAccessor.fieldOffset("nonexistent_field");
                });
    }

    // ========================================================================
    // Primitive Field Accessor Tests
    // ========================================================================

    @Test
    @DisplayName("setByte/getByte should work correctly")
    void setGetByte_shouldWork() {
        // Create a custom struct with byte fields for testing
        StructLayout byteStruct =
                MemoryLayout.structLayout(
                        ValueLayout.JAVA_BYTE.withName("field1"),
                        ValueLayout.JAVA_BYTE.withName("field2"));
        StructAccessor accessor = StructAccessor.of(byteStruct);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = accessor.allocate(arena);

            accessor.setByte(segment, "field1", (byte) 42);
            assertEquals((byte) 42, accessor.getByte(segment, "field1"));

            accessor.setByte(segment, "field2", (byte) 0xFF);
            assertEquals((byte) 0xFF, accessor.getByte(segment, "field2"));
        }
    }

    @Test
    @DisplayName("setShort/getShort should work correctly")
    void setGetShort_shouldWork() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addr = sockaddrInAccessor.allocate(arena);

            sockaddrInAccessor.setShort(addr, "sin_family", LinuxLayouts.AF_INET);
            assertEquals(LinuxLayouts.AF_INET, sockaddrInAccessor.getShort(addr, "sin_family"));

            sockaddrInAccessor.setShort(addr, "sin_port", (short) 8080);
            assertEquals((short) 8080, sockaddrInAccessor.getShort(addr, "sin_port"));
        }
    }

    @Test
    @DisplayName("setInt/getInt should work correctly")
    void setGetInt_shouldWork() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment addr = sockaddrInAccessor.allocate(arena);

            // 127.0.0.1 = 0x7F000001
            sockaddrInAccessor.setInt(addr, "sin_addr", 0x7F000001);
            assertEquals(0x7F000001, sockaddrInAccessor.getInt(addr, "sin_addr"));
        }
    }

    @Test
    @DisplayName("setLong/getLong should work correctly")
    void setGetLong_shouldWork() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment epoll = epollAccessor.allocate(arena);

            epollAccessor.setLong(epoll, "data", 0xDEADBEEFCAFEBABEL);
            assertEquals(0xDEADBEEFCAFEBABEL, epollAccessor.getLong(epoll, "data"));
        }
    }

    @Test
    @DisplayName("setPointer/getPointer should work correctly")
    void setGetPointer_shouldWork() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment iovec = iovecAccessor.allocate(arena);
            MemorySegment buffer = arena.allocate(1024);

            iovecAccessor.setPointer(iovec, "iov_base", buffer);
            MemorySegment retrieved = iovecAccessor.getPointer(iovec, "iov_base");

            assertEquals(buffer.address(), retrieved.address());
        }
    }

    // ========================================================================
    // Offset-Based Accessor Tests
    // ========================================================================

    @Test
    @DisplayName("setIntAt/getIntAt should work with raw offset")
    void setGetIntAt_shouldWork() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment epoll = epollAccessor.allocate(arena);

            // events is at offset 0
            epollAccessor.setIntAt(epoll, 0, 42);
            assertEquals(42, epollAccessor.getIntAt(epoll, 0));
        }
    }

    @Test
    @DisplayName("setLongAt/getLongAt should work with raw offset")
    void setGetLongAt_shouldWork() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment iovec = iovecAccessor.allocate(arena);

            // iov_len is at offset 8
            iovecAccessor.setLongAt(iovec, 8, 4096);
            assertEquals(4096, iovecAccessor.getLongAt(iovec, 8));
        }
    }

    // ========================================================================
    // Array Access Tests
    // ========================================================================

    @Test
    @DisplayName("elementAt() should return correct slice")
    void elementAt_shouldReturnCorrectSlice() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment array = iovecAccessor.allocateArray(arena, 5);

            // Set values in each element
            for (int i = 0; i < 5; i++) {
                MemorySegment element = iovecAccessor.elementAt(array, i);
                iovecAccessor.setLong(element, "iov_len", i * 100);
            }

            // Read back and verify
            for (int i = 0; i < 5; i++) {
                MemorySegment element = iovecAccessor.elementAt(array, i);
                assertEquals(i * 100, iovecAccessor.getLong(element, "iov_len"));
            }
        }
    }

    @Test
    @DisplayName("elementAt() should have correct size")
    void elementAt_shouldHaveCorrectSize() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment array = epollAccessor.allocateArray(arena, 10);

            MemorySegment elem = epollAccessor.elementAt(array, 5);
            assertEquals(16, elem.byteSize());
        }
    }

    // ========================================================================
    // Edge Case Tests
    // ========================================================================

    @Test
    @DisplayName("accessor should handle minimum values")
    void accessor_shouldHandleMinValues() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment epoll = epollAccessor.allocate(arena);

            epollAccessor.setInt(epoll, "events", Integer.MIN_VALUE);
            assertEquals(Integer.MIN_VALUE, epollAccessor.getInt(epoll, "events"));

            epollAccessor.setLong(epoll, "data", Long.MIN_VALUE);
            assertEquals(Long.MIN_VALUE, epollAccessor.getLong(epoll, "data"));
        }
    }

    @Test
    @DisplayName("accessor should handle maximum values")
    void accessor_shouldHandleMaxValues() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment epoll = epollAccessor.allocate(arena);

            epollAccessor.setInt(epoll, "events", Integer.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, epollAccessor.getInt(epoll, "events"));

            epollAccessor.setLong(epoll, "data", Long.MAX_VALUE);
            assertEquals(Long.MAX_VALUE, epollAccessor.getLong(epoll, "data"));
        }
    }

    @Test
    @DisplayName("accessor should be reusable for multiple segments")
    void accessor_shouldBeReusable() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg1 = iovecAccessor.allocate(arena);
            MemorySegment seg2 = iovecAccessor.allocate(arena);

            iovecAccessor.setLong(seg1, "iov_len", 100);
            iovecAccessor.setLong(seg2, "iov_len", 200);

            assertEquals(100, iovecAccessor.getLong(seg1, "iov_len"));
            assertEquals(200, iovecAccessor.getLong(seg2, "iov_len"));
        }
    }

    @Test
    @DisplayName("float accessors should work")
    void floatAccessors_shouldWork() {
        // Create a custom struct with float
        StructLayout floatStruct =
                MemoryLayout.structLayout(ValueLayout.JAVA_FLOAT.withName("value"));
        StructAccessor accessor = StructAccessor.of(floatStruct);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = accessor.allocate(arena);

            accessor.setFloat(seg, "value", 3.14159f);
            assertEquals(3.14159f, accessor.getFloat(seg, "value"), 0.00001f);
        }
    }

    @Test
    @DisplayName("double accessors should work")
    void doubleAccessors_shouldWork() {
        // Create a custom struct with double
        StructLayout doubleStruct =
                MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("value"));
        StructAccessor accessor = StructAccessor.of(doubleStruct);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = accessor.allocate(arena);

            accessor.setDouble(seg, "value", 3.141592653589793);
            assertEquals(3.141592653589793, accessor.getDouble(seg, "value"), 0.0);
        }
    }
}
