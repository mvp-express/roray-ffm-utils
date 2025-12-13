package express.mvp.roray.ffm.utils.functions;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Provides convenient accessors for reading and writing struct fields in a {@link MemorySegment}.
 *
 * <p>This class creates {@link VarHandle}s for struct field access and provides type-safe
 * read/write methods. All setup costs are paid at construction time.
 *
 * <h2>Performance Patterns (Fastest to Slowest)</h2>
 *
 * <p>This class supports three access patterns with different performance characteristics. Choose
 * based on your performance requirements:
 *
 * <h3>Pattern 1: Extracted VarHandles (Fastest - ~40 ns/op)</h3>
 *
 * <p>For <b>hot paths</b> requiring maximum performance, extract VarHandles at class initialization
 * and use them directly. This achieves the same performance as hand-written VarHandle code.
 *
 * <pre>{@code
 * // At class level - extract once
 * private static final StructAccessor SQE = StructAccessor.of(IO_URING_SQE_LAYOUT);
 * private static final VarHandle OPCODE_VH = SQE.varHandle("opcode");
 * private static final VarHandle FD_VH = SQE.varHandle("fd");
 * private static final VarHandle USER_DATA_VH = SQE.varHandle("user_data");
 *
 * // In hot path - direct VarHandle access (~40 ns for single field)
 * void prepareOperation(MemorySegment sqe, byte opcode, int fd, long userData) {
 *     OPCODE_VH.set(sqe, 0L, opcode);
 *     FD_VH.set(sqe, 0L, fd);
 *     USER_DATA_VH.set(sqe, 0L, userData);
 * }
 * }</pre>
 *
 * <h3>Pattern 2: String-based Methods (Convenient - ~60-180 ns/op)</h3>
 *
 * <p>For <b>non-critical paths</b> where code clarity matters more than raw speed. Uses cached
 * VarHandles internally but has HashMap lookup overhead per call.
 *
 * <pre>{@code
 * StructAccessor sockaddr = StructAccessor.of(SOCKADDR_IN_LAYOUT);
 *
 * // Setup code - readable but ~1.5-4x slower than Pattern 1
 * sockaddr.setShort(addr, "sin_family", AF_INET);
 * sockaddr.setShort(addr, "sin_port", port);
 * sockaddr.setInt(addr, "sin_addr", ipAddress);
 * }</pre>
 *
 * <h3>Pattern 3: Offset-based Methods (Manual - ~40 ns/op)</h3>
 *
 * <p>For cases where you've pre-computed offsets. Same speed as Pattern 1 but requires manual
 * offset management.
 *
 * <pre>{@code
 * long userDataOffset = sqeAccessor.fieldOffset("user_data");
 * sqeAccessor.setLongAt(sqe, userDataOffset, value);
 * }</pre>
 *
 * <h2>Performance Benchmark Results</h2>
 *
 * <p>Measured on io_uring SQE struct (64 bytes) with JMH, Java 25:
 *
 * <table border="1">
 *   <tr><th>Pattern</th><th>Single Field</th><th>6 Fields (prepSend)</th></tr>
 *   <tr><td>Direct VarHandle (baseline)</td><td>~40 ns</td><td>~47 ns</td></tr>
 *   <tr><td>Pattern 1: Extracted VarHandles</td><td>~38 ns</td><td>~44 ns</td></tr>
 *   <tr><td>Pattern 2: String-based methods</td><td>~57 ns (+43%)</td><td>~179 ns (+280%)</td></tr>
 * </table>
 *
 * <h2>Basic Usage</h2>
 *
 * <pre>{@code
 * // Create accessor for sockaddr_in
 * StructAccessor sockaddrIn = StructAccessor.of(LinuxLayouts.SOCKADDR_IN);
 *
 * try (Arena arena = Arena.ofConfined()) {
 *     MemorySegment addr = sockaddrIn.allocate(arena);
 *
 *     sockaddrIn.setShort(addr, "sin_family", LinuxLayouts.AF_INET);
 *     sockaddrIn.setShort(addr, "sin_port", Short.reverseBytes((short) 8080));
 *     sockaddrIn.setInt(addr, "sin_addr", 0x7F000001); // 127.0.0.1
 * }
 * }</pre>
 *
 * <h2>Array of Structs</h2>
 *
 * <pre>{@code
 * StructAccessor iovec = StructAccessor.of(LinuxLayouts.IOVEC);
 * MemorySegment iovecs = iovec.allocateArray(arena, 3);
 *
 * for (int i = 0; i < 3; i++) {
 *     MemorySegment element = iovec.elementAt(iovecs, i);
 *     iovec.setPointer(element, "iov_base", buffers[i]);
 *     iovec.setLong(element, "iov_len", buffers[i].byteSize());
 * }
 * }</pre>
 *
 * @see #varHandle(String) For extracting VarHandles for hot paths (Pattern 1)
 * @see #setLong(MemorySegment, String, long) For convenient string-based access (Pattern 2)
 * @see #setLongAt(MemorySegment, long, long) For offset-based access (Pattern 3)
 */
