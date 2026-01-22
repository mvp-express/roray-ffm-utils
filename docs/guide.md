# Roray FFM Utils — Usage Guide

This document explains how to use the public APIs in `roray-ffm`. It assumes familiarity with the Java Foreign Function & Memory API introduced in Java 14 and refined in Java 22+. All examples compile against JDK 25+ with the preview FFM API enabled.

## Prerequisites

- JDK 25 or newer (the project targets 25+ and configures the toolchain for JDK 25).
- Preview features enabled when compiling or running (e.g. `--enable-preview`).
- Gradle 9+ or equivalent build tooling.
- Opt-in to the FFM API in runtime arguments when required (for long-running apps use `--enable-native-access=ALL-UNNAMED`).

## Adding the Library to Your Build

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("express.mvp:roray-ffm:0.2.1")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'express.mvp:roray-ffm:0.2.1'
}
```

### Maven

```xml
<dependency>
    <groupId>express.mvp</groupId>
    <artifactId>roray-ffm</artifactId>
    <version>0.2.1</version>
</dependency>
```

> **Tip:** When running or testing, pass `--enable-preview --enable-native-access=ALL-UNNAMED` to the JVM.

## Memory Lifecycle Strategies

The library is designed for zero-GC workflows that reuse off-heap buffers.

1. **Arena scope**: Use `Arena.ofConfined()` for per-thread lifecycles or `Arena.ofAuto()` for shared lifetimes managed by reference counting.
2. **Segment pooling**: Use `MemorySegmentPool` to acquire and recycle fixed-size segments without repeated heap pressure.
3. **Reusable codecs**: Reuse instances of `SegmentBinaryWriter`, `SegmentBinaryReader`, and `Utf8View` across invocations on the same thread.
4. **Scratch buffers**: Allocate one scratch `MemorySegment` per thread for zero-allocation string encoding.

The sections that follow walk through each API in detail.

---

## MemorySegmentPool

`MemorySegmentPool` offers a thread-safe pool of fixed-size segments allocated from an auto-managed `Arena`. It reduces the cost of creating fresh segments in hot paths.

### Construction and Sizing

```java
int segmentSize = 4096;      // bytes per segment
int initialSize = 32;        // eagerly allocated
int maxSize = 128;           // hard cap
MemorySegmentPool pool = new MemorySegmentPool(segmentSize, initialSize, maxSize);

// Or with configurable zeroing
boolean zeroOnRelease = false;  // Skip zeroing for performance (if safe)
MemorySegmentPool fastPool = new MemorySegmentPool(segmentSize, initialSize, maxSize, zeroOnRelease);
```

- `segmentSize > 0` defines the capacity of each pooled segment.
- `initialSize >= 0` determines how many segments are created up-front and made available.
- `maxSize > 0` is the upper bound on total segments (pooled + checked-out). Exceeding this raises an `IllegalStateException`.
- `zeroOnRelease` (default: `true`) controls whether released segments are zero-filled before returning to pool. Disable only if you have application-level security guarantees.

### Acquiring and Releasing Segments

```java
MemorySegment segment = pool.acquire(); // fixed-size segment
try {
    // Use segment directly or wrap in reader/writer
} finally {
    pool.release(segment);              // zeroes and returns to pool
}
```

`acquire()` returns a zeroed, fixed-size segment from the pool if available, otherwise it allocates a new one (respecting `maxSize`).

Release semantics:

- Only segments whose `byteSize()` equals `segmentSize` are returned to the pool.
- Released segments are zero-filled (`segment.fill((byte) 0)`) before re-enqueueing to mitigate data leakage between borrowers.

### Variable-Size Requests

```java
long largeSize = 32_768;
MemorySegment big = pool.acquire(largeSize);
try {
    // big.byteSize() == largeSize, never pooled on release
} finally {
    pool.release(big); // ignored, because size != segmentSize
}
```

`acquire(long requiredSize)` returns a fixed-size pooled segment when `requiredSize <= segmentSize` and allocates an on-demand segment otherwise. Oversized segments bypass pooling on release.

### Pool Introspection and Metrics

```java
int available = pool.getAvailableCount(); // size of the internal queue
int total     = pool.getTotalCount();     // segments ever allocated up to maxSize
long capacity = pool.getSegmentSize();    // configured per-segment capacity

// New metrics for monitoring
long totalAllocations = pool.getTotalAllocations();  // Lifetime allocation count
int currentlyInUse = pool.getCurrentlyInUse();       // Currently checked-out segments
int peakUsage = pool.getPeakUsage();                 // Maximum concurrent usage observed
```

These metrics are safe to call concurrently and aid in operational monitoring. Use `getPeakUsage()` to understand maximum concurrency requirements and size your pool accordingly.

### Concurrent Usage Pattern

```java
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
SegmentBinaryWriter writer = new SegmentBinaryWriter();
SegmentBinaryReader reader = new SegmentBinaryReader();
Utf8View view = new Utf8View();
MemorySegment scratch = Arena.ofAuto().allocate(4096);

Runnable task = () -> {
    MemorySegment buffer = pool.acquire();
    try {
        writer.wrap(buffer)
              .writeIntBE(Thread.currentThread().hashCode())
              .writeString("payload", scratch);

        reader.wrap(buffer);
        int hash = reader.readIntBE();
        reader.readString(view);
        if (!view.equalsString("payload")) {
            throw new IllegalStateException("Corrupted message");
        }
    } finally {
        pool.release(buffer);
    }
};

