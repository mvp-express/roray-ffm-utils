package express.mvp.roray.ffm.collections;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

@SuppressWarnings("checkstyle:VariableDeclarationUsageDistance") // Test setup pattern
class IntrusiveLinkedListTest {

    @Test
    void testAddAndPoll() {
        try (Arena arena = Arena.ofConfined();
                IntrusiveLinkedList list = new IntrusiveLinkedListImpl()) {

            assertTrue(list.isEmpty());

            MemorySegment seg1 = arena.allocate(16);
            MemorySegment seg2 = arena.allocate(16);

            // Use offset 0 for the 'next' pointer
            list.add(seg1);
            list.add(seg2);

            assertEquals(2, list.size());
            assertFalse(list.isEmpty());

            // Poll should return in FIFO order
            MemorySegment polled1 = list.poll();
            assertEquals(seg1.address(), polled1.address());

            MemorySegment polled2 = list.poll();
            assertEquals(seg2.address(), polled2.address());

            assertTrue(list.isEmpty());
            assertNull(list.poll());
        }
    }

    @Test
    void testInterleavedOps() {
        try (Arena arena = Arena.ofConfined();
                IntrusiveLinkedList list = new IntrusiveLinkedListImpl()) {

            MemorySegment s1 = arena.allocate(8);
            MemorySegment s2 = arena.allocate(8);
            MemorySegment s3 = arena.allocate(8);

            list.add(s1);
            assertEquals(s1.address(), list.poll().address());
            assertTrue(list.isEmpty());

            list.add(s2);
            list.add(s3);
            assertEquals(s2.address(), list.poll().address());
            assertEquals(s3.address(), list.poll().address());
        }
    }
}
