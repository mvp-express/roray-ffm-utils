/**
 * Utilities for the Foreign Function &amp; Memory (FFM) API.
 *
 * <p>This package provides helper classes that reduce boilerplate when working with Java's FFM API
 * for native interop, while maintaining zero runtime overhead.
 *
 * <h2>Design Philosophy</h2>
 *
 * <p>All abstractions in this package are designed for <b>setup-time cost only</b>. The helpers
 * produce standard FFM objects ({@link java.lang.invoke.MethodHandle}, {@link
 * java.lang.foreign.FunctionDescriptor}, {@link java.lang.foreign.MemoryLayout}) that should be
 * stored in {@code static final} fields for optimal JIT performance.
 *
 * <h2>Core Utilities (Phase 1)</h2>
 *
 * <ul>
 *   <li>{@link express.mvp.roray.utils.functions.FunctionDescriptorBuilder} - Fluent builder for
 *       function descriptors
 *   <li>{@link express.mvp.roray.utils.functions.DowncallFactory} - Factory for creating downcall
 *       handles
 *   <li>{@link express.mvp.roray.utils.functions.LinuxLayouts} - Pre-defined layouts for Linux/C
 *       types and structs
 *   <li>{@link express.mvp.roray.utils.functions.UpcallFactory} - Factory for creating upcall stubs
 * </ul>
 *
 * <h2>Extended Utilities (Phase 2)</h2>
 *
 * <ul>
 *   <li>{@link express.mvp.roray.utils.functions.ErrnoCapture} - Helper for capturing and
 *       interpreting errno
 *   <li>{@link express.mvp.roray.utils.functions.StructAccessor} - VarHandle-based struct field
 *       accessors
 *   <li>{@link express.mvp.roray.utils.functions.CriticalSafe} - Annotation marking functions safe
 *       for critical mode
 *   <li>{@link express.mvp.roray.utils.functions.NeverCritical} - Annotation marking functions that
 *       should NOT use critical mode
 * </ul>
 *
 * <h2>Platform Support</h2>
 *
 * <p>The layouts and utilities are designed for the <b>LP64 data model</b>:
 *
 * <ul>
 *   <li>Linux x86_64 (AMD64) ✓
 *   <li>Linux ARM64 (aarch64) ✓
 *   <li>Linux RISC-V 64 ✓
 *   <li>Windows x64 ✗ (uses LLP64)
 *   <li>32-bit systems ✗
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Create a factory for libc functions
 * private static final DowncallFactory LIBC = DowncallFactory.forNativeLinker();
 *
 * // Define downcall handles
 * private static final MethodHandle getpid = LIBC.downcall(
 *     "getpid",
 *     FunctionDescriptorBuilder.returnsInt().build()
 * );
 *
 * private static final MethodHandle socket = LIBC.downcall(
 *     "socket",
 *     FunctionDescriptorBuilder.returnsInt()
 *         .args(LinuxLayouts.C_INT, LinuxLayouts.C_INT, LinuxLayouts.C_INT)
 *         .build()
 * );
 *
 * // Use the handles
 * public static int getPid() throws Throwable {
 *     return (int) getpid.invokeExact();
 * }
 * }</pre>
 *
 * @see java.lang.foreign
 */
package express.mvp.roray.utils.functions;
