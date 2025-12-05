package express.mvp.roray.utils.functions;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.VarHandle;

/**
 * Helper for capturing and interpreting {@code errno} from native calls.
 *
 * <p>When a native function fails, it typically returns -1 and sets {@code errno} to indicate the
 * specific error. This class provides utilities to capture and interpret these error codes.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create a downcall that captures errno
 * MethodHandle open = factory.downcall("open", descriptor,
 *     ErrnoCapture.captureOption());
 *
 * // Make the call with errno capture
 * try (Arena arena = Arena.ofConfined()) {
 *     MemorySegment capturedState = ErrnoCapture.allocateCaptureState(arena);
 *     int fd = (int) open.invokeExact(capturedState, path, flags, mode);
 *
 *     if (fd < 0) {
 *         int errno = ErrnoCapture.getErrno(capturedState);
 *         throw new IOException(ErrnoCapture.strerror(errno));
 *     }
 * }
 * }</pre>
 *
 * <h2>Socket Connection Example</h2>
 *
 * <pre>{@code
 * MethodHandle connect = factory.downcall("connect", connectDesc,
 *     ErrnoCapture.captureOption());
 *
 * try (Arena arena = Arena.ofConfined()) {
 *     MemorySegment state = ErrnoCapture.allocateCaptureState(arena);
 *     int result = (int) connect.invokeExact(state, sockfd, addr, addrlen);
 *
 *     if (result < 0) {
 *         int errno = ErrnoCapture.getErrno(state);
 *         switch (errno) {
 *             case ErrnoCapture.EINPROGRESS -> {} // Non-blocking connect started
 *             case ErrnoCapture.ECONNREFUSED -> throw new ConnectException("Connection refused");
 *             case ErrnoCapture.ETIMEDOUT -> throw new SocketTimeoutException("Connect timed out");
 *             default -> throw new IOException(ErrnoCapture.strerror(errno));
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Important Notes</h2>
 *
 * <ul>
 *   <li>When using {@code captureCallState}, the MethodHandle signature changes: a {@code
 *       MemorySegment} parameter is prepended for the captured state.
 *   <li>The captured state segment must be allocated before each call.
 * </ul>
 */
public final class ErrnoCapture {

    private ErrnoCapture() {}

    /** Layout for the captured call state containing errno. */
    private static final StructLayout CAPTURED_STATE_LAYOUT = Linker.Option.captureStateLayout();

    /** VarHandle to read errno from the captured state. */
    private static final VarHandle ERRNO_HANDLE;

