package express.mvp.roray.ffm.concurrent.queue;

/**
 * A primitive int-based ring buffer (circular queue) backed by off-heap memory.
 *
 * <p>Designed for zero-allocation passing of indices (pointers) between threads. Implementations
 * should support Single-Producer/Single-Consumer (SPSC) or Multi-Producer/Multi-Consumer (MPMC)
 * semantics as needed.
 */
public interface RingBuffer extends AutoCloseable {

    /**
     * Offers an item to the ring.
     *
     * @param item The item (index) to enqueue.
     * @return true if successful, false if full.
     */
    boolean offer(int item);

    /**
     * Polls an item from the ring.
     *
     * @return The item, or -1 (or a configured sentinel) if empty.
     */
    int poll();

    /** The total capacity of the ring. */
    int capacity();

    /** The current number of items in the ring. */
    int size();

    boolean isEmpty();

    boolean isFull();

    /** Releases the underlying off-heap memory. */
    @Override
    void close();
}
