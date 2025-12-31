package express.mvp.roray.ffm.utils.functions;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation to document that a native function is safe to call with {@code*
 * Linker.Option.critical(true)}.
 *
 * <p>A function is safe for critical mode when it:
 *
 * <ul>
 *   <li>Does NOT make system calls
 *   <li>Does NOT block or wait
 *   <li>Does NOT call back into Java (no upcalls)
 *   <li>Does NOT take locks that could contend with GC
 *   <li>Executes in bounded, short time
 * </ul>
 *
 * <p>Critical mode skips the thread state transition overhead (~10-20ns savings) but prevents the
 * JVM from reaching a safepoint during the call. Misusing critical mode with blocking calls can
 * cause GC pauses and deadlocks.
 *
 * <h2>Good Candidates for Critical Mode</h2>
 *
 * <ul>
 *   <li>Pure memory reads/writes (no syscalls)
 *   <li>Pointer arithmetic operations
 *   <li>CPU-only operations (memory fences, atomics)
 *   <li>Fast inline functions that don't call other functions
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * @CriticalSafe("Pure memory read, no syscall")
 * private static final MethodHandle io_uring_cq_ready = FACTORY.downcall(
 *     "io_uring_cq_ready", descriptor,
 *     Linker.Option.critical(true)
 * );
 * }</pre>
 *
 * @see NeverCritical
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.LOCAL_VARIABLE})
public @interface CriticalSafe {
    /** Explanation of why this function is safe for critical mode. */
    String value() default "";
}
