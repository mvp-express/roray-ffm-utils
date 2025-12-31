# roray-ffm

> High-performance memory toolkit for Java's Foreign Function & Memory API.

[![Build](https://img.shields.io/github/actions/workflow/status/mvp-express/roray-ffm-utils/build.yml?branch=main)](https://github.com/mvp-express/roray-ffm-utils/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Installation

```kotlin
dependencies {
    implementation("express.mvp:roray-ffm:0.1.0-SNAPSHOT")
}
```

**Requires Java 25+** with FFM enabled:

```bash
java --enable-native-access=ALL-UNNAMED -jar your-app.jar
```

## Quick Example

```java
// Zero-GC memory pool for high-frequency operations
MemorySegmentPool pool = new MemorySegmentPool(1024, 10);
MemorySegment segment = pool.acquire();

try {
    SegmentBinaryWriter writer = new SegmentBinaryWriter().wrap(segment);
    writer.writeIntBE(12345);
    writer.writeString("ORDER_ID", scratchBuffer);
    
    SegmentBinaryReader reader = new SegmentBinaryReader().wrap(segment);
    int id = reader.readIntBE();
} finally {
    pool.release(segment);
}
```

## FFM Function Helpers

Zero-overhead utilities for calling native functions via Java's FFM API.

> **Platform:** Linux x86_64 and ARM64 (LP64 data model)

```java
import express.mvp.roray.ffm.utils.functions.*;

// Setup-time: create factory and downcall handles (store in static final)
private static final DowncallFactory FACTORY = DowncallFactory.forNativeLinker();

        private static final MethodHandle getpid = FACTORY.downcall(
                "getpid",
                FunctionDescriptorBuilder.returnsInt().build()
        );

        private static final MethodHandle write = FACTORY.downcall(
                "write",
                FunctionDescriptorBuilder.returnsLong()
                        .args(LinuxLayouts.FD, LinuxLayouts.C_POINTER, LinuxLayouts.C_SIZE_T)
                        .build(),
                Linker.Option.critical(false)  // Fast path for non-blocking calls
        );

        // Call-time: zero overhead - same as raw MethodHandle.invokeExact()
        public void example() throws Throwable {
            int pid = (int) getpid.invokeExact();

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocateFrom("Hello\n");
                long written = (long) write.invokeExact(1, buffer, 6L);  // stdout
            }
        }
```

**Key utilities:**

- `FunctionDescriptorBuilder` — Fluent API for building function signatures
- `DowncallFactory` — Factory for creating native function handles
- `LinuxLayouts` — Pre-defined layouts for C types and Linux structs
- `UpcallFactory` — Create native callbacks from Java methods
- `ErrnoCapture` — Capture and interpret errno from syscalls
- `StructAccessor` — VarHandle-based struct field access

## Documentation

📚 **[User Guide](https://mvp.express/docs/roray-ffm/)** — Full documentation  
🚀 **[Getting Started](https://mvp.express/docs/getting-started/)** — Ecosystem tutorial  
📖 **[API Reference](https://mvp.express/docs/roray-ffm/api/)** — Javadoc

## For Contributors

See [CONTRIBUTING.md](CONTRIBUTING.md) for build instructions and PR process.

## License

Apache 2.0 — See [LICENSE](LICENSE)
