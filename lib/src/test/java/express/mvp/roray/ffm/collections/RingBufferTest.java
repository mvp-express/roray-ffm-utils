package express.mvp.roray.ffm.collections;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RingBufferTest {

    @Test
    void testOfferAndPoll() {
        try (RingBuffer rb = new RingBufferImpl(4)) {
            assertTrue(rb.isEmpty());
            assertEquals(0, rb.size());
            assertEquals(4, rb.capacity());

            assertTrue(rb.offer(10));
            assertTrue(rb.offer(20));
            assertEquals(2, rb.size());
            assertFalse(rb.isEmpty());

            assertEquals(10, rb.poll());
            assertEquals(20, rb.poll());
            assertEquals(0, rb.size());
            assertTrue(rb.isEmpty());
        }
    }

    @Test
    void testFull() {
        try (RingBuffer rb = new RingBufferImpl(2)) {
            assertTrue(rb.offer(1));
            assertTrue(rb.offer(2));
            assertTrue(rb.isFull());
            assertFalse(rb.offer(3)); // Should fail

            assertEquals(1, rb.poll());
            assertFalse(rb.isFull());
            assertTrue(rb.offer(3)); // Should succeed now
        }
    }

    @Test
    void testWrapAround() {
        try (RingBuffer rb = new RingBufferImpl(4)) {
            // Fill
            rb.offer(1);
            rb.offer(2);
            rb.offer(3);
            rb.offer(4);
            assertTrue(rb.isFull());

            // Consume 2
            assertEquals(1, rb.poll());
            assertEquals(2, rb.poll());
            assertEquals(2, rb.size());

            // Add 2 more (wrapping around)
            assertTrue(rb.offer(5));
            assertTrue(rb.offer(6));
            assertTrue(rb.isFull());

            // Verify order
            assertEquals(3, rb.poll());
            assertEquals(4, rb.poll());
            assertEquals(5, rb.poll());
            assertEquals(6, rb.poll());
            assertTrue(rb.isEmpty());
        }
    }

    @Test
    void testPollEmpty() {
        try (RingBuffer rb = new RingBufferImpl(4)) {
            assertEquals(-1, rb.poll()); // Assuming -1 is sentinel
        }
    }
}