public final class StructAccessor {

    private final StructLayout layout;
    private final Map<String, Long> fieldOffsets;
    private final Map<String, VarHandle> varHandles;

    private StructAccessor(StructLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.fieldOffsets = buildFieldOffsetCache(layout);
        this.varHandles = buildVarHandleCache(layout);
    }

    /**
     * Pre-computes and caches all field offsets at construction time. This eliminates the
     * layout.byteOffset() lookup cost on every field access.
     */
    private static Map<String, Long> buildFieldOffsetCache(StructLayout layout) {
        Map<String, Long> cache = new HashMap<>();
        for (MemoryLayout member : layout.memberLayouts()) {
            member.name()
                    .ifPresent(
                            name -> {
                                long offset =
                                        layout.byteOffset(
                                                MemoryLayout.PathElement.groupElement(name));
                                cache.put(name, offset);
                            });
        }
        return Map.copyOf(cache); // Immutable for thread-safety
    }

    /**
     * Pre-computes and caches VarHandles for all named fields at construction time. VarHandle
     * access is faster than segment.get/set with offset when JIT can inline.
     */
    private static Map<String, VarHandle> buildVarHandleCache(StructLayout layout) {
        Map<String, VarHandle> cache = new HashMap<>();
        for (MemoryLayout member : layout.memberLayouts()) {
            member.name()
                    .ifPresent(
                            name -> {
                                try {
                                    VarHandle vh =
                                            layout.varHandle(
                                                    MemoryLayout.PathElement.groupElement(name));
                                    cache.put(name, vh);
                                } catch (IllegalArgumentException e) {
                                    // Skip fields that can't have VarHandles (e.g., nested structs,
                                    // padding)
                                }
                            });
        }
        return Map.copyOf(cache); // Immutable for thread-safety
    }

    /**
     * Creates a struct accessor for the given layout.
     *
     * @param layout The struct layout
     * @return A new StructAccessor
     */
    public static StructAccessor of(StructLayout layout) {
        return new StructAccessor(layout);
    }

    /** Gets the underlying struct layout. */
    public StructLayout layout() {
        return layout;
    }

    /** Gets the byte size of the struct. */
    public long byteSize() {
        return layout.byteSize();
    }

    /**
     * Allocates a new instance of this struct.
     *
     * @param arena Arena to allocate from
     * @return A zeroed memory segment of the struct's size
     */
    public MemorySegment allocate(Arena arena) {
        return arena.allocate(layout);
    }

    /**
     * Allocates an array of structs.
     *
     * @param arena Arena to allocate from
     * @param count Number of struct instances
     * @return Memory segment for the array
     */
    public MemorySegment allocateArray(Arena arena, long count) {
        return arena.allocate(layout, count);
    }

    // ========================================================================
    // Field offset lookup
    // ========================================================================

