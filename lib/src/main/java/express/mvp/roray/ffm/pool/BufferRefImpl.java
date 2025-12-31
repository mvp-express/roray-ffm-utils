package express.mvp.roray.ffm.pool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.IntConsumer;

/** Lock-free implementation of {@link BufferRef} with atomic reference counting. */
public final class BufferRefImpl implements BufferRef {

    private static final VarHandle REF_COUNT_VH;

    static {
        try {
            REF_COUNT_VH =
                    MethodHandles.lookup()
                            .findVarHandle(BufferRefImpl.class, "refCount", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final MemorySegment segment;
    private final long address;
    private final int poolIndex;
    private final IntConsumer releaseAction;

    private volatile int refCount;
    private volatile boolean inPool;
    private volatile int length;

    /**
     * Creates a new buffer reference for a pooled segment.
     *
     * @param segment the pooled memory segment
     * @param poolIndex the index used by the pool
     * @param releaseAction callback invoked when the buffer returns to the pool
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Stores MemorySegment for zero-copy access by design.")
    public BufferRefImpl(MemorySegment segment, int poolIndex, IntConsumer releaseAction) {
        this.segment = segment;
        this.address = segment.address();
        this.poolIndex = poolIndex;
        this.releaseAction = releaseAction;
        this.refCount = 0;
        this.inPool = true;
    }

    /** Resets this buffer for reuse after acquisition from pool (refCount 0 -> 1). */
    public void reset() {
        if (!REF_COUNT_VH.compareAndSet(this, 0, 1)) {
            int current = refCount;
            throw new IllegalStateException(
                    "Buffer reset() called but refCount was "
                            + current
                            + " (expected 0), poolIndex="
                            + poolIndex);
        }
        if (!inPool) {
            throw new IllegalStateException(
                    "Buffer reset() called but inPool=false, poolIndex=" + poolIndex);
        }
        this.inPool = false;
        this.length = 0;
    }

    @Override
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Exposes MemorySegment handles intentionally for zero-copy access.")
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public long address() {
        return address;
    }

    @Override
    public int poolIndex() {
        return poolIndex;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public void length(int newLength) {
        this.length = newLength;
    }

    @Override
    public void retain() {
        int c;
        do {
            c = refCount;
            if (c <= 0) {
                throw new IllegalStateException(
                        "Cannot retain a released buffer (refCount=" + c + ")");
            }
        } while (!REF_COUNT_VH.compareAndSet(this, c, c + 1));
    }

    @Override
    public void release() {
        int c;
        do {
            c = refCount;
            if (c <= 0) {
                throw new IllegalStateException(
                        "Buffer already released (refCount=" + c + "), poolIndex=" + poolIndex);
            }
        } while (!REF_COUNT_VH.compareAndSet(this, c, c - 1));

        if (c == 1) {
            if (inPool) {
                throw new IllegalStateException(
                        "Buffer release() would return to pool but inPool=true, poolIndex="
                                + poolIndex);
            }
            this.inPool = true;
            releaseAction.accept(poolIndex);
        }
    }

    @Override
    public String toString() {
        return "BufferRef{idx=" + poolIndex + ", len=" + length + ", ref=" + refCount + "}";
    }
}
