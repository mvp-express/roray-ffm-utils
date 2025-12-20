package express.mvp.roray.ffm.concurrent.queue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * An MPMC (Multi-Producer Multi-Consumer) lock-free ring buffer.
 *
 * <p>
 * Uses a sequence buffer to ensure consistency of slots.
 */
public class RingBufferImpl implements RingBuffer {

    private static final VarHandle HEAD_VH;
    private static final VarHandle TAIL_VH;
    private static final VarHandle SEQ_VH;
    private static final VarHandle BUF_VH;

    static {
        try {
            HEAD_VH = MethodHandles.lookup().findVarHandle(RingBufferImpl.class, "head", long.class);
            TAIL_VH = MethodHandles.lookup().findVarHandle(RingBufferImpl.class, "tail", long.class);
            SEQ_VH = ValueLayout.JAVA_LONG.arrayElementVarHandle();
            BUF_VH = ValueLayout.JAVA_INT.arrayElementVarHandle();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final Arena arena;
    private final MemorySegment buffer;
    private final MemorySegment sequences;
    private final int capacity;
    private final int mask;

    // Padding fields for false-sharing prevention
    @SuppressWarnings({ "unused", "checkstyle:MultipleVariableDeclarations" })
    private long p01, p02, p03, p04, p05, p06, p07;
    @SuppressWarnings("unused")
    private volatile long head;
    @SuppressWarnings({ "unused", "checkstyle:MultipleVariableDeclarations" })
    private long p08, p09, p10, p11, p12, p13, p14;
    @SuppressWarnings("unused")
    private volatile long tail;
    @SuppressWarnings({ "unused", "checkstyle:MultipleVariableDeclarations" })
    private long p15, p16, p17, p18, p19, p20, p21;

    /**
     * Creates a new ring buffer with the specified capacity.
     *
     * @param capacity the capacity (must be a power of 2)
     */
    public RingBufferImpl(int capacity) {
        if (Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("Capacity must be a power of 2");
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.arena = Arena.ofShared();
        this.buffer = arena.allocate(ValueLayout.JAVA_INT, capacity);
        this.sequences = arena.allocate(ValueLayout.JAVA_LONG, capacity);

        for (int i = 0; i < capacity; i++) {
            sequences.setAtIndex(ValueLayout.JAVA_LONG, i, i);
        }

        this.head = 0;
        this.tail = 0;
    }

    @Override
    public boolean offer(int item) {
        long currentTail;
        long seq;
        do {
            currentTail = (long) TAIL_VH.getAcquire(this);
            long index = currentTail & mask;

            seq = (long) SEQ_VH.getAcquire(sequences, 0L, index);
            long dif = seq - currentTail;

            if (dif < 0) {
                return false;
            } else if (dif > 0) {
                continue;
            } else {
                if (TAIL_VH.compareAndSet(this, currentTail, currentTail + 1)) {
                    BUF_VH.set(buffer, 0L, index, item);
                    SEQ_VH.setRelease(sequences, 0L, index, currentTail + 1);
                    return true;
                }
            }
        } while (true);
    }

    @Override
    public int poll() {
        long currentHead;
        long seq;
        do {
            currentHead = (long) HEAD_VH.getAcquire(this);
            long index = currentHead & mask;

            seq = (long) SEQ_VH.getAcquire(sequences, 0L, index);
            long dif = seq - (currentHead + 1);

            if (dif < 0) {
                return -1;
            } else if (dif > 0) {
                continue;
            } else {
                if (HEAD_VH.compareAndSet(this, currentHead, currentHead + 1)) {
                    int item = (int) BUF_VH.get(buffer, 0L, index);
                    SEQ_VH.setRelease(sequences, 0L, index, currentHead + capacity);
                    return item;
                }
            }
        } while (true);
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int size() {
        long currentTail = (long) TAIL_VH.getAcquire(this);
        long currentHead = (long) HEAD_VH.getAcquire(this);
        long s = currentTail - currentHead;
        if (s <= 0) {
            return 0;
        }
        if (s >= capacity) {
            return capacity;
        }
        return (int) s;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean isFull() {
        return size() == capacity;
    }

    @Override
    public void close() {
        arena.close();
    }
}