    /**
     * Gets the byte offset of a named field.
     *
     * <p>This method uses a pre-computed cache for O(1) lookup time.
     *
     * @param fieldName Name of the field
     * @return Byte offset from struct start
     * @throws IllegalArgumentException if field not found
     */
    public long fieldOffset(String fieldName) {
        Long offset = fieldOffsets.get(fieldName);
        if (offset == null) {
            throw new IllegalArgumentException(
                    "Unknown field: "
                            + fieldName
                            + " in "
                            + layout.name().orElse("unnamed struct"));
        }
        return offset;
    }

    // ========================================================================
    // VarHandle access (Pattern 1 - fastest for hot paths)
    // ========================================================================

    /**
     * Gets the cached VarHandle for a field.
     *
     * <p><b>This is the recommended pattern for hot paths.</b> Extract VarHandles at class
     * initialization time and use them directly for maximum performance (~40 ns/op).
     *
     * <h3>Usage Pattern</h3>
     *
     * <pre>{@code
     * // Extract at class initialization (one-time cost)
     * private static final StructAccessor SQE = StructAccessor.of(IO_URING_SQE_LAYOUT);
     * private static final VarHandle OPCODE_VH = SQE.varHandle("opcode");
     * private static final VarHandle FD_VH = SQE.varHandle("fd");
     * private static final VarHandle ADDR_VH = SQE.varHandle("addr");
     * private static final VarHandle LEN_VH = SQE.varHandle("len");
     * private static final VarHandle USER_DATA_VH = SQE.varHandle("user_data");
     *
     * // In hot path - direct VarHandle calls, JIT can fully inline
     * void prepSend(MemorySegment sqe, int fd, long bufAddr, int len, long userData) {
     *     OPCODE_VH.set(sqe, 0L, IORING_OP_SEND);
     *     FD_VH.set(sqe, 0L, fd);
     *     ADDR_VH.set(sqe, 0L, bufAddr);
     *     LEN_VH.set(sqe, 0L, len);
     *     USER_DATA_VH.set(sqe, 0L, userData);
     * }
     * }</pre>
     *
     * <h3>Performance</h3>
     *
     * <ul>
     *   <li>Single field access: ~38 ns (same as hand-written VarHandle code)
     *   <li>6-field operation: ~44 ns
     *   <li>Overhead vs direct VarHandle: &lt;5% (within measurement noise)
     * </ul>
     *
     * @param fieldName Name of the field
     * @return VarHandle for the field, suitable for storing in a static final field
     * @throws IllegalArgumentException if field not found or has no VarHandle
     */
    public VarHandle varHandle(String fieldName) {
        VarHandle vh = varHandles.get(fieldName);
        if (vh == null) {
            throw new IllegalArgumentException(
                    "No VarHandle for field: "
                            + fieldName
                            + " in "
                            + layout.name().orElse("unnamed struct"));
        }
        return vh;
    }

    // ========================================================================
    // String-based field accessors (Pattern 2 - convenient for non-critical paths)
    // ========================================================================

