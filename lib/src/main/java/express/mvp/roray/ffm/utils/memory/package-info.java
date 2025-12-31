/**
 * Binary, zero-copy codecs built on the Java Foreign Function &amp; Memory API.
 *
 * <p>This package provides sequential readers and writers over {@link
 * java.lang.foreign.MemorySegment} for high-throughput encoding/decoding without intermediate heap
 * copies.
 *
 * <h2>Core Utilities</h2>
 *
 * <ul>
 *   <li>{@link express.mvp.roray.ffm.utils.memory.BinaryReader} - Sequential, zero-copy decoding
 *       API for primitives, VarInts, strings, and slices
 *   <li>{@link express.mvp.roray.ffm.utils.memory.BinaryWriter} - Sequential, fluent, zero-copy
 *       encoding API for primitives, VarInts, strings, and slices
 *   <li>{@link express.mvp.roray.ffm.utils.memory.SegmentBinaryReader} - MemorySegment-backed
 *       {@link express.mvp.roray.ffm.utils.memory.BinaryReader} implementation
 *   <li>{@link express.mvp.roray.ffm.utils.memory.SegmentBinaryWriter} - MemorySegment-backed
 *       {@link express.mvp.roray.ffm.utils.memory.BinaryWriter} implementation
 * </ul>
 *
 * <h2>Flyweight &amp; Views</h2>
 *
 * <ul>
 *   <li>{@link express.mvp.roray.ffm.utils.memory.FlyweightAccessor} - Contract for reusable
 *       flyweights that wrap a segment and expose structured field access
 *   <li>{@link express.mvp.roray.ffm.utils.memory.Utf8View} - Zero-allocation flyweight view over a
 *       UTF-8 slice of a {@link java.lang.foreign.MemorySegment}
 *   <li>{@link express.mvp.roray.ffm.utils.memory.BitSetView} - Zero-allocation BitSet-like view
 *       for bit operations over a segment slice
 * </ul>
 *
 * <h2>Layouts &amp; Helpers</h2>
 *
 * <ul>
 *   <li>{@link express.mvp.roray.ffm.utils.memory.Layouts} - Predefined unaligned, endian-aware
 *       {@link java.lang.foreign.ValueLayout} constants for packed IO
 *   <li>{@link express.mvp.roray.ffm.utils.memory.SegmentUtils} - Stateless utilities for operating
 *       on {@link java.lang.foreign.MemorySegment}s (e.g., CRC32)
 *   <li>{@link express.mvp.roray.ffm.utils.memory.VarFieldWriter} - Builder for messages with
 *       variable-length fields addressed by offset/length headers
 * </ul>
 *
 * <h2>Pooling</h2>
 *
 * <ul>
 *   <li>{@link express.mvp.roray.ffm.utils.memory.MemorySegmentPool} - Thread-safe pool for
 *       reusable off-heap segments to reduce allocation and GC pressure
 *   <li>{@link express.mvp.roray.ffm.utils.memory.PooledSegment} - Try-with-resources wrapper that
 *       returns a segment to its pool on close
 * </ul>
 *
 * <h2>Conventions</h2>
 *
 * <ul>
 *   <li>Endianness suffixes: {@code BE} for big-endian (network byte order) and {@code LE} for
 *       little-endian.
 *   <li>Variable-length integers: {@code VarInt} (32-bit) and {@code VarLong} (64-bit) use a 7-bit
 *       continuation encoding.
 *   <li>Strings and byte arrays are typically length-prefixed with a {@code VarInt}. For
 *       allocation-free string decoding, populate a {@link
 *       express.mvp.roray.ffm.utils.memory.Utf8View} instead of creating a {@link
 *       java.lang.String}.
 *   <li>Some APIs can return a slice view ({@link java.lang.foreign.MemorySegment}) to avoid
 *       copying data to the heap.
 * </ul>
 *
 * @see express.mvp.roray.ffm.utils.memory.BinaryReader
 * @see express.mvp.roray.ffm.utils.memory.BinaryWriter
 */
package express.mvp.roray.ffm.utils.memory;