    static {
        ERRNO_HANDLE =
                CAPTURED_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("errno"));
    }

    /**
     * Returns the {@link Linker.Option} to capture errno.
     *
     * <p>When this option is used, the resulting MethodHandle will have a {@code MemorySegment}
     * prepended to its parameter list. This segment must be allocated using {@link
     * #allocateCaptureState(Arena)} before each invocation.
     *
     * @return Linker option for capturing errno
     */
    public static Linker.Option captureOption() {
        return Linker.Option.captureCallState("errno");
    }

    /**
     * Allocates a memory segment suitable for capturing call state.
     *
     * @param arena Arena to allocate from
     * @return MemorySegment for capturing errno
     */
    public static MemorySegment allocateCaptureState(Arena arena) {
        return arena.allocate(CAPTURED_STATE_LAYOUT);
    }

    /**
     * Gets the size required for the capture state segment.
     *
     * @return Size in bytes
     */
    public static long captureStateSize() {
        return CAPTURED_STATE_LAYOUT.byteSize();
    }

    /**
     * Extracts the errno value from a captured call state.
     *
     * @param capturedState The segment passed to the downcall
     * @return The errno value set by the native function
     */
    public static int getErrno(MemorySegment capturedState) {
        return (int) ERRNO_HANDLE.get(capturedState, 0L);
    }

    /**
     * Returns a human-readable description of an errno value.
     *
     * @param errno The error number
     * @return String description of the error
     */
    public static String strerror(int errno) {
        return switch (errno) {
            case 0 -> "Success";
            case 1 -> "EPERM: Operation not permitted";
            case 2 -> "ENOENT: No such file or directory";
            case 3 -> "ESRCH: No such process";
            case 4 -> "EINTR: Interrupted system call";
            case 5 -> "EIO: I/O error";
            case 6 -> "ENXIO: No such device or address";
            case 7 -> "E2BIG: Argument list too long";
            case 8 -> "ENOEXEC: Exec format error";
            case 9 -> "EBADF: Bad file descriptor";
            case 10 -> "ECHILD: No child processes";
            case 11 -> "EAGAIN: Resource temporarily unavailable";
            case 12 -> "ENOMEM: Cannot allocate memory";
            case 13 -> "EACCES: Permission denied";
            case 14 -> "EFAULT: Bad address";
            case 15 -> "ENOTBLK: Block device required";
            case 16 -> "EBUSY: Device or resource busy";
            case 17 -> "EEXIST: File exists";
            case 18 -> "EXDEV: Invalid cross-device link";
            case 19 -> "ENODEV: No such device";
            case 20 -> "ENOTDIR: Not a directory";
            case 21 -> "EISDIR: Is a directory";
            case 22 -> "EINVAL: Invalid argument";
            case 23 -> "ENFILE: Too many open files in system";
            case 24 -> "EMFILE: Too many open files";
            case 25 -> "ENOTTY: Inappropriate ioctl for device";
            case 26 -> "ETXTBSY: Text file busy";
            case 27 -> "EFBIG: File too large";
            case 28 -> "ENOSPC: No space left on device";
            case 29 -> "ESPIPE: Illegal seek";
            case 30 -> "EROFS: Read-only file system";
            case 31 -> "EMLINK: Too many links";
            case 32 -> "EPIPE: Broken pipe";
            case 33 -> "EDOM: Numerical argument out of domain";
            case 34 -> "ERANGE: Numerical result out of range";
            case 35 -> "EDEADLK: Resource deadlock avoided";
            case 36 -> "ENAMETOOLONG: File name too long";
            case 37 -> "ENOLCK: No locks available";
            case 38 -> "ENOSYS: Function not implemented";
            case 39 -> "ENOTEMPTY: Directory not empty";
            case 40 -> "ELOOP: Too many levels of symbolic links";
            // Socket errors
            case 88 -> "ENOTSOCK: Socket operation on non-socket";
            case 89 -> "EDESTADDRREQ: Destination address required";
            case 90 -> "EMSGSIZE: Message too long";
            case 91 -> "EPROTOTYPE: Protocol wrong type for socket";
            case 92 -> "ENOPROTOOPT: Protocol not available";
            case 93 -> "EPROTONOSUPPORT: Protocol not supported";
            case 94 -> "ESOCKTNOSUPPORT: Socket type not supported";
            case 95 -> "EOPNOTSUPP: Operation not supported";
            case 96 -> "EPFNOSUPPORT: Protocol family not supported";
            case 97 -> "EAFNOSUPPORT: Address family not supported";
            case 98 -> "EADDRINUSE: Address already in use";
            case 99 -> "EADDRNOTAVAIL: Cannot assign requested address";
            case 100 -> "ENETDOWN: Network is down";
            case 101 -> "ENETUNREACH: Network is unreachable";
            case 102 -> "ENETRESET: Network dropped connection on reset";
            case 103 -> "ECONNABORTED: Software caused connection abort";
            case 104 -> "ECONNRESET: Connection reset by peer";
            case 105 -> "ENOBUFS: No buffer space available";
            case 106 -> "EISCONN: Transport endpoint is already connected";
            case 107 -> "ENOTCONN: Transport endpoint is not connected";
            case 108 -> "ESHUTDOWN: Cannot send after transport endpoint shutdown";
            case 110 -> "ETIMEDOUT: Connection timed out";
            case 111 -> "ECONNREFUSED: Connection refused";
            case 112 -> "EHOSTDOWN: Host is down";
            case 113 -> "EHOSTUNREACH: No route to host";
            case 114 -> "EALREADY: Operation already in progress";
            case 115 -> "EINPROGRESS: Operation now in progress";
            default -> "Unknown error: " + errno;
        };
    }

    // ========================================================================
    // Common errno constants
    // ========================================================================

    /** Operation not permitted. */
    public static final int EPERM = 1;

    /** No such file or directory. */
    public static final int ENOENT = 2;

    /** No such process. */
    public static final int ESRCH = 3;

    /** Interrupted system call. */
    public static final int EINTR = 4;

    /** I/O error. */
    public static final int EIO = 5;

    /** Bad file descriptor. */
    public static final int EBADF = 9;

    /** Resource temporarily unavailable (same as EWOULDBLOCK). */
    public static final int EAGAIN = 11;

    /** Cannot allocate memory. */
    public static final int ENOMEM = 12;

    /** Permission denied. */
    public static final int EACCES = 13;

    /** Bad address. */
    public static final int EFAULT = 14;

    /** Device or resource busy. */
    public static final int EBUSY = 16;

    /** File exists. */
    public static final int EEXIST = 17;

    /** Invalid argument. */
    public static final int EINVAL = 22;

    /** Too many open files. */
    public static final int EMFILE = 24;

    /** No space left on device. */
    public static final int ENOSPC = 28;

    /** Broken pipe. */
    public static final int EPIPE = 32;

    /** Resource deadlock avoided. */
    public static final int EDEADLK = 35;

    /** Function not implemented. */
    public static final int ENOSYS = 38;

    /** Connection refused. */
    public static final int ECONNREFUSED = 111;

    /** Connection reset by peer. */
    public static final int ECONNRESET = 104;

    /** Connection timed out. */
    public static final int ETIMEDOUT = 110;

    /** Operation now in progress. */
    public static final int EINPROGRESS = 115;

    /** Operation already in progress. */
    public static final int EALREADY = 114;
}
