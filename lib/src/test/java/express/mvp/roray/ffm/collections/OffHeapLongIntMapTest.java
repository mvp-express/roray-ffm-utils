package express.mvp.roray.ffm.collections;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OffHeapLongIntMapTest {

    @Test
    void testPutAndGet() {
        try (OffHeapLongIntMap map = new OffHeapLongIntMapImpl(16)) {
            assertTrue(map.isEmpty());

            map.put(100L, 10);
            map.put(200L, 20);

            assertEquals(2, map.size());
            assertTrue(map.containsKey(100L));
            assertTrue(map.containsKey(200L));
            assertFalse(map.containsKey(300L));

            assertEquals(10, map.get(100L));
            assertEquals(20, map.get(200L));
            assertEquals(-1, map.get(300L)); // Default missing value
        }
    }

    @Test
    void testUpdate() {
        try (OffHeapLongIntMap map = new OffHeapLongIntMapImpl(16)) {
            map.put(1L, 100);
            assertEquals(100, map.get(1L));

            map.put(1L, 200);
            assertEquals(200, map.get(1L));
            assertEquals(1, map.size());
        }
    }

    @Test
    void testRemove() {
        try (OffHeapLongIntMap map = new OffHeapLongIntMapImpl(16)) {
            map.put(1L, 10);
            map.put(2L, 20);

            assertEquals(10, map.remove(1L));
            assertEquals(1, map.size());
            assertEquals(-1, map.get(1L));
            assertEquals(20, map.get(2L));

            assertEquals(-1, map.remove(99L)); // Non-existent
        }
    }

    @Test
    void testClear() {
        try (OffHeapLongIntMap map = new OffHeapLongIntMapImpl(16)) {
            map.put(1L, 10);
            map.put(2L, 20);
            map.clear();

            assertEquals(0, map.size());
            assertTrue(map.isEmpty());
            assertEquals(-1, map.get(1L));
        }
    }

    @Test
    void testCollisionHandling() {
        try (OffHeapLongIntMap map = new OffHeapLongIntMapImpl(4)) {
            map.put(1L, 1);
            map.put(5L, 5); // Collision likely

            assertEquals(1, map.get(1L));
            assertEquals(5, map.get(5L));
            assertEquals(2, map.size());
        }
    }

    @Test
    void testCustomMissingValue() {
        try (OffHeapLongIntMap map = new OffHeapLongIntMapImpl(16, -999)) {
            assertEquals(-999, map.get(1L));
            assertEquals(-999, map.remove(1L));
        }
    }
}
