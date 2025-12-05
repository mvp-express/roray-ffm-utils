package express.mvp.roray.utils.functions;

import static java.lang.foreign.MemoryLayout.paddingLayout;
import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.MemoryLayout.structLayout;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;

/**
 * Pre-defined {@link MemoryLayout} constants for common Linux/C types and structures. All layouts
 * are compile-time constants with zero runtime cost.
 *
 * <p>These layouts are designed for the <b>LP64 data model</b> used by:
 *
 * <ul>
 *   <li>Linux x86_64 (AMD64)
 *   <li>Linux ARM64 (aarch64)
 *   <li>Linux RISC-V 64
 * </ul>
 *
 * <p><b>Note:</b> These layouts are NOT compatible with Windows (LLP64) or 32-bit systems.
 *
 * <h2>Socket Server Example</h2>
 *
 * <pre>{@code
 * // Create socket
 * int fd = (int) socket.invokeExact(
 *     (int) LinuxLayouts.AF_INET,
 *     LinuxLayouts.SOCK_STREAM | LinuxLayouts.SOCK_NONBLOCK,
 *     0
 * );
 *
 * // Bind to address
 * try (Arena arena = Arena.ofConfined()) {
 *     MemorySegment addr = arena.allocate(LinuxLayouts.SOCKADDR_IN);
 *     addr.set(ValueLayout.JAVA_SHORT, 0, LinuxLayouts.AF_INET);
 *     addr.set(ValueLayout.JAVA_SHORT, 2, Short.reverseBytes((short) 8080));
 *     addr.set(ValueLayout.JAVA_INT, 4, 0);  // INADDR_ANY
 *
 *     bind.invokeExact(fd, addr, (int) LinuxLayouts.SOCKADDR_IN_SIZE);
 * }
 * }</pre>
 *
 * <h2>Scatter/Gather I/O Example</h2>
 *
 * <pre>{@code
 * StructAccessor iovec = StructAccessor.of(LinuxLayouts.IOVEC);
 *
 * try (Arena arena = Arena.ofConfined()) {
 *     MemorySegment iovecs = iovec.allocateArray(arena, 2);
 *     MemorySegment buf1 = arena.allocateFrom("Hello ");
 *     MemorySegment buf2 = arena.allocateFrom("World!\n");
 *
 *     iovec.setPointer(iovec.elementAt(iovecs, 0), "iov_base", buf1);
 *     iovec.setLong(iovec.elementAt(iovecs, 0), "iov_len", buf1.byteSize());
 *     iovec.setPointer(iovec.elementAt(iovecs, 1), "iov_base", buf2);
 *     iovec.setLong(iovec.elementAt(iovecs, 1), "iov_len", buf2.byteSize());
 *
 *     writev.invokeExact(1, iovecs, 2);  // stdout
 * }
 * }</pre>
 */
public final class LinuxLayouts {

    private LinuxLayouts() {}

    // ========================================================================
    // Primitive C Types (LP64 data model)
    // ========================================================================

    /** C {@code char} - 8 bits, signed. */
    public static final ValueLayout C_CHAR = JAVA_BYTE;

    /** C {@code unsigned char} - 8 bits, unsigned. */
    public static final ValueLayout C_UCHAR = JAVA_BYTE;

    /** C {@code short} - 16 bits, signed. */
    public static final ValueLayout C_SHORT = JAVA_SHORT;

    /** C {@code unsigned short} - 16 bits, unsigned. */
    public static final ValueLayout C_USHORT = JAVA_SHORT;

    /** C {@code int} - 32 bits, signed. */
    public static final ValueLayout C_INT = JAVA_INT;

    /** C {@code unsigned int} - 32 bits, unsigned. */
    public static final ValueLayout C_UINT = JAVA_INT;

    /** C {@code long} - 64 bits on LP64 Linux, signed. */
    public static final ValueLayout C_LONG = JAVA_LONG;

    /** C {@code unsigned long} - 64 bits on LP64 Linux, unsigned. */
    public static final ValueLayout C_ULONG = JAVA_LONG;

    /** C {@code long long} - 64 bits, signed. */
    public static final ValueLayout C_LONG_LONG = JAVA_LONG;

    /** C {@code unsigned long long} - 64 bits, unsigned. */
    public static final ValueLayout C_ULONG_LONG = JAVA_LONG;

    /** C {@code float} - 32-bit IEEE 754. */
    public static final ValueLayout C_FLOAT = JAVA_FLOAT;

    /** C {@code double} - 64-bit IEEE 754. */
    public static final ValueLayout C_DOUBLE = JAVA_DOUBLE;

    /** C pointer - 64 bits on LP64. */
    public static final ValueLayout C_POINTER = ADDRESS;

