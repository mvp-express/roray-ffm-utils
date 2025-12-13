package express.mvp.roray.ffm.collections;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * An intrusive linked list implementation using off-heap memory segments.
 *
 * <p>This implementation uses the memory segment's own memory to store the next pointer, avoiding
 * separate node allocations.
 */
public class IntrusiveLinkedListImpl implements IntrusiveLinkedList {

    private final long nextOffset;
    private long head = 0;
    private long tail = 0;
    private int size = 0;

    public IntrusiveLinkedListImpl(long nextOffset) {
        this.nextOffset = nextOffset;
    }

    public IntrusiveLinkedListImpl() {
        this(0);
    }

    @Override
    public void add(MemorySegment segment) {
        long addr = segment.address();
        if (addr == 0) {
            throw new IllegalArgumentException("Segment address is 0");
        }

        // Set next pointer of new segment to 0 (NULL)
        segment.set(ValueLayout.JAVA_LONG, nextOffset, 0L);

        if (tail == 0) {
            head = addr;
            tail = addr;
        } else {
            // Link current tail to new segment
            MemorySegment tailSeg = MemorySegment.ofAddress(tail).reinterpret(nextOffset + 8);
            tailSeg.set(ValueLayout.JAVA_LONG, nextOffset, addr);
            tail = addr;
        }
        size++;
    }

    @Override
    public MemorySegment poll() {
        if (head == 0) {
            return null;
        }

        long currentHead = head;
        // Read next pointer from current head
        MemorySegment headSeg = MemorySegment.ofAddress(currentHead).reinterpret(nextOffset + 8);
        long next = headSeg.get(ValueLayout.JAVA_LONG, nextOffset);

        head = next;
        if (head == 0) {
            tail = 0;
        }
        size--;

        // Return a segment wrapping the address.
        // WARNING: This segment has 0 size and global scope (or similar).
        // The caller is expected to know the size or reinterpret it.
        return MemorySegment.ofAddress(currentHead);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void close() {
        // Nothing to close as we don't own the segments
    }
}
