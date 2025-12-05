package express.mvp.roray.utils.concurrent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class MpscRingBufferTest {

    @Test
    void testPowerOfTwoCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new MpscRingBuffer<>(3));
        assertDoesNotThrow(() -> new MpscRingBuffer<>(4));
    }

    @Test
    void testOfferPollSingleThread() {
        MpscRingBuffer<Integer> queue = new MpscRingBuffer<>(4);

        assertTrue(queue.offer(1));
        assertTrue(queue.offer(2));
        assertTrue(queue.offer(3));
        assertTrue(queue.offer(4));
        assertFalse(queue.offer(5)); // Full

        assertEquals(1, queue.poll());
        assertEquals(2, queue.poll());
        assertEquals(3, queue.poll());
        assertEquals(4, queue.poll());
        assertNull(queue.poll()); // Empty
    }

    @Test
    void testWrapAround() {
        MpscRingBuffer<Integer> queue = new MpscRingBuffer<>(4);

        // Fill and empty
        for (int i = 0; i < 4; i++) {
            assertTrue(queue.offer(i));
        }
        for (int i = 0; i < 4; i++) {
            assertEquals(i, queue.poll());
        }

        // Fill again (indices should wrap/continue)
        for (int i = 0; i < 4; i++) {
            assertTrue(queue.offer(i + 10));
        }
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 10, queue.poll());
        }
    }

    @Test
    @Timeout(10)
    void testMultiProducerSingleConsumer() throws InterruptedException {
        int capacity = 1024;
        int producerCount = 4;
        int itemsPerProducer = 100_000;
        MpscRingBuffer<Integer> queue = new MpscRingBuffer<>(capacity);

        ExecutorService producers = Executors.newFixedThreadPool(producerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(producerCount);

        for (int i = 0; i < producerCount; i++) {
            final int producerId = i;
            producers.submit(
                    () -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < itemsPerProducer; j++) {
                                while (!queue.offer(producerId * itemsPerProducer + j)) {
                                    Thread.onSpinWait(); // Spin if full
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
        }

        startLatch.countDown();

        // Consumer
        Set<Integer> received = new HashSet<>();
        int totalItems = producerCount * itemsPerProducer;
        int count = 0;

        while (count < totalItems) {
            Integer item = queue.poll();
            if (item != null) {
                received.add(item);
                count++;
            } else {
                // If producers are done and queue is empty but we haven't got everything, something
                // is wrong
                if (doneLatch.getCount() == 0 && queue.isEmpty()) {
                    // Give it a moment, maybe race condition in test logic?
                    // But if queue is empty and producers done, we should have everything.
                    // Wait, poll() returning null means empty OR race.
                    // But if producers are done, no race on offer side.
                    // Just spin.
                }
                Thread.onSpinWait();
            }
        }

        doneLatch.await();
        producers.shutdown();

        assertEquals(totalItems, received.size());
    }
}
