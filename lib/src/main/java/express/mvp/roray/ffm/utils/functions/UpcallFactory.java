package express.mvp.roray.ffm.utils.functions;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

/**
 * Factory for creating upcall stubs (Java callbacks callable from native code). All setup costs are
 * paid at stub creation time.
 *
 * <p>Upcall stubs allow native code to call back into Java methods. The stub's lifetime is tied to
 * the {@link Arena} used during creation.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Define a callback interface
 * @FunctionalInterface
 * interface SignalHandler {
 *     void handle(int signum);
 * }
 *
 * // Create an upcall stub
 * try (Arena arena = Arena.ofConfined()) {
 *     UpcallFactory factory = UpcallFactory.create();
 *
 *     MemorySegment stub = factory.upcallStub(
 *         arena,
 *         (int signum) -> System.out.println("Signal: " + signum),
 *         SignalHandler.class,
 *         FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT)
 *     );
 *
 *     // Pass 'stub' to native code as a function pointer
 * }
 * }</pre>
 *
 * <p><b>Warning:</b> The upcall stub is only valid while the Arena is alive. Using the stub after
 * the Arena is closed results in undefined behavior.
 */
public final class UpcallFactory {

    private final Linker linker;

    private UpcallFactory(Linker linker) {
        this.linker = linker;
    }

    /** Create an {@code UpcallFactory} using the native linker. */
    public static UpcallFactory create() {
        return new UpcallFactory(Linker.nativeLinker());
    }

    /**
     * Creates an upcall stub from a {@link MethodHandle}.
     *
     * <p>The returned {@link MemorySegment} can be passed to native code as a function pointer. The
     * stub remains valid for the lifetime of the arena.
     *
     * @param arena Arena controlling the stub's lifetime
     * @param target MethodHandle to the Java method to invoke
     * @param descriptor Native function signature matching the MethodHandle
     * @return MemorySegment representing a native function pointer
     * @throws IllegalArgumentException if the descriptor doesn't match the handle
     */
    public MemorySegment upcallStub(
            Arena arena, MethodHandle target, FunctionDescriptor descriptor) {
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(descriptor, "descriptor");
        return linker.upcallStub(target, descriptor, arena);
    }

    /**
     * Creates an upcall stub from a functional interface instance.
     *
     * <p>This is a convenience method that looks up the single abstract method (SAM) of the
     * functional interface and creates an upcall stub for it.
     *
     * @param <T> The functional interface type
     * @param arena Arena controlling the stub's lifetime
     * @param lookup MethodHandles.Lookup with access to the interface
     * @param callback The callback instance (lambda or method reference)
     * @param functionalInterface The functional interface class
     * @param descriptor Native function signature
     * @return MemorySegment representing a native function pointer
     * @throws IllegalArgumentException if the interface is not a functional interface
     * @throws RuntimeException if the method cannot be found or accessed
     */
    public <T> MemorySegment upcallStub(
            Arena arena,
            MethodHandles.Lookup lookup,
            T callback,
            Class<T> functionalInterface,
            FunctionDescriptor descriptor) {
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(callback, "callback");
        Objects.requireNonNull(functionalInterface, "functionalInterface");
        Objects.requireNonNull(descriptor, "descriptor");

        MethodHandle handle = findSamMethod(lookup, callback, functionalInterface);
        return linker.upcallStub(handle, descriptor, arena);
    }

    /**
     * Creates an upcall stub using the caller's lookup context.
     *
     * @param <T> The functional interface type
     * @param arena Arena controlling the stub's lifetime
     * @param callback The callback instance
     * @param functionalInterface The functional interface class
     * @param descriptor Native function signature
     * @return MemorySegment representing a native function pointer
     */
    public <T> MemorySegment upcallStub(
            Arena arena, T callback, Class<T> functionalInterface, FunctionDescriptor descriptor) {
        return upcallStub(
                arena, MethodHandles.publicLookup(), callback, functionalInterface, descriptor);
    }

    /** Get the underlying linker. */
    public Linker linker() {
        return linker;
    }

    /**
     * Find the single abstract method (SAM) of a functional interface and bind it to the given
     * callback instance.
     */
    @SuppressFBWarnings(
            value = "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION",
            justification = "Invalid interface definitions are treated as programmer errors.")
    private static <T> MethodHandle findSamMethod(
            MethodHandles.Lookup lookup, T callback, Class<T> functionalInterface) {
        if (!functionalInterface.isInterface()) {
            throw new IllegalArgumentException(
                    functionalInterface.getName() + " is not an interface");
        }

        java.lang.reflect.Method samMethod = null;
        for (java.lang.reflect.Method method : functionalInterface.getMethods()) {
            if (java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
                if (samMethod != null) {
                    throw new IllegalArgumentException(
                            functionalInterface.getName() + " has multiple abstract methods");
                }
                samMethod = method;
            }
        }

        if (samMethod == null) {
            throw new IllegalArgumentException(
                    functionalInterface.getName() + " has no abstract methods");
        }

        try {
            MethodHandle handle = lookup.unreflect(samMethod);
            return handle.bindTo(callback);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot access method: " + samMethod, e);
        }
    }
}
