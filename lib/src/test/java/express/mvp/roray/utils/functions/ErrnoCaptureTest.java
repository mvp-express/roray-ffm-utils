package express.mvp.roray.utils.functions;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Tests for {@link ErrnoCapture}. */
@EnabledOnOs(OS.LINUX)
class ErrnoCaptureTest {

    @Test
    @DisplayName("captureOption() should return non-null")
    void captureOption_shouldReturnNonNull() {
        Linker.Option option = ErrnoCapture.captureOption();
        assertNotNull(option);
    }

    @Test
    @DisplayName("allocateCaptureState() should allocate correct size")
    void allocateCaptureState_shouldAllocateCorrectSize() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capturedState = ErrnoCapture.allocateCaptureState(arena);

            assertNotNull(capturedState);
            assertEquals(ErrnoCapture.captureStateSize(), capturedState.byteSize());
        }
    }

    @Test
    @DisplayName("captureStateSize() should return positive value")
    void captureStateSize_shouldReturnPositive() {
        assertTrue(ErrnoCapture.captureStateSize() > 0);
    }

    @Test
    @DisplayName("strerror() should return description for known errors")
    void strerror_shouldReturnDescriptionForKnownErrors() {
        assertEquals("Success", ErrnoCapture.strerror(0));
        assertTrue(ErrnoCapture.strerror(1).contains("EPERM"));
        assertTrue(ErrnoCapture.strerror(2).contains("ENOENT"));
        assertTrue(ErrnoCapture.strerror(13).contains("EACCES"));
        assertTrue(ErrnoCapture.strerror(22).contains("EINVAL"));
    }

    @Test
    @DisplayName("strerror() should handle unknown errors")
    void strerror_shouldHandleUnknownErrors() {
        String unknown = ErrnoCapture.strerror(9999);
        assertTrue(unknown.contains("Unknown") || unknown.contains("9999"));
    }

    @Test
    @DisplayName("strerror() should return socket errors")
    void strerror_shouldReturnSocketErrors() {
        assertTrue(ErrnoCapture.strerror(111).contains("ECONNREFUSED"));
        assertTrue(ErrnoCapture.strerror(104).contains("ECONNRESET"));
        assertTrue(ErrnoCapture.strerror(110).contains("ETIMEDOUT"));
    }

    @Test
    @DisplayName("errno constants should have correct values")
    void errnoConstants_shouldHaveCorrectValues() {
        assertEquals(1, ErrnoCapture.EPERM);
        assertEquals(2, ErrnoCapture.ENOENT);
        assertEquals(4, ErrnoCapture.EINTR);
        assertEquals(9, ErrnoCapture.EBADF);
        assertEquals(11, ErrnoCapture.EAGAIN);
        assertEquals(12, ErrnoCapture.ENOMEM);
        assertEquals(13, ErrnoCapture.EACCES);
        assertEquals(22, ErrnoCapture.EINVAL);
        assertEquals(32, ErrnoCapture.EPIPE);
        assertEquals(111, ErrnoCapture.ECONNREFUSED);
    }

    @Test
    @DisplayName("getErrno() should read errno from captured state")
    void getErrno_shouldReadFromCapturedState() throws Throwable {
        // This is a more comprehensive test that actually captures errno
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        // Create a downcall that will fail and set errno
        // open() with invalid path should fail
        java.lang.invoke.MethodHandle open =
                factory.downcall(
                        "open",
                        FunctionDescriptorBuilder.returnsInt()
                                .args(LinuxLayouts.C_POINTER, LinuxLayouts.C_INT)
                                .build(),
                        ErrnoCapture.captureOption());

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capturedState = ErrnoCapture.allocateCaptureState(arena);
            MemorySegment path = arena.allocateFrom("/nonexistent/path/to/file");

            int fd = (int) open.invokeExact(capturedState, path, 0); // O_RDONLY = 0

            assertTrue(fd < 0, "open() should fail for nonexistent file");

            int errno = ErrnoCapture.getErrno(capturedState);
            assertEquals(
                    ErrnoCapture.ENOENT, errno, "errno should be ENOENT (2) for nonexistent file");
        }
    }

    @Test
    @DisplayName("captured errno should be correct for EACCES")
    void capturedErrno_shouldBeCorrectForEacces() throws Throwable {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        java.lang.invoke.MethodHandle open =
                factory.downcall(
                        "open",
                        FunctionDescriptorBuilder.returnsInt()
                                .args(LinuxLayouts.C_POINTER, LinuxLayouts.C_INT)
                                .build(),
                        ErrnoCapture.captureOption());

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capturedState = ErrnoCapture.allocateCaptureState(arena);
            // Try to open /etc/shadow which requires root
            MemorySegment path = arena.allocateFrom("/etc/shadow");

            int fd = (int) open.invokeExact(capturedState, path, 0);

            if (fd < 0) {
                int errno = ErrnoCapture.getErrno(capturedState);
                // Should be either EACCES (permission denied) or ENOENT (not exists)
                assertTrue(
                        errno == ErrnoCapture.EACCES || errno == ErrnoCapture.ENOENT,
                        "Expected EACCES or ENOENT, got: " + errno);
            }
            // If fd >= 0, we're running as root - just close it
            if (fd >= 0) {
                java.lang.invoke.MethodHandle close =
                        factory.downcall(
                                "close",
                                FunctionDescriptorBuilder.returnsInt()
                                        .args(LinuxLayouts.FD)
                                        .build());
                close.invokeExact(fd);
            }
        }
    }

    @Test
    @DisplayName("multiple errno captures should be independent")
    void multipleErrnoCaptures_shouldBeIndependent() throws Throwable {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        java.lang.invoke.MethodHandle open =
                factory.downcall(
                        "open",
                        FunctionDescriptorBuilder.returnsInt()
                                .args(LinuxLayouts.C_POINTER, LinuxLayouts.C_INT)
                                .build(),
                        ErrnoCapture.captureOption());

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment state1 = ErrnoCapture.allocateCaptureState(arena);
            MemorySegment state2 = ErrnoCapture.allocateCaptureState(arena);

            MemorySegment path = arena.allocateFrom("/nonexistent/file");

            // First call (capture return value to match invokeExact signature)
            int result1 = (int) open.invokeExact(state1, path, 0);
            int errno1 = ErrnoCapture.getErrno(state1);

            // Second call with different state
            int result2 = (int) open.invokeExact(state2, path, 0);
            int errno2 = ErrnoCapture.getErrno(state2);

            assertEquals(-1, result1, "open should return -1 on error");
            assertEquals(-1, result2, "open should return -1 on error");
            assertEquals(errno1, errno2, "Both should capture ENOENT");
            assertEquals(ErrnoCapture.ENOENT, errno1);
        }
    }
}
