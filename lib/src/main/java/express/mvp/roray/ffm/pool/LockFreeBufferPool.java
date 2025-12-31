package express.mvp.roray.ffm.pool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import express.mvp.roray.ffm.concurrent.queue.RingBufferImpl;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Lock-free buffer pool using a ring buffer for index tracking.
 *
 * <p>This pool provides high-performance buffer acquisition without locks, using a ring buffer to
 * track free buffer indices.
 */
public class LockFreeBufferPool implements AutoCloseable {

    private final Arena arena;
    private final BufferRefImpl[] buffers;
    private final RingBufferImpl freeIndices;

    /**
     * Creates a new lock-free buffer pool.
     *
     * @param count number of buffers (must be a power of 2)
     * @param bufferSize size of each buffer in bytes
     */
    @SuppressFBWarnings(
            value = "CT_CONSTRUCTOR_THROW",
            justification = "Capacity validation is required for safe usage.")
    public LockFreeBufferPool(int count, int bufferSize) {
        if (Integer.bitCount(count) != 1) {
            throw new IllegalArgumentException("Pool count must be a power of 2");
        }
        this.arena = Arena.ofShared();
        this.buffers = new BufferRefImpl[count];
        this.freeIndices = new RingBufferImpl(count);

        MemorySegment slab = arena.allocate((long) count * bufferSize, 64);

        for (int i = 0; i < count; i++) {
            MemorySegment slice = slab.asSlice((long) i * bufferSize, bufferSize);
            buffers[i] = new BufferRefImpl(slice, i, this::returnToPool);
            if (!freeIndices.offer(i)) {
                throw new IllegalStateException(
                        "Free-index queue unexpectedly full during init, index=" + i);
            }
        }
    }

    /**
     * Acquires a buffer reference from the pool.
     *
     * @return a BufferRef with refCount=1, or {@code null} if the pool is empty
     */
    public BufferRef acquire() {
        int index = freeIndices.poll();
        if (index == -1) {
            return null;
        }
        BufferRefImpl buf = buffers[index];
        buf.reset();
        return buf;
    }

    private void returnToPool(int index) {
        // In a buffer pool, returning should not fail under correct usage.
        // Under extreme contention, a bounded queue may appear transiently full; spin
        // briefly.
        for (int attempts = 0; attempts < 1_000_000; attempts++) {
            if (freeIndices.offer(index)) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("Pool full, cannot return buffer " + index);
    }

    public int capacity() {
        return buffers.length;
    }

    public int available() {
        return freeIndices.size();
    }

    @Override
    public void close() {
        freeIndices.close();
        arena.close();
    }
}