executor.submit(task);
```

The pool is safe for use with platform or virtual threads.

---

## SegmentBinaryWriter & BinaryWriter

`SegmentBinaryWriter` is the canonical implementation of the `BinaryWriter` interface. It encodes values into a wrapped `MemorySegment` using fluent, chainable methods. Every method returns `this` so you can compose writes.

### Preparing the Writer

```java
SegmentBinaryWriter writer = new SegmentBinaryWriter();
MemorySegment payload = Arena.ofConfined().allocate(8192);
writer.wrap(payload);             // resets position to 0
long bytesLeft = writer.remaining();
writer.position(128);             // jump ahead (throws IndexOutOfBounds if > byteSize)
```

- `wrap(MemorySegment)` must be called before writing; it sets the internal segment and resets the write position to `0`.
- `position()` and `position(long)` report or update the cursor. Bounds are enforced.
- `remaining()` reports `segment.byteSize() - position`.
- `skip(long)` advances the cursor without writing data.

### Writing Big-Endian Primitives

```java
writer.wrap(payload)
      .writeByte((byte) 0x7F)
      .writeBoolean(true)            // encoded as 1 byte: 1 for true, 0 for false
      .writeShortBE((short) -12345)
      .writeIntBE(0xDEADBEEF)
      .writeLongBE(0x0102030405060708L)
      .writeFloatBE(3.14159f)
      .writeDoubleBE(Math.PI);
```

These methods rely on value layouts defined in `Layouts`. They increment the cursor by the width of the primitive.

### Writing Little-Endian Primitives

```java
writer.wrap(payload)
      .writeShortLE((short) 0xABCD)
      .writeIntLE(42)
      .writeLongLE(123_456_789L)
      .writeFloatLE(Float.MIN_VALUE)
      .writeDoubleLE(Double.MAX_VALUE);
```

Little-endian writes mirror the big-endian variants so you can match wire formats or packed structs.

### Variable-Length Integers (Writing)

```java
writer.wrap(payload)
      .writeVarInt(127)          // 1 byte
      .writeVarInt(16_384)       // 3 bytes
      .writeVarLong(9_223_372_036_854_775_807L); // up to 10 bytes
```

- `writeVarInt(int value)` encodes 32-bit values using a LEB128-like scheme.
- `writeVarLong(long value)` does the same for 64-bit values.

These are ideal for small numbers and length prefixes.

> **Note:** These methods emit unsigned varints. Negative inputs are still supported but will consume the full 5/10-byte width unless you zig-zag them first. A simple helper:

```java
static int zigZagEncodeInt(int value) {
    return (value << 1) ^ (value >> 31);
}

static int zigZagDecodeInt(int value) {
    return (value >>> 1) ^ -(value & 1);
}
```

### Strings and Byte Arrays (Zero-Copy Friendly)

```java
MemorySegment scratch = Arena.ofConfined().allocate(4096);
writer.wrap(payload)
      .writeString("ASCII", scratch)
      .writeString("你好", scratch)      // multi-byte UTF-8
      .writeBytes(new byte[] {1, 2, 3, 4});
```

- `writeString(String value, MemorySegment scratchBuffer)` manually encodes UTF-8 bytes into the provided scratch buffer, writes a VarInt length prefix, and copies the bytes into the target segment.
- Scratch buffers must be large enough to hold the encoded bytes; reuse one buffer per thread to avoid allocations.
- `writeBytes(byte[] value)` writes a VarInt-prefixed heap array without requiring a scratch buffer.

### Nullable Encodings

Every nullable variant writes a 1-byte presence flag (`true` for present, `false` for `null`) and then delegates to the non-null variant.

```java
writer.wrap(payload)
      .writeNullableBoolean(Boolean.TRUE)
      .writeNullableBoolean(null)
      .writeNullableByte((byte) 42)
      .writeNullableShortBE((short) 1024)
      .writeNullableIntBE(123)
      .writeNullableIntLE(456)
      .writeNullableLongBE(123L)
      .writeNullableLongLE(456L)
      .writeNullableFloatBE(1.0f)
      .writeNullableFloatLE(2.0f)
      .writeNullableDoubleBE(Math.E)
      .writeNullableDoubleLE(null)
      .writeNullableString("optional", scratch)
      .writeNullableString(null, scratch)
      .writeNullableBytes(new byte[] {9, 9})
      .writeNullableBytes(null);
```

These pair with the corresponding `readNullable*` methods in `SegmentBinaryReader`.

### Bulk Segment Copies

```java
MemorySegment source = Arena.ofConfined().allocate(256);
source.set(Layouts.INT_LE, 0, 0x12345678);
writer.wrap(payload).writeSegment(source);
```

`writeSegment(MemorySegment source)` writes a VarLong length prefix followed by the raw bytes of the source segment.

### Position Control

```java
writer.wrap(payload)
      .writeIntBE(1)
      .skip(8)            // leaves a gap for future data
      .writeIntBE(2);