    /**
     * Reads a byte field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public byte getByte(MemorySegment segment, String fieldName) {
        return (byte) varHandle(fieldName).get(segment, 0L);
    }

    /**
     * Writes a byte field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public void setByte(MemorySegment segment, String fieldName, byte value) {
        varHandle(fieldName).set(segment, 0L, value);
    }

    /**
     * Reads a short field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public short getShort(MemorySegment segment, String fieldName) {
        return (short) varHandle(fieldName).get(segment, 0L);
    }

    /**
     * Writes a short field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public void setShort(MemorySegment segment, String fieldName, short value) {
        varHandle(fieldName).set(segment, 0L, value);
    }

    /**
     * Reads an int field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public int getInt(MemorySegment segment, String fieldName) {
        return (int) varHandle(fieldName).get(segment, 0L);
    }

    /**
     * Writes an int field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public void setInt(MemorySegment segment, String fieldName, int value) {
        varHandle(fieldName).set(segment, 0L, value);
    }

    /**
     * Reads a long field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public long getLong(MemorySegment segment, String fieldName) {
        return (long) varHandle(fieldName).get(segment, 0L);
    }

    /**
     * Writes a long field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public void setLong(MemorySegment segment, String fieldName, long value) {
        varHandle(fieldName).set(segment, 0L, value);
    }

    /**
     * Reads a float field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public float getFloat(MemorySegment segment, String fieldName) {
        return (float) varHandle(fieldName).get(segment, 0L);
    }

    /**
     * Writes a float field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public void setFloat(MemorySegment segment, String fieldName, float value) {
        varHandle(fieldName).set(segment, 0L, value);
    }

    /**
     * Reads a double field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public double getDouble(MemorySegment segment, String fieldName) {
        return (double) varHandle(fieldName).get(segment, 0L);
    }

    /**
     * Writes a double field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public void setDouble(MemorySegment segment, String fieldName, double value) {
        varHandle(fieldName).set(segment, 0L, value);
    }

    /**
     * Reads a pointer (address) field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public MemorySegment getPointer(MemorySegment segment, String fieldName) {
        return (MemorySegment) varHandle(fieldName).get(segment, 0L);
    }

    /**
     * Writes a pointer (address) field by name.
     *
     * <p><b>Performance:</b> ~57 ns/op (1.4x slower than extracted VarHandle). For hot paths, use
     * {@link #varHandle(String)} instead.
     */
    public void setPointer(MemorySegment segment, String fieldName, MemorySegment value) {
        varHandle(fieldName).set(segment, 0L, value);
    }

    // ========================================================================
    // Offset-based accessors (Pattern 3 - for pre-computed offsets)
    // ========================================================================

    /**
     * Reads an int at the given offset.
     *
     * <p><b>Performance:</b> ~40 ns/op (same as extracted VarHandle). Use when you've pre-computed
     * the offset via {@link #fieldOffset(String)}.
     */
    public int getIntAt(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT, offset);
    }

    /**
     * Writes an int at the given offset.
     *
     * <p><b>Performance:</b> ~40 ns/op (same as extracted VarHandle). Use when you've pre-computed
     * the offset via {@link #fieldOffset(String)}.
     */
    public void setIntAt(MemorySegment segment, long offset, int value) {
        segment.set(ValueLayout.JAVA_INT, offset, value);
    }

    /**
     * Reads a long at the given offset.
     *
     * <p><b>Performance:</b> ~40 ns/op (same as extracted VarHandle). Use when you've pre-computed
     * the offset via {@link #fieldOffset(String)}.
     */
    public long getLongAt(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_LONG, offset);
    }

    /**
     * Writes a long at the given offset.
     *
     * <p><b>Performance:</b> ~40 ns/op (same as extracted VarHandle). Use when you've pre-computed
     * the offset via {@link #fieldOffset(String)}.
     */
    public void setLongAt(MemorySegment segment, long offset, long value) {
        segment.set(ValueLayout.JAVA_LONG, offset, value);
    }

    /**
     * Reads a pointer at the given offset.
     *
     * <p><b>Performance:</b> ~40 ns/op (same as extracted VarHandle). Use when you've pre-computed
     * the offset via {@link #fieldOffset(String)}.
     */
    public MemorySegment getPointerAt(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.ADDRESS, offset);
    }

    /**
     * Writes a pointer at the given offset.
     *
     * <p><b>Performance:</b> ~40 ns/op (same as extracted VarHandle). Use when you've pre-computed
     * the offset via {@link #fieldOffset(String)}.
     */
    public void setPointerAt(MemorySegment segment, long offset, MemorySegment value) {
        segment.set(ValueLayout.ADDRESS, offset, value);
    }

    // ========================================================================
    // Array element access
    // ========================================================================

    /**
     * Gets a slice representing the nth element in an array of structs.
     *
     * @param arraySegment The array segment
     * @param index Element index (0-based)
     * @return Slice representing the struct at that index
     */
    public MemorySegment elementAt(MemorySegment arraySegment, long index) {
        return arraySegment.asSlice(index * layout.byteSize(), layout.byteSize());
    }
}
