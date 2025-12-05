package express.mvp.roray.utils.functions;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * Factory for creating downcall handles with common patterns. All operations are setup-time only -
 * produces raw {@link MethodHandle}s that should be stored in {@code static final} fields for
 * optimal JIT performance.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * private static final DowncallFactory FACTORY = DowncallFactory.forNativeLinker();
 *
 * private static final MethodHandle getpid = FACTORY.downcall(
 *     "getpid",
 *     FunctionDescriptorBuilder.returnsInt().build()
 * );
 * }</pre>
 */
public final class DowncallFactory {

    private final Linker linker;
    private final SymbolLookup lookup;

    private DowncallFactory(Linker linker, SymbolLookup lookup) {
        this.linker = linker;
        this.lookup = lookup;
    }

    /**
     * Create a factory using the native linker and its default lookup. Suitable for standard C
     * library functions.
     */
    public static DowncallFactory forNativeLinker() {
        Linker nativeLinker = Linker.nativeLinker();
        return new DowncallFactory(nativeLinker, nativeLinker.defaultLookup());
    }

    /** Create a factory with a custom symbol lookup (e.g., for a loaded library). */
    public static DowncallFactory withLookup(SymbolLookup libraryLookup) {
        return new DowncallFactory(Linker.nativeLinker(), libraryLookup);
    }

    /**
     * Create a factory for a library loaded from the specified path.
     *
     * @param arena Arena controlling the library's lifetime
     * @param libraryPath Path to the shared library
     */
    public static DowncallFactory withLibrary(Arena arena, String libraryPath) {
        return withLookup(SymbolLookup.libraryLookup(libraryPath, arena));
    }

    /**
     * Creates a downcall handle. The returned {@link MethodHandle} should be stored in a {@code
     * static final} field for optimal JIT performance.
     *
     * @param functionName Name of the native function
     * @param descriptor Function signature
     * @return MethodHandle for calling the native function
     * @throws UnsatisfiedLinkError if the symbol is not found
     */
    public MethodHandle downcall(String functionName, FunctionDescriptor descriptor) {
        MemorySegment symbol =
                lookup.find(functionName)
                        .orElseThrow(
                                () ->
                                        new UnsatisfiedLinkError(
                                                "Symbol not found: " + functionName));
        return linker.downcallHandle(symbol, descriptor);
    }

    /**
     * Creates a downcall handle with linker options.
     *
     * @param functionName Name of the native function
     * @param descriptor Function signature
     * @param options Linker options (e.g., critical, captureCallState)
     * @return MethodHandle for calling the native function
     * @throws UnsatisfiedLinkError if the symbol is not found
     */
    public MethodHandle downcall(
            String functionName, FunctionDescriptor descriptor, Linker.Option... options) {
        MemorySegment symbol =
                lookup.find(functionName)
                        .orElseThrow(
                                () ->
                                        new UnsatisfiedLinkError(
                                                "Symbol not found: " + functionName));
        return linker.downcallHandle(symbol, descriptor, options);
    }

    /**
     * Creates a "virtual" downcall handle that takes a function pointer as its first argument.
     * Useful when function addresses are resolved dynamically (e.g., io_uring ops).
     *
     * <p>The returned {@link MethodHandle} should be stored in a {@code static final} field for
     * optimal JIT performance.
     *
     * @param descriptor Function signature (first arg will be the function pointer)
     * @return MethodHandle that expects function pointer as first argument
     */
    public MethodHandle downcallVirtual(FunctionDescriptor descriptor) {
        return linker.downcallHandle(descriptor);
    }

    /**
     * Creates a virtual downcall handle with linker options.
     *
     * <p>The returned {@link MethodHandle} should be stored in a {@code static final} field for
     * optimal JIT performance.
     *
     * @param descriptor Function signature (first arg will be the function pointer)
     * @param options Linker options (e.g., critical, captureCallState)
     * @return MethodHandle that expects function pointer as first argument
     */
    public MethodHandle downcallVirtual(FunctionDescriptor descriptor, Linker.Option... options) {
        return linker.downcallHandle(descriptor, options);
    }

    /**
     * Look up a symbol address without creating a downcall handle.
     *
     * @param symbolName Name of the symbol
     * @return MemorySegment representing the symbol address
     * @throws UnsatisfiedLinkError if the symbol is not found
     */
    public MemorySegment findSymbol(String symbolName) {
        return lookup.find(symbolName)
                .orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + symbolName));
    }

    /** Get the underlying linker. */
    public Linker linker() {
        return linker;
    }

    /** Get the underlying symbol lookup. */
    public SymbolLookup lookup() {
        return lookup;
    }
}