writer.position(4);
writer.writeLongBE(99L);  // fill skipped region later
```

`skip(long)` is useful for reserving space (e.g. checksums). Remember to ensure you do not move beyond the segment bounds.

---

## SegmentBinaryReader & BinaryReader

`SegmentBinaryReader` mirrors the writer. Wrap a segment, then sequentially decode values. Methods throw if you attempt to read past the end or if the variable-length encodings are malformed.

### Preparing the Reader

```java
SegmentBinaryReader reader = new SegmentBinaryReader();
reader.wrap(payload);
long pos = reader.position();
reader.position(0);           // rewinds or jumps around
long remaining = reader.remaining();
reader.skip(4);               // advances cursor with bounds checks
```

### Reading Big-Endian Primitives

```java
reader.wrap(payload);
byte marker      = reader.readByte();
boolean flag     = reader.readBoolean();
short shortBE    = reader.readShortBE();
int intBE        = reader.readIntBE();
long longBE      = reader.readLongBE();
float floatBE    = reader.readFloatBE();
double doubleBE  = reader.readDoubleBE();
```

Each method advances the cursor by the width of the primitive.

### Reading Little-Endian Primitives

```java
short shortLE   = reader.readShortLE();
int intLE       = reader.readIntLE();
long longLE     = reader.readLongLE();
float floatLE   = reader.readFloatLE();
double doubleLE = reader.readDoubleLE();
```

### Variable-Length Integers (Reading)

```java
int small = reader.readVarInt();
long big  = reader.readVarLong();
```

Both throw `IllegalStateException` if more continuation bytes are encountered than the format allows.

### Reading Strings and Byte Arrays

```java
Utf8View utf8 = new Utf8View();
reader.readString(utf8);       // populates view with next VarInt-prefixed string
String heapCopy = utf8.toString();
boolean present = reader.readNullableString(utf8); // false => value was null

byte[] bytes = reader.readBytes();
byte[] optional = reader.readNullableBytes();
```

- `readString(Utf8View view)` consumes the VarInt length and points the view at the substring inside the backing segment. No heap allocations occur until `toString()` is called.
- `readNullableString(Utf8View view)` returns `false` and leaves the view untouched when the presence byte is `0`.

### Nullable Primitives

```java
Byte optByte            = reader.readNullableByte();
Short optShortBE        = reader.readNullableShortBE();
Integer optIntBE        = reader.readNullableIntBE();
Long optLongBE          = reader.readNullableLongBE();
byte[] optBytes         = reader.readNullableBytes();
```

Each method reads the presence flag, returning `null` if absent and delegating to the corresponding non-null method when present.

### Combined Reader/Writer Round Trip Example

```java
MemorySegment scratch = Arena.ofConfined().allocate(1024);
MemorySegment buffer  = Arena.ofConfined().allocate(1024);
SegmentBinaryWriter writer = new SegmentBinaryWriter();
SegmentBinaryReader reader = new SegmentBinaryReader();
Utf8View view = new Utf8View();

writer.wrap(buffer)
      .writeIntBE(123)
      .writeVarLong(999_999_999L)
      .writeString("hello", scratch)
      .writeNullableIntBE(null)
      .writeNullableString("optional", scratch)
      .writeBoolean(false);

reader.wrap(buffer);
int id = reader.readIntBE();
long counter = reader.readVarLong();
reader.readString(view);
Integer maybeInt = reader.readNullableIntBE();
boolean stringPresent = reader.readNullableString(view);
boolean lastFlag = reader.readBoolean();
```

This pattern underpins serialization/deserialization in zero-GC pipelines.

---

## Utf8View Deep Dive

`Utf8View` is a reusable flyweight that exposes UTF-8 slices inside a `MemorySegment`.

### Wrapping and Inspecting Data

```java
Utf8View view = new Utf8View();
MemorySegment segment = Arena.ofAuto().allocateFrom("Hi", StandardCharsets.UTF_8);
view.wrap(segment, 0, (int) segment.byteSize());

// Zero-allocation comparisons
if (view.equalsString("Hi")) {
    // matched
}

// Heap allocation only when necessary
String copy = view.toString();
```

- `wrap(MemorySegment segment, long offset, int length)` points the view at UTF-8 bytes located at `segment.asSlice(offset, length)`.
- `toString()` allocates and decodes into a Java `String` (use sparingly on hot paths).
- `equalsString(String other)` compares without allocating intermediate byte arrays (currently delegates to `toString()` internally, but centralises the comparison logic for future optimisation).

### Using with SegmentBinaryReader

```java
SegmentBinaryReader reader = new SegmentBinaryReader();
Utf8View customerName = new Utf8View();
reader.wrap(buffer);
reader.readString(customerName);   // view now references bytes in the buffer
processName(customerName);         // downstream logic can inspect without copying
```

The view remains valid as long as the underlying segment stays alive.

---

## Layouts Utility

`Layouts` defines reusable `ValueLayout` constants with byte alignment set to 1 (`withByteAlignment(1)`), permitting unaligned access.

### Accessing Primitives Manually

```java
MemorySegment segment = Arena.ofAuto().allocate(32);
long base = 0;
segment.set(Layouts.INT_BE, base, 0xDEADBEEF);
segment.set(Layouts.SHORT_LE, base + 4, (short) 0xCAFE);
segment.set(Layouts.DOUBLE_BE, base + 8, Math.PI);

