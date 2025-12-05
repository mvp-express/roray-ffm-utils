package express.mvp.roray.utils.concurrent;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * A high-performance, lock-free, off-heap friendly Multi-Producer Single-Consumer (MPSC) ring
 * buffer.
 *
 * <p><b>Design Principles:</b>
 *
 * <ul>
 *   <li><b>FFM-First:</b> Uses {@link MemorySegment} and {@link VarHandle} for safe, efficient
 *       memory access.
 *   <li><b>Zero-Allocation:</b> No objects allocated during {@link #offer(Object)} or {@link
 *       #poll()}.
 *   <li><b>False Sharing Protection:</b> Critical fields (producer/consumer indices) are padded to
 *       separate cache lines.
 *   <li><b>Mechanical Sympathy:</b> Optimized for modern CPU architectures with relaxed consistency
 *       models.
 * </ul>
 *
 * <p>This implementation is inspired by JCTools' MpscArrayQueue but built natively on Java 21+ FFM.
 *
 * @param <E> the type of elements held in this buffer
 */
public final class MpscRingBuffer<E> implements AutoCloseable {

    private static final VarHandle PRODUCER_INDEX;
    private static final VarHandle PRODUCER_LIMIT;
    private static final VarHandle CONSUMER_INDEX;
    private static final VarHandle ARRAY_HANDLE;

    static {
        try {
            PRODUCER_INDEX = ValueLayout.JAVA_LONG.varHandle();
            PRODUCER_LIMIT = ValueLayout.JAVA_LONG.varHandle();
            CONSUMER_INDEX = ValueLayout.JAVA_LONG.varHandle();
            ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Offsets for cache line padding (assuming 64-byte cache lines)
    // Layout:
    // [0-7]   producerIndex
    // [8-63]  padding
    // [64-71] producerLimit (cached consumer index for producers)
    // [72-127] padding
    // [128-135] consumerIndex
    // [136-191] padding
    private static final long P_INDEX_OFFSET = 0;
    private static final long P_LIMIT_OFFSET = 64;
    private static final long C_INDEX_OFFSET = 128;
    private static final long ALLOCATION_SIZE = 192;

    private final Arena arena;
    private final MemorySegment state;
    private final Object[] buffer;
    private final long mask;
    private final long capacity;

    /**
     * Creates a new MpscRingBuffer.
     *
     * @param capacity the capacity of the buffer (must be a power of 2)
     */
    public MpscRingBuffer(int capacity) {
        if (capacity < 2 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be a power of 2");
        }

        this.capacity = capacity;
        this.mask = capacity - 1;
        this.buffer = new Object[capacity];

        // Allocate off-heap memory for indices to ensure stable addresses and no GC interference
        this.arena = Arena.ofShared();
        this.state = arena.allocate(ALLOCATION_SIZE, 64); // 64-byte alignment

        // Initialize indices to 0
        state.fill((byte) 0);
        // Initial limit is capacity
        PRODUCER_LIMIT.set(state, P_LIMIT_OFFSET, (long) capacity);
    }

    /**
     * Offers an element to the queue.
     *
     * <p>This method is thread-safe for multiple producers.
     *
     * @param e the element to offer (must not be null)
     * @return true if successful, false if the queue is full
     */
    public boolean offer(E e) {
        if (e == null) {
            throw new IllegalArgumentException("Null elements not allowed");
        }

        long mask = this.mask;
        @SuppressWarnings("checkstyle:LocalVariableName")
        long pIndex;
        long producerLimit;

        do {
            pIndex = (long) PRODUCER_INDEX.getVolatile(state, P_INDEX_OFFSET);
            producerLimit = (long) PRODUCER_LIMIT.getVolatile(state, P_LIMIT_OFFSET);

            if (pIndex >= producerLimit) {
                // We think we are full, check the actual consumer index
                @SuppressWarnings("checkstyle:LocalVariableName")
                long cIndex = (long) CONSUMER_INDEX.getVolatile(state, C_INDEX_OFFSET);

                // Recalculate limit
                producerLimit = cIndex + capacity;

                if (pIndex >= producerLimit) {
                    return false; // FULL
                }

                // Update cached limit
                PRODUCER_LIMIT.setVolatile(state, P_LIMIT_OFFSET, producerLimit);
            }

            // Try to claim the slot
        } while (!PRODUCER_INDEX.compareAndSet(state, P_INDEX_OFFSET, pIndex, pIndex + 1));

        // We claimed the slot at pIndex.
        // Write the element with Release semantics to ensure visibility
        int offset = (int) (pIndex & mask);
        ARRAY_HANDLE.setRelease(buffer, offset, e);

        return true;
    }

    /**
     * Polls an element from the queue.
     *
     * <p>This method is NOT thread-safe and must be called by a single consumer.
     *
     * @return the element, or null if empty
     */
    @SuppressWarnings("unchecked")
    public E poll() {
        @SuppressWarnings("checkstyle:LocalVariableName")
        long cIndex = (long) CONSUMER_INDEX.getVolatile(state, C_INDEX_OFFSET);
        @SuppressWarnings("checkstyle:LocalVariableName")
        long pIndex = (long) PRODUCER_INDEX.getVolatile(state, P_INDEX_OFFSET);

        if (cIndex >= pIndex) {
            return null; // Empty
        }

        int offset = (int) (cIndex & mask);

        // Read with Acquire semantics
        Object e = ARRAY_HANDLE.getAcquire(buffer, offset);

        if (e == null) {
            // Race condition: Producer claimed slot but hasn't written yet.
            // We must spin-wait for the write to complete.
            // This is rare but possible in MPSC.
            do {
                Thread.onSpinWait();
                e = ARRAY_HANDLE.getAcquire(buffer, offset);
            } while (e == null);
        }

        // Clear the slot
        ARRAY_HANDLE.setRelease(buffer, offset, null);

        // Move consumer index
        CONSUMER_INDEX.setRelease(state, C_INDEX_OFFSET, cIndex + 1);

        return (E) e;
    }

    /**
     * Checks if the queue is empty.
     *
     * @return true if the queue is empty, false otherwise
     */
    @SuppressWarnings("checkstyle:LocalVariableName")
    public boolean isEmpty() {
        long cIndex = (long) CONSUMER_INDEX.getVolatile(state, C_INDEX_OFFSET);
        long pIndex = (long) PRODUCER_INDEX.getVolatile(state, P_INDEX_OFFSET);
        return cIndex >= pIndex;
    }

    @Override
    public void close() {
        arena.close();
    }
}
