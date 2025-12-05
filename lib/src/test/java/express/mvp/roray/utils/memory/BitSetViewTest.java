package express.mvp.roray.utils.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for BitSetView covering bit operations, edge cases, and boundary conditions.
 */
class BitSetViewTest {

    private Arena arena;
    private BitSetView bitSet;

    @BeforeEach
    void setUp() {
        arena = Arena.ofConfined();
        bitSet = new BitSetView();
    }

    @AfterEach
    void tearDown() {
        arena.close();
    }

    // =================================================================
    // Basic Functionality Tests
    // =================================================================

    @Test
    void testWrapAndBasicProperties() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        assertTrue(bitSet.isValid());
        assertEquals(16, bitSet.byteSize());
        assertEquals(0, bitSet.offset());
        assertEquals(128, bitSet.size()); // 16 bytes * 8 bits
        assertSame(segment, bitSet.segment());
    }

    @Test
    void testUnwrappedBitSet() {
        assertFalse(bitSet.isValid());
        assertNull(bitSet.segment());
    }

    @Test
    void testSetAndGet() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        // Initially all bits should be 0
        assertFalse(bitSet.get(0));
        assertFalse(bitSet.get(7));
        assertFalse(bitSet.get(127));

        // Set some bits
        bitSet.set(0);
        bitSet.set(7);
        bitSet.set(15);
        bitSet.set(127);

        // Check they're set
        assertTrue(bitSet.get(0));
        assertTrue(bitSet.get(7));
        assertTrue(bitSet.get(15));
        assertTrue(bitSet.get(127));

        // Check other bits remain 0
        assertFalse(bitSet.get(1));
        assertFalse(bitSet.get(6));
        assertFalse(bitSet.get(8));
        assertFalse(bitSet.get(126));
    }

    @Test
    void testClear() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        // Set some bits
        bitSet.set(0);
        bitSet.set(7);
        bitSet.set(64);

        assertTrue(bitSet.get(0));
        assertTrue(bitSet.get(7));
        assertTrue(bitSet.get(64));

        // Clear them
        bitSet.clear(0);
        bitSet.clear(7);
        bitSet.clear(64);

        assertFalse(bitSet.get(0));
        assertFalse(bitSet.get(7));
        assertFalse(bitSet.get(64));
    }

    @Test
    void testSetWithValue() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.set(5, true);
        assertTrue(bitSet.get(5));

        bitSet.set(5, false);
        assertFalse(bitSet.get(5));

        bitSet.set(10, false);
        assertFalse(bitSet.get(10));

        bitSet.set(10, true);
        assertTrue(bitSet.get(10));
    }

    @Test
    void testFlip() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        assertFalse(bitSet.get(3));
        bitSet.flip(3);
        assertTrue(bitSet.get(3));
        bitSet.flip(3);
        assertFalse(bitSet.get(3));
        bitSet.flip(3);
        assertTrue(bitSet.get(3));
    }

    @Test
    void testClearAll() {
        MemorySegment segment = arena.allocate(16);
        segment.fill((byte) 0xFF); // Fill with all 1s
        bitSet.wrap(segment, 0, 16);

        // All bits should be set
        assertTrue(bitSet.get(0));
        assertTrue(bitSet.get(64));
        assertTrue(bitSet.get(127));

        bitSet.clearAll();

        // All bits should now be clear
        assertFalse(bitSet.get(0));
        assertFalse(bitSet.get(64));
        assertFalse(bitSet.get(127));
        assertEquals(0, bitSet.cardinality());
    }

    @Test
    void testSetAll() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.setAll();

        // All bits should be set
        assertTrue(bitSet.get(0));
        assertTrue(bitSet.get(64));
        assertTrue(bitSet.get(127));
        assertEquals(128, bitSet.cardinality());
    }

    // =================================================================
    // Cardinality Tests
    // =================================================================

    @Test
    void testCardinality_Empty() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        assertEquals(0, bitSet.cardinality());
        assertTrue(bitSet.isEmpty());
    }

    @Test
    void testCardinality_SingleBit() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.set(42);
        assertEquals(1, bitSet.cardinality());
        assertFalse(bitSet.isEmpty());
    }

    @Test
    void testCardinality_MultipleBits() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.set(0);
        bitSet.set(7);
        bitSet.set(15);
        bitSet.set(64);
        bitSet.set(127);

        assertEquals(5, bitSet.cardinality());
    }

    @Test
    void testCardinality_AllBits() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.setAll();
        assertEquals(128, bitSet.cardinality());
    }

    @Test
    void testCardinality_AfterClear() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.set(0);
        bitSet.set(1);
        bitSet.set(2);
        assertEquals(3, bitSet.cardinality());

        bitSet.clear(1);
        assertEquals(2, bitSet.cardinality());

        bitSet.clearAll();
        assertEquals(0, bitSet.cardinality());
    }

    // =================================================================
    // nextSetBit() Tests
    // =================================================================

    @Test
    void testNextSetBit_Empty() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        assertEquals(-1, bitSet.nextSetBit(0));
        assertEquals(-1, bitSet.nextSetBit(50));
    }

    @Test
    void testNextSetBit_FirstBit() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.set(0);
        assertEquals(0, bitSet.nextSetBit(0));
    }

    @Test
    void testNextSetBit_Sequential() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.set(5);
        bitSet.set(10);
        bitSet.set(20);
        bitSet.set(100);

        assertEquals(5, bitSet.nextSetBit(0));
        assertEquals(5, bitSet.nextSetBit(5));
        assertEquals(10, bitSet.nextSetBit(6));
        assertEquals(10, bitSet.nextSetBit(10));
        assertEquals(20, bitSet.nextSetBit(11));
        assertEquals(100, bitSet.nextSetBit(21));
        assertEquals(-1, bitSet.nextSetBit(101));
    }

    @Test
    void testNextSetBit_WithinByte() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.set(3);
        bitSet.set(5);
        bitSet.set(7);

        assertEquals(3, bitSet.nextSetBit(0));
        assertEquals(5, bitSet.nextSetBit(4));
        assertEquals(7, bitSet.nextSetBit(6));
    }

    @Test
    void testNextSetBit_AcrossBytes() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.set(7); // Last bit of byte 0
        bitSet.set(8); // First bit of byte 1
        bitSet.set(15); // Last bit of byte 1
        bitSet.set(16); // First bit of byte 2

        assertEquals(7, bitSet.nextSetBit(0));
        assertEquals(8, bitSet.nextSetBit(8));
        assertEquals(15, bitSet.nextSetBit(9));
        assertEquals(16, bitSet.nextSetBit(16));
    }

    @Test
    void testNextSetBit_OutOfBounds() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.set(50);
        assertEquals(-1, bitSet.nextSetBit(128));
        assertEquals(-1, bitSet.nextSetBit(200));
    }

    @Test
    void testNextSetBit_NegativeIndex() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.nextSetBit(-1));
    }

    // =================================================================
    // nextClearBit() Tests
    // =================================================================

    @Test
    void testNextClearBit_AllClear() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        assertEquals(0, bitSet.nextClearBit(0));
        assertEquals(50, bitSet.nextClearBit(50));
        assertEquals(127, bitSet.nextClearBit(127));
    }

    @Test
    void testNextClearBit_AllSet() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);
        bitSet.setAll();

        assertEquals(128, bitSet.nextClearBit(0)); // Beyond the bitset
    }

    @Test
    void testNextClearBit_Mixed() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        bitSet.set(0);
        bitSet.set(1);
        bitSet.set(2);
        bitSet.set(5);
        bitSet.set(6);

        assertEquals(3, bitSet.nextClearBit(0));
        assertEquals(3, bitSet.nextClearBit(3));
        assertEquals(4, bitSet.nextClearBit(4));
        assertEquals(7, bitSet.nextClearBit(7));
    }

    @Test
    void testNextClearBit_AcrossBytes() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        // Set first byte completely
        for (int i = 0; i < 8; i++) {
            bitSet.set(i);
        }

        assertEquals(8, bitSet.nextClearBit(0));
        assertEquals(8, bitSet.nextClearBit(7));
    }

    @Test
    void testNextClearBit_NegativeIndex() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.nextClearBit(-1));
    }

    // =================================================================
    // Boundary Tests
    // =================================================================

    @Test
    void testByteBoundaries() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        // Test all byte boundaries
        for (int i = 0; i < 16; i++) {
            int bitIndex = i * 8;
            bitSet.set(bitIndex);
            assertTrue(bitSet.get(bitIndex));

            bitIndex = i * 8 + 7;
            bitSet.set(bitIndex);
            assertTrue(bitSet.get(bitIndex));
        }
    }

    @Test
    void testAllBitsInByte() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        // Set all bits in second byte (bits 8-15)
        for (int i = 8; i < 16; i++) {
            bitSet.set(i);
        }

        // Verify all are set
        for (int i = 8; i < 16; i++) {
            assertTrue(bitSet.get(i));
        }

        // Verify first byte is clear
        for (int i = 0; i < 8; i++) {
            assertFalse(bitSet.get(i));
        }

        assertEquals(8, bitSet.cardinality());
    }

    @Test
    void testSingleByteOperations() {
        MemorySegment segment = arena.allocate(1);
        bitSet.wrap(segment, 0, 1);

        assertEquals(8, bitSet.size());

        for (int i = 0; i < 8; i++) {
            bitSet.set(i);
            assertTrue(bitSet.get(i));
        }

        assertEquals(8, bitSet.cardinality());

        for (int i = 0; i < 8; i++) {
            bitSet.clear(i);
            assertFalse(bitSet.get(i));
        }

        assertEquals(0, bitSet.cardinality());
    }

    @Test
    void testLargeBitSet() {
        MemorySegment segment = arena.allocate(1024); // 8192 bits
        bitSet.wrap(segment, 0, 1024);

        assertEquals(8192, bitSet.size());

        // Set every 100th bit
        for (int i = 0; i < 8192; i += 100) {
            bitSet.set(i);
        }

        // Verify
        for (int i = 0; i < 8192; i++) {
            if (i % 100 == 0) {
                assertTrue(bitSet.get(i), "Bit " + i + " should be set");
            } else {
                assertFalse(bitSet.get(i), "Bit " + i + " should be clear");
            }
        }

        assertEquals(82, bitSet.cardinality()); // 8192/100 = 81.92, so 82 bits
    }

    // =================================================================
    // Error Handling Tests
    // =================================================================

    @Test
    void testUnwrappedOperationsThrow() {
        assertThrows(IllegalStateException.class, () -> bitSet.set(0));
        assertThrows(IllegalStateException.class, () -> bitSet.clear(0));
        assertThrows(IllegalStateException.class, () -> bitSet.get(0));
        assertThrows(IllegalStateException.class, () -> bitSet.flip(0));
    }

    @Test
    void testOutOfBoundsAccess() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.set(128));
        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.set(200));
        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.get(128));
        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.clear(128));
        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.flip(128));
    }

    @Test
    void testNegativeIndices() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.set(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.clear(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.flip(-1));
    }

    // =================================================================
    // Offset Tests
    // =================================================================

    @Test
    void testNonZeroOffset() {
        MemorySegment segment = arena.allocate(32);
        bitSet.wrap(segment, 16, 16); // Start at byte 16

        assertEquals(16, bitSet.offset());
        assertEquals(16, bitSet.byteSize());
        assertEquals(128, bitSet.size());

        bitSet.set(0);
        bitSet.set(127);

        assertTrue(bitSet.get(0));
        assertTrue(bitSet.get(127));
        assertEquals(2, bitSet.cardinality());
    }

    // =================================================================
    // Pattern Tests
    // =================================================================

    @Test
    void testAlternatingPattern() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        // Set alternating bits (0, 2, 4, 6, ...)
        for (int i = 0; i < 128; i += 2) {
            bitSet.set(i);
        }

        // Verify pattern
        for (int i = 0; i < 128; i++) {
            if (i % 2 == 0) {
                assertTrue(bitSet.get(i));
            } else {
                assertFalse(bitSet.get(i));
            }
        }

        assertEquals(64, bitSet.cardinality());
    }

    @Test
    void testBlockPattern() {
        MemorySegment segment = arena.allocate(16);
        bitSet.wrap(segment, 0, 16);

        // Set blocks of 8 bits
        for (int i = 0; i < 64; i++) {
            bitSet.set(i);
        }

        assertEquals(64, bitSet.cardinality());

        // First 8 bytes should have all bits set
        for (int i = 0; i < 64; i++) {
            assertTrue(bitSet.get(i));
        }

        // Remaining should be clear
        for (int i = 64; i < 128; i++) {
            assertFalse(bitSet.get(i));
        }
    }

    @Test
    void testSparsePattern() {
        MemorySegment segment = arena.allocate(128); // 1024 bits
        bitSet.wrap(segment, 0, 128);

        // Set very sparse bits
        int[] sparseBits = {0, 10, 100, 500, 999, 1023};
        for (int bit : sparseBits) {
            bitSet.set(bit);
        }

        assertEquals(6, bitSet.cardinality());

        // Use nextSetBit to find them
        int count = 0;
        for (long i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
            assertEquals(sparseBits[count], i);
            count++;
        }
        assertEquals(6, count);
    }
}