int intBE = segment.get(Layouts.INT_BE, base);
short shortLE = segment.get(Layouts.SHORT_LE, base + 4);
double doubleBE = segment.get(Layouts.DOUBLE_BE, base + 8);
```

These layouts are leveraged internally by the reader/writer but are available for custom low-level work such as flyweights or manual struct manipulation.

### Supporting Custom Flyweights

```java
public record AuditHeader(MemorySegment segment, long offset) {
    public int sequence() { return segment.get(Layouts.INT_LE, offset); }
    public void sequence(int value) { segment.set(Layouts.INT_LE, offset, value); }

    public long timestamp() { return segment.get(Layouts.LONG_BE, offset + 4); }
    public void timestamp(long value) { segment.set(Layouts.LONG_BE, offset + 4, value); }
}
```

Using `Layouts` ensures consistent endianness and alignment across the codebase.

---

## FlyweightAccessor Interface

`FlyweightAccessor` defines the contract for objects that provide structured access to bytes stored in a `MemorySegment`.

### Implementing a Flyweight

```java
public final class TradeHeader implements FlyweightAccessor {
    private static final long OFFSET_ID = 0;
    private static final long OFFSET_FLAGS = OFFSET_ID + 8;
    private static final long OFFSET_PRICE = OFFSET_FLAGS + 1;
    private static final int BYTE_SIZE = (int) (OFFSET_PRICE + 8);

    private MemorySegment segment;
    private long offset;

    @Override
    public void wrap(MemorySegment segment, long offset) {
        this.segment = segment;
        this.offset = offset;
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public int byteSize() {
        return BYTE_SIZE;
    }

    public long tradeId() {
        return segment.get(Layouts.LONG_BE, offset + OFFSET_ID);
    }

    public void tradeId(long value) {
        segment.set(Layouts.LONG_BE, offset + OFFSET_ID, value);
    }

    public byte flags() {
        return segment.get(Layouts.BYTE, offset + OFFSET_FLAGS);
    }

    public void flags(byte value) {
        segment.set(Layouts.BYTE, offset + OFFSET_FLAGS, value);
    }

    public double price() {
        return segment.get(Layouts.DOUBLE_LE, offset + OFFSET_PRICE);
    }

    public void price(double value) {
        segment.set(Layouts.DOUBLE_LE, offset + OFFSET_PRICE, value);
    }

    @Override
    public void writeTo(BinaryWriter writer) {
        writer.writeLongBE(tradeId())
              .writeByte(flags())
              .writeDoubleLE(price());
    }
}
```

### Using the Flyweight

```java
MemorySegment buffer = Arena.ofConfined().allocate(64);
TradeHeader header = new TradeHeader();
header.wrap(buffer, 0);
header.tradeId(1_234_567_890L);
header.flags((byte) 0x01);
header.price(123.45);

SegmentBinaryWriter writer = new SegmentBinaryWriter();
writer.wrap(buffer).writeLongBE(1_234_567_890L);

SegmentBinaryReader reader = new SegmentBinaryReader();
reader.wrap(buffer);
TradeHeader snapshot = new TradeHeader();
snapshot.wrap(buffer, reader.position());
```

Flyweights allow you to manipulate structured records without copying data between heap objects.

---

## SegmentUtils

`SegmentUtils` contains stateless helpers for `MemorySegment` operations.

### CRC32 Calculation

```java
MemorySegment payload = Arena.ofConfined().allocate(256);
payload.set(Layouts.INT_LE, 0, 42);
payload.set(Layouts.INT_LE, 4, 84);

int checksum = SegmentUtils.calculateCrc32(payload);
System.out.println("CRC32: " + Integer.toUnsignedString(checksum));
```

- `calculateCrc32(MemorySegment segment)` creates a zero-copy `ByteBuffer` view and computes the CRC32 checksum without copying to the heap.
- Useful for message integrity checks or detecting buffer corruption before releasing to the pool.

### Extending SegmentUtils

The class is `final` with a private constructor, encouraging addition of more high-performance utilities (e.g. vectorized zeroing or copying using Java’s Vector API). Use this file as the central place for shared segment helpers.

---

## Putting It All Together: End-to-End Example

The following example shows a complete request/response codec that utilises every major component.

```java
public final class OrderCodec {
    private final MemorySegmentPool pool;
    private final SegmentBinaryWriter writer = new SegmentBinaryWriter();
    private final SegmentBinaryReader reader = new SegmentBinaryReader();
    private final Utf8View symbolView = new Utf8View();
    private final MemorySegment scratch;

    public OrderCodec() {
        this.pool = new MemorySegmentPool(2048, 16, 64);
        this.scratch = Arena.ofAuto().allocate(1024);
    }

    public MemorySegment encode(long orderId, String symbol, double price) {
        MemorySegment segment = pool.acquire();
        writer.wrap(segment)
              .writeLongBE(orderId)
              .writeString(symbol, scratch)
              .writeDoubleLE(price)
              .writeNullableLongBE(null)
              .writeBoolean(true);

        int crc32 = SegmentUtils.calculateCrc32(segment.asSlice(0, writer.position()));
        writer.position(0).writeIntBE(crc32); // overwrite the first four bytes with checksum
        return segment;
    }

