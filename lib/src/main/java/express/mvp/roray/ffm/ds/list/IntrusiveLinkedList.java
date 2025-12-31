package express.mvp.roray.ffm.ds.list;

import java.lang.foreign.MemorySegment;

/**
 * An intrusive linked list where the "next" pointer is stored within the element's memory.
 *
 * <p>This avoids allocating a Node wrapper for every element. The element is represented by a
 * MemorySegment (or an offset within a segment).
 *
 * <p>Not thread-safe. Designed for single-threaded hot paths.
 */
public interface IntrusiveLinkedList extends AutoCloseable {

    /**
     * Adds an element to the tail of the list.
     *
     * @param segment The memory segment representing the element.
     * @return true if the element was added successfully.
     */
    boolean offer(MemorySegment segment);

    /**
     * Removes and returns the head of the list.
     *
     * @return The memory segment of the head, or null if empty.
     */
    MemorySegment poll();

    int size();

    boolean isEmpty();

    @Override
    void close();
}
