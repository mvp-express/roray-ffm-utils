package express.mvp.roray.ffm.utils.functions;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation to document that a native function should NOT be called with {@code
 * Linker.Option.critical(false)}.
 *
 * <p>This annotation is for <b>documentation purposes only</b> and does not provide compile-time
 * enforcement. It serves as a warning to developers that the annotated function may:
 *
 * <ul>
 *   <li>Make system calls that can block
 *   <li>Trigger callbacks into Java (upcalls)
 *   <li>Take locks that could contend with GC
 *   <li>Perform I/O operations
 * </ul>
 *
 * <h2>Why No Compile-Time Enforcement?</h2>
 *
 * <p>The Java compiler cannot verify that a {@link java.lang.invoke.MethodHandle} was created with
 * or without the critical option, as this is a runtime decision. Additionally, the safety of using
 * critical mode depends on the native function's implementation, which cannot be introspected at
 * compile time.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Document that this handle should NOT use critical mode
 * @NeverCritical("Blocks waiting for I/O completion")
 * private static final MethodHandle io_uring_wait_cqe = FACTORY.downcall(
 *     "io_uring_wait_cqe", descriptor
 *     // NO critical option here - this blocks!
 * );
 *
 * // This handle CAN safely use critical mode (not annotated)
 * private static final MethodHandle io_uring_cq_ready = FACTORY.downcall(
 *     "io_uring_cq_ready", descriptor,
 *     Linker.Option.critical(false)  // Safe: just reads memory
 * );
 * }</pre>
 *
 * <h2>Static Analysis</h2>
 *
 * <p>For actual enforcement, consider using static analysis tools like:
 *
 * <ul>
 *   <li>Error Prone (custom BugChecker)
 *   <li>SpotBugs (custom detector)
 *   <li>Checker Framework (custom type system)
 * </ul>
 *
 * @see CriticalSafe
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.LOCAL_VARIABLE})
public @interface NeverCritical {
    /** Explanation of why this function should not use critical mode. */
    String value() default "";
}
