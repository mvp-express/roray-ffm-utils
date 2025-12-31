package express.mvp.roray.ffm.ds.map;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import org.junit.jupiter.api.Test;

class OffHeapLongIntMapTest {

    private static boolean found(long packed) {
        return packed < 0;
    }

    private static int value(long packed) {
        return (int) packed;
    }

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

            long p1 = map.getPacked(100L);
            assertTrue(found(p1));
            assertEquals(10, value(p1));

            long p2 = map.getPacked(200L);
            assertTrue(found(p2));
            assertEquals(20, value(p2));

            assertFalse(found(map.getPacked(300L)));
        }
    }

    @Test
    void testUpdate() {
        try (OffHeapLongIntMap map = new OffHeapLongIntMapImpl(16)) {
            map.put(1L, 100);
            assertEquals(100, value(map.getPacked(1L)));

            map.put(1L, 200);
            assertEquals(200, value(map.getPacked(1L)));
            assertEquals(1, map.size());
        }
    }

    @Test
    void testRemove() {
        try (OffHeapLongIntMap map = new OffHeapLongIntMapImpl(16)) {
            map.put(1L, 10);
            map.put(2L, 20);

            long removed = map.removePacked(1L);
            assertTrue(found(removed));
            assertEquals(10, value(removed));
            assertEquals(1, map.size());
            assertFalse(found(map.getPacked(1L)));
            assertEquals(20, value(map.getPacked(2L)));

            assertFalse(found(map.removePacked(99L))); // Non-existent
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
            assertFalse(found(map.getPacked(1L)));
        }
    }

    @Test
    void testCollisionHandling() {
        try (OffHeapLongIntMap map = new OffHeapLongIntMapImpl(4)) {
            map.put(1L, 1);
            map.put(5L, 5); // Collision likely

            assertEquals(1, value(map.getPacked(1L)));
            assertEquals(5, value(map.getPacked(5L)));
            assertEquals(2, map.size());
        }
    }

    @Test
    void testCloseIsIdempotent() {
        OffHeapLongIntMap map = new OffHeapLongIntMapImpl(16);
        map.close();
        assertDoesNotThrow(map::close);
    }

    @Test
    void testUseAfterCloseThrows() {
        OffHeapLongIntMap map = new OffHeapLongIntMapImpl(16);
        map.put(1L, 10);
        map.close();

        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> map.getPacked(1L)),
                () -> assertThrows(IllegalStateException.class, () -> map.put(2L, 20)),
                () -> assertThrows(IllegalStateException.class, () -> map.removePacked(1L)),
                () -> assertThrows(IllegalStateException.class, map::clear),
                () -> assertThrows(IllegalStateException.class, map::size),
                () -> assertThrows(IllegalStateException.class, map::isEmpty),
                () -> assertThrows(IllegalStateException.class, () -> map.containsKey(1L)));
    }

    @Test
    void testCloseDoesNotCloseCallerOwnedArena() {
        Arena arena = Arena.ofShared();
        OffHeapLongIntMap map = new OffHeapLongIntMapImpl(16, arena);
        map.put(1L, 10);

        map.close();

        assertDoesNotThrow(() -> arena.allocate(8, 1));
        arena.close();
    }
}