    /** C {@code size_t} - unsigned, pointer-sized. */
    public static final ValueLayout C_SIZE_T = JAVA_LONG;

    /** C {@code ssize_t} - signed, pointer-sized. */
    public static final ValueLayout C_SSIZE_T = JAVA_LONG;

    /** C {@code off_t} - file offset type (64-bit on modern Linux). */
    public static final ValueLayout C_OFF_T = JAVA_LONG;

    /** C {@code pid_t} - process ID (32-bit signed). */
    public static final ValueLayout C_PID_T = JAVA_INT;

    /** C {@code uid_t} - user ID (32-bit unsigned). */
    public static final ValueLayout C_UID_T = JAVA_INT;

    /** C {@code gid_t} - group ID (32-bit unsigned). */
    public static final ValueLayout C_GID_T = JAVA_INT;

    // ========================================================================
    // File Descriptor
    // ========================================================================

    /** File descriptor type - 32-bit signed integer. */
    public static final ValueLayout FD = JAVA_INT;

    // ========================================================================
    // Socket Structures
    // ========================================================================

    /**
     * {@code struct iovec} - scatter/gather I/O vector.
     *
     * <p>Used with {@code readv}, {@code writev}, {@code sendmsg}, {@code recvmsg} for efficient
     * multi-buffer I/O operations.
     *
     * <pre>
     * struct iovec {
     *     void  *iov_base;  // Starting address
     *     size_t iov_len;   // Number of bytes
     * };
     * </pre>
     *
     * <h3>Example: writev with multiple buffers</h3>
     *
     * <pre>{@code
     * StructAccessor iov = StructAccessor.of(LinuxLayouts.IOVEC);
     * MemorySegment iovecs = iov.allocateArray(arena, 2);
     *
     * MemorySegment iov0 = iov.elementAt(iovecs, 0);
     * iov.setPointer(iov0, "iov_base", headerBuf);
     * iov.setLong(iov0, "iov_len", headerBuf.byteSize());
     *
     * MemorySegment iov1 = iov.elementAt(iovecs, 1);
     * iov.setPointer(iov1, "iov_base", dataBuf);
     * iov.setLong(iov1, "iov_len", dataBuf.byteSize());
     *
     * long written = (long) writev.invokeExact(fd, iovecs, 2);
     * }</pre>
     */
    public static final StructLayout IOVEC =
            structLayout(ADDRESS.withName("iov_base"), JAVA_LONG.withName("iov_len"))
                    .withName("iovec");

    /** Offset of {@code iov_base} in {@link #IOVEC}. */
    public static final long IOVEC_IOV_BASE = 0L;

    /** Offset of {@code iov_len} in {@link #IOVEC}. */
    public static final long IOVEC_IOV_LEN = 8L;

    /**
     * {@code struct sockaddr_in} - IPv4 socket address.
     *
     * <pre>
     * struct sockaddr_in {
     *     sa_family_t    sin_family;  // AF_INET
     *     in_port_t      sin_port;    // Port number (network byte order)
     *     struct in_addr sin_addr;    // IPv4 address
     *     char           sin_zero[8]; // Padding
     * };
     * </pre>
     */
    public static final StructLayout SOCKADDR_IN =
            structLayout(
                            JAVA_SHORT.withName("sin_family"),
                            JAVA_SHORT.withName("sin_port"),
                            JAVA_INT.withName("sin_addr"),
                            sequenceLayout(8, JAVA_BYTE).withName("sin_zero"))
                    .withName("sockaddr_in");

    /** Size of {@link #SOCKADDR_IN} in bytes (16). */
    public static final long SOCKADDR_IN_SIZE = SOCKADDR_IN.byteSize();

    /**
     * {@code struct sockaddr_in6} - IPv6 socket address.
     *
     * <pre>
     * struct sockaddr_in6 {
     *     sa_family_t     sin6_family;   // AF_INET6
     *     in_port_t       sin6_port;     // Port number
     *     uint32_t        sin6_flowinfo; // IPv6 flow information
     *     struct in6_addr sin6_addr;     // IPv6 address (16 bytes)
     *     uint32_t        sin6_scope_id; // Scope ID
     * };
     * </pre>
     */
    public static final StructLayout SOCKADDR_IN6 =
            structLayout(
                            JAVA_SHORT.withName("sin6_family"),
                            JAVA_SHORT.withName("sin6_port"),
                            JAVA_INT.withName("sin6_flowinfo"),
                            sequenceLayout(16, JAVA_BYTE).withName("sin6_addr"),
                            JAVA_INT.withName("sin6_scope_id"))
                    .withName("sockaddr_in6");

    /** Size of {@link #SOCKADDR_IN6} in bytes (28). */
    public static final long SOCKADDR_IN6_SIZE = SOCKADDR_IN6.byteSize();