    public void decode(MemorySegment segment) {
        reader.wrap(segment);
        int checksum = reader.readIntBE();
        int actual = SegmentUtils.calculateCrc32(segment.asSlice(0, reader.remaining()));
        if (checksum != actual) {
            throw new IllegalStateException("Checksum mismatch");
        }

        long orderId = reader.readLongBE();
        reader.readString(symbolView);
        double price = reader.readDoubleLE();
        Long optional = reader.readNullableLongBE();
        boolean flag = reader.readBoolean();

        // Use symbolView.equalsString or symbolView.toString() as needed
    }

    public void release(MemorySegment segment) {
        pool.release(segment);
    }
}
```

This demonstrates how to combine the pool, codec primitives, UTF-8 views, and utilities in a cohesive pipeline.

---

## VarFieldWriter

`VarFieldWriter` builds messages with variable-length fields in a flyweight-compatible format. It uses a two-phase layout: a fixed header region containing `[offset:int32][length:int32]` descriptors for each variable field, followed by a data region where the actual field content is written.

### Construction

```java
MemorySegment segment = arena.allocate(2048);
int fixedHeaderSize = 16;    // Reserve 16 bytes for fixed fields
int maxVarFields = 3;         // Support up to 3 variable fields

VarFieldWriter writer = new VarFieldWriter(segment, fixedHeaderSize, maxVarFields);
```

Memory layout:

```text
[0..fixedHeaderSize-1]              : Fixed header (write with writeIntLE, etc.)
[fixedHeaderSize..N]                : Var field headers (8 bytes each: offset+length)
[after var field headers..end]      : Variable data region
```

### Writing Fixed Header Fields

```java
writer.writeIntLE(0, 1001);        // Write message type at offset 0
writer.writeLongLE(4, 123456L);    // Write timestamp at offset 4
writer.writeShortLE(12, (short)5); // Write flags at offset 12
```

### Writing Variable Fields

```java
// Reserve slot for each variable field
int keySlot = writer.reserveVarField();
int valueSlot = writer.reserveVarField();
int metadataSlot = writer.reserveVarField();

// Reusable scratch buffer for UTF-8 encoding (size it for your largest string)
MemorySegment stringScratch = arena.allocate(256);

// Write variable data (can be byte[], String, or MemorySegment)
writer.writeVarField(keySlot, "user:12345", stringScratch);
writer.writeVarField(valueSlot, jsonBytes);
writer.writeVarField(metadataSlot, metadataSegment);
```

### Finishing and Metrics

```java
MemorySegment message = writer.finish();  // Returns slice from start to current position
long totalBytes = writer.bytesWritten();  // Total message size
int varCount = writer.varFieldCount();    // Number of var fields written
long dataStart = writer.dataOffset();     // Offset where data region begins
```

### Resetting for Reuse

```java
writer.reset();  // Clears state, allows reusing same writer instance
```

**Important**: `finish()` returns a slice of the same segment. If you need to preserve the message after calling `reset()`, copy it to independent storage first.

---

## BitSetView

`BitSetView` is a flyweight that treats a `MemorySegment` as a bit set, enabling zero-copy bit manipulation.

### Wrapping a Segment

```java
MemorySegment segment = arena.allocate(128);  // 128 bytes = 1024 bits
BitSetView bitSet = new BitSetView();
bitSet.wrap(segment);
```

### Basic Operations

```java
bitSet.set(42);           // Set bit 42 to 1
boolean isSet = bitSet.get(42);  // Returns true
bitSet.clear(42);         // Clear bit 42 to 0
bitSet.flip(42);          // Toggle bit 42

bitSet.setAll();          // Set all bits to 1
bitSet.clearAll();        // Clear all bits to 0
```

### Cardinality

```java
long count = bitSet.cardinality();  // Count number of 1 bits
```

### Searching

```java
long firstSet = bitSet.nextSetBit(0);      // Find first 1 bit starting from index 0
long firstClear = bitSet.nextClearBit(0);  // Find first 0 bit starting from index 0

// Iterate all set bits
for (long i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
    System.out.println("Bit " + i + " is set");
}
```

### Boundary Checks

All operations check that the bit index is within the segment bounds. Out-of-bounds access throws `IndexOutOfBoundsException`.

---

## Utf8View

`Utf8View` is a zero-GC flyweight for UTF-8 strings stored in off-heap memory. It enables string comparisons and hashing without heap allocations.

### Wrapping UTF-8 Data

```java
String text = "Hello, 世界! 👋";
byte[] utf8Bytes = text.getBytes(StandardCharsets.UTF_8);
MemorySegment segment = arena.allocate(utf8Bytes.length);
segment.copyFrom(MemorySegment.ofArray(utf8Bytes));

Utf8View view = new Utf8View();
view.wrap(segment);
```

### Zero-GC String Comparison

```java
// Compare with Java String without heap allocation
if (view.equalsString("Hello, 世界! 👋")) {
    // Match!
}

// Compare with another Utf8View
Utf8View other = new Utf8View();
other.wrap(otherSegment);
if (view.equals(other)) {
    // Match!
}
```

### Lexicographic Ordering

```java
int cmp = view.compareTo(other);  // Returns -1, 0, or 1
if (cmp < 0) {
    System.out.println("view comes before other");
}
```

### Hash Code

```java
int hash = view.hashCode();  // Consistent with String.hashCode() for same content
Map<Utf8View, Value> map = new HashMap<>();  // Can use as map key
```

### String Conversion (Allocates)

```java
String str = view.toString();  // Allocates heap String - avoid in hot path
```

### Validation

```java
if (!view.isWrapped()) {
    throw new IllegalStateException("View not initialized");
}
```

---

## Operational Tips

- **Bounds safety**: `position(long)` and `skip(long)` enforce bounds, but they do **not** grow the underlying segment. Always size segments to accommodate the maximum payload.
- **Threading**: `SegmentBinaryWriter`, `SegmentBinaryReader`, and `Utf8View` are *not* thread-safe. Reuse them per thread or guard externally.
- **Scratch buffers**: Maintain one scratch buffer per thread to avoid contention and ensure zero allocations during string writes. If you underestimate capacity, throws occur when copying string bytes.
- **Pooling vs. arenas**: The pool internally uses `Arena.ofAuto()`. If you require deterministic lifetime control (e.g. closing per request), prefer `Arena.ofConfined()` and manual resource management.
- **Monitoring**: Track `getAvailableCount()` and `getTotalCount()` to spot saturation. When the pool reaches `maxSize`, `acquire()` throws — treat this as backpressure.

---

## Test Coverage References

The project ships with comprehensive JUnit tests illustrating most APIs:

- `MemorySegmentPoolTest` exercises pool growth, exhaustion, zeroing, variable-size handling, and concurrency.
- `SegmentBinaryReaderWriterTest` covers primitive round-trips, varints, strings, byte arrays, nullable fields, `skip`, and positional controls.

Use these tests as additional examples or starting points for your own scenarios.

---

## Next Steps and Extensibility

Future work noted in `ToDo.md` includes vectorized copy/zero helpers, additional flyweights, and JMH benchmarks. When new utilities are added, extend this guide with corresponding usage patterns to keep it authoritative.

---

By following this guide, you can leverage `roray-ffm` to build high-throughput, low-latency, GC-free pipelines on top of Java's Foreign Function & Memory API.

---

## FFM Function Utilities

The `express.mvp.roray.ffm.functions` package provides zero-overhead utilities for calling native functions via Java's Foreign Function & Memory API.

> **Platform:** Linux x86_64 and ARM64 (LP64 data model only). These utilities are NOT compatible with Windows or 32-bit systems.

### Design Philosophy

All utilities follow the **zero-cost abstraction** principle:

1. **Setup-time cost only** — Builder patterns and factories run once during class initialization
2. **Call-time zero overhead** — The resulting `MethodHandle` is identical to hand-written FFM code
3. **Static final storage** — Store handles in `static final` fields for optimal JIT performance

### FunctionDescriptorBuilder

Fluent builder for creating `FunctionDescriptor` instances:

```java
// Simple syscall with no arguments
FunctionDescriptor getpidDesc = FunctionDescriptorBuilder.returnsInt().build();

// File I/O with multiple arguments
FunctionDescriptor writeDesc = FunctionDescriptorBuilder.returnsLong()
    .args(LinuxLayouts.FD, LinuxLayouts.C_POINTER, LinuxLayouts.C_SIZE_T)
    .build();

// Socket creation
FunctionDescriptor socketDesc = FunctionDescriptorBuilder.returnsInt()
    .args(LinuxLayouts.C_INT, LinuxLayouts.C_INT, LinuxLayouts.C_INT)
    .build();

// Void return (e.g., exit)
FunctionDescriptor exitDesc = FunctionDescriptorBuilder.returnsVoid()
    .args(LinuxLayouts.C_INT)
    .build();
```

### DowncallFactory

Factory for creating downcall method handles:

```java
private static final DowncallFactory FACTORY = DowncallFactory.forNativeLinker();

// Basic syscall
private static final MethodHandle getpid = FACTORY.downcall(
    "getpid",
    FunctionDescriptorBuilder.returnsInt().build()
);

// With linker options (critical mode for non-blocking calls)
private static final MethodHandle read = FACTORY.downcall(
    "read",
    FunctionDescriptorBuilder.returnsLong()
        .args(LinuxLayouts.FD, LinuxLayouts.C_POINTER, LinuxLayouts.C_SIZE_T)
        .build(),
    Linker.Option.critical(false)
);

// Usage at call-time (zero overhead)
public int getPid() throws Throwable {
    return (int) getpid.invokeExact();
}
```

**Loading a custom library:**

```java
// Load liburing.so
private static final DowncallFactory URING_FACTORY = 
    DowncallFactory.withLibrary(Arena.global(), "/usr/lib/liburing.so");

private static final MethodHandle io_uring_queue_init = URING_FACTORY.downcall(
    "io_uring_queue_init",
    FunctionDescriptorBuilder.returnsInt()
        .args(LinuxLayouts.C_INT, LinuxLayouts.C_POINTER, LinuxLayouts.C_INT)
        .build()
);
```

### LinuxLayouts

Pre-defined `MemoryLayout` constants for C types and Linux structures:

**Primitive Types:**

```java
LinuxLayouts.C_CHAR      // 1 byte
LinuxLayouts.C_SHORT     // 2 bytes
LinuxLayouts.C_INT       // 4 bytes
LinuxLayouts.C_LONG      // 8 bytes (LP64)
LinuxLayouts.C_POINTER   // 8 bytes (64-bit)
LinuxLayouts.C_SIZE_T    // 8 bytes
LinuxLayouts.FD          // 4 bytes (file descriptor)
```

**Socket Structures:**

```java
// struct iovec - scatter/gather I/O
LinuxLayouts.IOVEC           // 16 bytes
LinuxLayouts.IOVEC_IOV_BASE  // offset 0
LinuxLayouts.IOVEC_IOV_LEN   // offset 8

// struct sockaddr_in - IPv4 address
LinuxLayouts.SOCKADDR_IN     // 16 bytes
LinuxLayouts.AF_INET         // address family constant

// struct sockaddr_in6 - IPv6 address
LinuxLayouts.SOCKADDR_IN6    // 28 bytes
LinuxLayouts.AF_INET6        // address family constant

// struct msghdr - message header for sendmsg/recvmsg
LinuxLayouts.MSGHDR          // 56 bytes
```

**io_uring Structures:**

```java
// Submission Queue Entry
LinuxLayouts.IO_URING_SQE      // 64 bytes

// Completion Queue Entry
LinuxLayouts.IO_URING_CQE      // 16 bytes
```

**Socket Example:**

```java
private static final MethodHandle socket = FACTORY.downcall(
    "socket",
    FunctionDescriptorBuilder.returnsInt()
        .args(LinuxLayouts.C_INT, LinuxLayouts.C_INT, LinuxLayouts.C_INT)
        .build()
);

private static final MethodHandle bind = FACTORY.downcall(
    "bind",
    FunctionDescriptorBuilder.returnsInt()
        .args(LinuxLayouts.FD, LinuxLayouts.C_POINTER, LinuxLayouts.C_INT)
        .build()
);

public int createAndBindSocket(int port) throws Throwable {
    int fd = (int) socket.invokeExact(
        (int) LinuxLayouts.AF_INET,
        LinuxLayouts.SOCK_STREAM,
        0
    );
    
    try (Arena arena = Arena.ofConfined()) {
        MemorySegment addr = arena.allocate(LinuxLayouts.SOCKADDR_IN);
        addr.set(ValueLayout.JAVA_SHORT, 0, LinuxLayouts.AF_INET);  // sin_family
        addr.set(ValueLayout.JAVA_SHORT, 2, Short.reverseBytes((short) port));  // sin_port (network order)
        addr.set(ValueLayout.JAVA_INT, 4, 0);  // sin_addr = INADDR_ANY
        
        int result = (int) bind.invokeExact(fd, addr, (int) LinuxLayouts.SOCKADDR_IN_SIZE);
        return result;
    }
}
```

### UpcallFactory

Create native callbacks from Java methods:

```java
// Define callback signature
FunctionDescriptor signalHandlerDesc = FunctionDescriptorBuilder.returnsVoid()
    .args(LinuxLayouts.C_INT)
    .build();

// From a MethodHandle
MethodHandle handler = MethodHandles.lookup().findStatic(
    MyClass.class, "handleSignal", MethodType.methodType(void.class, int.class)
);
MemorySegment callback = UpcallFactory.upcall(Arena.global(), handler, signalHandlerDesc);

// From a functional interface (lambda)
MemorySegment callback = UpcallFactory.upcall(
    Arena.global(),
    (int signal) -> System.out.println("Received signal: " + signal),
    signalHandlerDesc,
    MethodType.methodType(void.class, int.class)
);
```

> **Important:** The `Arena` controls the callback's lifetime. Use `Arena.global()` for callbacks that must outlive any specific scope.

### ErrnoCapture

Capture and interpret errno from syscalls:

```java
// Create handle with errno capture
private static final MethodHandle open = FACTORY.downcall(
    "open",
    FunctionDescriptorBuilder.returnsInt()
        .args(LinuxLayouts.C_POINTER, LinuxLayouts.C_INT)
        .build(),
    ErrnoCapture.captureOption()  // Linker.Option.captureCallState("errno")
);

public int openFile(String path) throws Throwable {
    try (Arena arena = Arena.ofConfined()) {
        MemorySegment state = ErrnoCapture.allocateCaptureState(arena);
        MemorySegment pathSeg = arena.allocateFrom(path);
        
        int fd = (int) open.invokeExact(state, pathSeg, 0);  // Note: state is first arg
        
        if (fd < 0) {
            int errno = ErrnoCapture.getErrno(state);
            String message = ErrnoCapture.strerror(errno);
            throw new IOException("open failed: " + message + " (errno=" + errno + ")");
        }
        return fd;
    }
}
```

**Common errno constants:**

```java
ErrnoCapture.ENOENT    // 2  - No such file or directory
ErrnoCapture.EACCES    // 13 - Permission denied
ErrnoCapture.EAGAIN    // 11 - Resource temporarily unavailable
ErrnoCapture.EINTR     // 4  - Interrupted system call
ErrnoCapture.ECONNREFUSED  // 111 - Connection refused
```

### StructAccessor

VarHandle-based struct field access:

```java
// Create accessor for iovec struct
StructAccessor iovecAccessor = StructAccessor.of(LinuxLayouts.IOVEC);

try (Arena arena = Arena.ofConfined()) {
    // Allocate array of 2 iovec structs
    MemorySegment iovecs = iovecAccessor.allocateArray(arena, 2);
    
    // Allocate buffers
    MemorySegment buf1 = arena.allocateFrom("Hello ");
    MemorySegment buf2 = arena.allocateFrom("World!\n");
    
    // Set first iovec
    MemorySegment iov0 = iovecAccessor.elementAt(iovecs, 0);
    iovecAccessor.setPointer(iov0, "iov_base", buf1);
    iovecAccessor.setLong(iov0, "iov_len", buf1.byteSize());
    
    // Set second iovec
    MemorySegment iov1 = iovecAccessor.elementAt(iovecs, 1);
    iovecAccessor.setPointer(iov1, "iov_base", buf2);
    iovecAccessor.setLong(iov1, "iov_len", buf2.byteSize());
    
    // Use with writev syscall
    long written = (long) writev.invokeExact(1, iovecs, 2);  // stdout, iovecs, count
}
```

**io_uring SQE example:**

```java
StructAccessor sqeAccessor = StructAccessor.of(LinuxLayouts.IO_URING_SQE);

MemorySegment sqe = sqeAccessor.allocate(arena);
sqeAccessor.setByte(sqe, "opcode", (byte) IORING_OP_READ);
sqeAccessor.setByte(sqe, "flags", (byte) 0);
sqeAccessor.setInt(sqe, "fd", fileDescriptor);
sqeAccessor.setLong(sqe, "off", offset);
sqeAccessor.setLong(sqe, "addr", buffer.address());
sqeAccessor.setInt(sqe, "len", (int) buffer.byteSize());
sqeAccessor.setLong(sqe, "user_data", requestId);
```

### Critical Mode Annotations

Documentation annotations to mark functions as safe or unsafe for critical mode:

```java
/**
 * @CriticalSafe - Can use Linker.Option.critical(false)
 * Criteria: No blocking, no signals, runs in microseconds
 */
@CriticalSafe
private static final MethodHandle getpid = FACTORY.downcall(
    "getpid",
    FunctionDescriptorBuilder.returnsInt().build(),
    Linker.Option.critical(false)
);

/**
 * @NeverCritical - Must NOT use critical mode
 * Reason: Blocks waiting for I/O
 */
@NeverCritical
private static final MethodHandle read = FACTORY.downcall(
    "read",
    FunctionDescriptorBuilder.returnsLong()
        .args(LinuxLayouts.FD, LinuxLayouts.C_POINTER, LinuxLayouts.C_SIZE_T)
        .build()
);
```

### Complete Socket Server Example

```java
public final class NativeSocketServer {
    private static final DowncallFactory FACTORY = DowncallFactory.forNativeLinker();
    
