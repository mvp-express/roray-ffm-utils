package express.mvp.roray.ffm.collections;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OffHeapLongObjectMapTest {

    @Test
    void testPutAndGet() {
        try (OffHeapLongObjectMap<String> map = new OffHeapLongObjectMapImpl<>(16)) {
            assertTrue(map.isEmpty());

            map.put(100L, "Value100");
            map.put(200L, "Value200");

            assertEquals(2, map.size());
            assertTrue(map.containsKey(100L));
            assertTrue(map.containsKey(200L));
            assertFalse(map.containsKey(300L));

            assertEquals("Value100", map.get(100L));
            assertEquals("Value200", map.get(200L));
            assertNull(map.get(300L));
        }
    }

    @Test
    void testUpdate() {
        try (OffHeapLongObjectMap<String> map = new OffHeapLongObjectMapImpl<>(16)) {
            map.put(1L, "A");
            assertEquals("A", map.get(1L));

            map.put(1L, "B");
            assertEquals("B", map.get(1L));
            assertEquals(1, map.size());
        }
    }

    @Test
    void testRemove() {
        try (OffHeapLongObjectMap<String> map = new OffHeapLongObjectMapImpl<>(16)) {
            map.put(1L, "A");
            map.put(2L, "B");

            assertEquals("A", map.remove(1L));
            assertEquals(1, map.size());
            assertNull(map.get(1L));
            assertEquals("B", map.get(2L));

            assertNull(map.remove(99L)); // Non-existent
        }
    }

    @Test
    void testClear() {
        try (OffHeapLongObjectMap<String> map = new OffHeapLongObjectMapImpl<>(16)) {
            map.put(1L, "A");
            map.put(2L, "B");
            map.clear();

            assertEquals(0, map.size());
            assertTrue(map.isEmpty());
            assertNull(map.get(1L));
        }
    }

    @Test
    void testCollisionHandling() {
        // This depends on implementation details (hash function), but we can try to force
        // collisions or just add enough items to trigger probing.
        try (OffHeapLongObjectMap<Integer> map = new OffHeapLongObjectMapImpl<>(4)) {
            map.put(1L, 1);
            map.put(5L, 5); // If size is 4, 1 and 5 might collide (1 % 4 == 1, 5 % 4 == 1)

            assertEquals(1, map.get(1L));
            assertEquals(5, map.get(5L));
            assertEquals(2, map.size());
        }
    }
}