    /**
     * {@code struct msghdr} - message header for sendmsg/recvmsg.
     *
     * <pre>
     * struct msghdr {
     *     void         *msg_name;       // Optional address
     *     socklen_t     msg_namelen;    // Size of address
     *     struct iovec *msg_iov;        // Scatter/gather array
     *     size_t        msg_iovlen;     // # elements in msg_iov
     *     void         *msg_control;    // Ancillary data
     *     size_t        msg_controllen; // Ancillary data length
     *     int           msg_flags;      // Flags on received message
     * };
     * </pre>
     */
    public static final StructLayout MSGHDR =
            structLayout(
                            ADDRESS.withName("msg_name"),
                            JAVA_INT.withName("msg_namelen"),
                            paddingLayout(4),
                            ADDRESS.withName("msg_iov"),
                            JAVA_LONG.withName("msg_iovlen"),
                            ADDRESS.withName("msg_control"),
                            JAVA_LONG.withName("msg_controllen"),
                            JAVA_INT.withName("msg_flags"),
                            paddingLayout(4))
                    .withName("msghdr");

    /** Size of {@link #MSGHDR} in bytes (56). */
    public static final long MSGHDR_SIZE = MSGHDR.byteSize();

    // ========================================================================
    // Epoll Structures
    // ========================================================================

    /**
     * {@code struct epoll_event} - epoll event structure.
     *
     * <pre>
     * struct epoll_event {
     *     uint32_t     events;  // Epoll events
     *     epoll_data_t data;    // User data (union, 8 bytes)
     * };
     * </pre>
     *
     * <p>Note: This struct is packed on x86_64 (__attribute__((packed))), so we add explicit
     * padding to maintain 4-byte alignment for the data field.
     */
    public static final StructLayout EPOLL_EVENT =
            structLayout(JAVA_INT.withName("events"), paddingLayout(4), JAVA_LONG.withName("data"))
                    .withName("epoll_event");

    /** Size of {@link #EPOLL_EVENT} in bytes (16 with padding for alignment). */
    public static final long EPOLL_EVENT_SIZE = EPOLL_EVENT.byteSize();

    // ========================================================================
    // Timespec Structure
    // ========================================================================

    /**
     * {@code struct timespec} - time specification.
     *
     * <pre>
     * struct timespec {
     *     time_t tv_sec;  // seconds
     *     long   tv_nsec; // nanoseconds
     * };
     * </pre>
     */
    public static final StructLayout TIMESPEC =
            structLayout(JAVA_LONG.withName("tv_sec"), JAVA_LONG.withName("tv_nsec"))
                    .withName("timespec");

    /** Size of {@link #TIMESPEC} in bytes (16). */
    public static final long TIMESPEC_SIZE = TIMESPEC.byteSize();

    // ========================================================================
    // Address Family Constants
    // ========================================================================

    /** AF_UNIX / AF_LOCAL - Local communication. */
    public static final short AF_UNIX = 1;

    /** AF_INET - IPv4 Internet protocols. */
    public static final short AF_INET = 2;

    /** AF_INET6 - IPv6 Internet protocols. */
    public static final short AF_INET6 = 10;

    // ========================================================================
    // Socket Type Constants
    // ========================================================================

    /** SOCK_STREAM - Sequenced, reliable, connection-based byte streams. */
    public static final int SOCK_STREAM = 1;

    /** SOCK_DGRAM - Connectionless, unreliable datagrams. */
    public static final int SOCK_DGRAM = 2;

    /** SOCK_NONBLOCK - Set O_NONBLOCK on the new socket. */
    public static final int SOCK_NONBLOCK = 0x800;

    /** SOCK_CLOEXEC - Set close-on-exec flag. */
    public static final int SOCK_CLOEXEC = 0x80000;

    // ========================================================================
    // Epoll Constants
    // ========================================================================

    /** EPOLLIN - Available for read. */
    public static final int EPOLLIN = 0x001;

    /** EPOLLOUT - Available for write. */
    public static final int EPOLLOUT = 0x004;

    /** EPOLLERR - Error condition. */
    public static final int EPOLLERR = 0x008;

    /** EPOLLHUP - Hang up. */
    public static final int EPOLLHUP = 0x010;

    /** EPOLLET - Edge-triggered. */
    public static final int EPOLLET = 1 << 31;

    /** EPOLLONESHOT - One-shot behavior. */
    public static final int EPOLLONESHOT = 1 << 30;

    /** EPOLL_CTL_ADD - Add entry to epoll. */
    public static final int EPOLL_CTL_ADD = 1;

    /** EPOLL_CTL_DEL - Remove entry from epoll. */
    public static final int EPOLL_CTL_DEL = 2;

    /** EPOLL_CTL_MOD - Modify entry in epoll. */
    public static final int EPOLL_CTL_MOD = 3;
}