    @CriticalSafe
    private static final MethodHandle socket = FACTORY.downcall(
        "socket",
        FunctionDescriptorBuilder.returnsInt()
            .args(LinuxLayouts.C_INT, LinuxLayouts.C_INT, LinuxLayouts.C_INT)
            .build(),
        Linker.Option.critical(false)
    );
    
    private static final MethodHandle bind = FACTORY.downcall(
        "bind",
        FunctionDescriptorBuilder.returnsInt()
            .args(LinuxLayouts.FD, LinuxLayouts.C_POINTER, LinuxLayouts.C_INT)
            .build(),
        ErrnoCapture.captureOption()
    );
    
    private static final MethodHandle listen = FACTORY.downcall(
        "listen",
        FunctionDescriptorBuilder.returnsInt()
            .args(LinuxLayouts.FD, LinuxLayouts.C_INT)
            .build()
    );
    
    @NeverCritical  // Blocks waiting for connections
    private static final MethodHandle accept = FACTORY.downcall(
        "accept",
        FunctionDescriptorBuilder.returnsInt()
            .args(LinuxLayouts.FD, LinuxLayouts.C_POINTER, LinuxLayouts.C_POINTER)
            .build(),
        ErrnoCapture.captureOption()
    );
    
    public int start(int port) throws Throwable {
        int fd = (int) socket.invokeExact(
            (int) LinuxLayouts.AF_INET,
            LinuxLayouts.SOCK_STREAM | LinuxLayouts.SOCK_NONBLOCK,
            0
        );
        
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment state = ErrnoCapture.allocateCaptureState(arena);
            MemorySegment addr = arena.allocate(LinuxLayouts.SOCKADDR_IN);
            
            // Configure address
            addr.set(ValueLayout.JAVA_SHORT, 0, LinuxLayouts.AF_INET);
            addr.set(ValueLayout.JAVA_SHORT, 2, Short.reverseBytes((short) port));
            addr.set(ValueLayout.JAVA_INT, 4, 0);  // INADDR_ANY
            
            int result = (int) bind.invokeExact(state, fd, addr, (int) LinuxLayouts.SOCKADDR_IN_SIZE);
            if (result < 0) {
                int errno = ErrnoCapture.getErrno(state);
                throw new IOException("bind failed: " + ErrnoCapture.strerror(errno));
            }
            
            result = (int) listen.invokeExact(fd, 128);
            if (result < 0) {
                throw new IOException("listen failed");
            }
        }
        
        return fd;
    }
}
```

### Performance Notes

- **JMH verified:** Benchmarks confirm zero overhead vs hand-written FFM code (~140ns for getpid)
- **Critical mode:** Use `Linker.Option.critical(false)` only for non-blocking operations
- **Static final:** Always store handles in `static final` fields for JIT optimization
- **Arena lifetime:** Match arena scope to data lifetime; avoid `Arena.global()` for short-lived data
