package express.mvp.roray.utils.functions;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link LinuxLayouts}. */
class LinuxLayoutsTest {

    // ========================================================================
    // Primitive Type Size Tests
    // ========================================================================

    @Test
    @DisplayName("C_CHAR should be 1 byte")
    void charShouldBe1Byte() {
        assertEquals(1, LinuxLayouts.C_CHAR.byteSize());
    }

    @Test
    @DisplayName("C_SHORT should be 2 bytes")
    void shortShouldBe2Bytes() {
        assertEquals(2, LinuxLayouts.C_SHORT.byteSize());
    }

    @Test
    @DisplayName("C_INT should be 4 bytes")
    void intShouldBe4Bytes() {
        assertEquals(4, LinuxLayouts.C_INT.byteSize());
    }

    @Test
    @DisplayName("C_LONG should be 8 bytes on LP64")
    void longShouldBe8Bytes() {
        assertEquals(8, LinuxLayouts.C_LONG.byteSize());
    }

    @Test
    @DisplayName("C_POINTER should be 8 bytes on 64-bit")
    void pointerShouldBe8Bytes() {
        assertEquals(8, LinuxLayouts.C_POINTER.byteSize());
    }

    @Test
    @DisplayName("C_SIZE_T should be 8 bytes")
    void sizeTShouldBe8Bytes() {
        assertEquals(8, LinuxLayouts.C_SIZE_T.byteSize());
    }

    @Test
    @DisplayName("FD should be 4 bytes (int)")
    void fd_shouldBe4Bytes() {
        assertEquals(4, LinuxLayouts.FD.byteSize());
    }

    // ========================================================================
    // Struct Layout Tests
    // ========================================================================

    @Test
    @DisplayName("IOVEC should be 16 bytes")
    void iovec_shouldBe16Bytes() {
        assertEquals(16, LinuxLayouts.IOVEC.byteSize());
        // iov_base (8 bytes pointer) + iov_len (8 bytes size_t) = 16 bytes
        assertEquals(16, LinuxLayouts.IOVEC_IOV_LEN + 8);
    }

    @Test
    @DisplayName("IOVEC should have correct field offsets")
    void iovec_shouldHaveCorrectOffsets() {
        assertEquals(0, LinuxLayouts.IOVEC_IOV_BASE);
        assertEquals(8, LinuxLayouts.IOVEC_IOV_LEN);
    }

    @Test
    @DisplayName("SOCKADDR_IN should be 16 bytes")
    void sockaddrIn_shouldBe16Bytes() {
        assertEquals(16, LinuxLayouts.SOCKADDR_IN_SIZE);
        assertEquals(16, LinuxLayouts.SOCKADDR_IN.byteSize());
    }

    @Test
    @DisplayName("SOCKADDR_IN should have named fields")
    void sockaddrIn_shouldHaveNamedFields() {
        StructLayout layout = LinuxLayouts.SOCKADDR_IN;

        // Should not throw - fields exist
        assertDoesNotThrow(
                () -> layout.byteOffset(MemoryLayout.PathElement.groupElement("sin_family")));
        assertDoesNotThrow(
                () -> layout.byteOffset(MemoryLayout.PathElement.groupElement("sin_port")));
        assertDoesNotThrow(
                () -> layout.byteOffset(MemoryLayout.PathElement.groupElement("sin_addr")));
    }

    @Test
    @DisplayName("SOCKADDR_IN6 should be 28 bytes")
    void sockaddrIn6_shouldBe28Bytes() {
        assertEquals(28, LinuxLayouts.SOCKADDR_IN6_SIZE);
    }

    @Test
    @DisplayName("MSGHDR should be 56 bytes")
    void msghdr_shouldBe56Bytes() {
        assertEquals(56, LinuxLayouts.MSGHDR_SIZE);
    }

    @Test
    @DisplayName("EPOLL_EVENT should be 16 bytes (with alignment padding)")
    void epollEvent_shouldBe16Bytes() {
        // Note: The real packed struct is 12 bytes on x86_64, but Java FFM
        // requires proper alignment, so we use 16 bytes with padding
        assertEquals(16, LinuxLayouts.EPOLL_EVENT_SIZE);
    }

    @Test
    @DisplayName("TIMESPEC should be 16 bytes")
    void timespec_shouldBe16Bytes() {
        assertEquals(16, LinuxLayouts.TIMESPEC_SIZE);
    }

    // ========================================================================
    // Constant Value Tests
    // ========================================================================

    @Test
    @DisplayName("AF_INET should be 2")
    void afInet_shouldBe2() {
        assertEquals(2, LinuxLayouts.AF_INET);
    }

    @Test
    @DisplayName("AF_INET6 should be 10")
    void afInet6_shouldBe10() {
        assertEquals(10, LinuxLayouts.AF_INET6);
    }

    @Test
    @DisplayName("SOCK_STREAM should be 1")
    void sockStream_shouldBe1() {
        assertEquals(1, LinuxLayouts.SOCK_STREAM);
    }

    @Test
    @DisplayName("SOCK_DGRAM should be 2")
    void sockDgram_shouldBe2() {
        assertEquals(2, LinuxLayouts.SOCK_DGRAM);
    }

    @Test
    @DisplayName("EPOLLIN should be 0x001")
    void epollin_shouldBeCorrect() {
        assertEquals(0x001, LinuxLayouts.EPOLLIN);
    }

    @Test
    @DisplayName("EPOLLOUT should be 0x004")
    void epollout_shouldBeCorrect() {
        assertEquals(0x004, LinuxLayouts.EPOLLOUT);
    }

    @Test
    @DisplayName("EPOLLET should have high bit set")
    void epollet_shouldHaveHighBitSet() {
        assertTrue(LinuxLayouts.EPOLLET < 0, "EPOLLET should be negative (high bit set)");
        assertEquals(1 << 31, LinuxLayouts.EPOLLET);
    }

    // ========================================================================
    // Struct Field Access Tests
    // ========================================================================

    @Test
    @DisplayName("IOVEC should have accessible fields")
    void iovec_shouldHaveAccessibleFields() {
        StructLayout iovec = LinuxLayouts.IOVEC;

        assertEquals(0, iovec.byteOffset(MemoryLayout.PathElement.groupElement("iov_base")));
        assertEquals(8, iovec.byteOffset(MemoryLayout.PathElement.groupElement("iov_len")));
    }

    @Test
    @DisplayName("EPOLL_EVENT should have accessible fields")
    void epollEvent_shouldHaveAccessibleFields() {
        StructLayout epoll = LinuxLayouts.EPOLL_EVENT;

        assertEquals(0, epoll.byteOffset(MemoryLayout.PathElement.groupElement("events")));
        // data is at offset 8 (after 4-byte events + 4-byte padding)
        assertEquals(8, epoll.byteOffset(MemoryLayout.PathElement.groupElement("data")));
    }

    @Test
    @DisplayName("All layouts should be non-null")
    void allLayouts_shouldBeNonNull() {
        assertNotNull(LinuxLayouts.C_CHAR);
        assertNotNull(LinuxLayouts.C_INT);
        assertNotNull(LinuxLayouts.C_LONG);
        assertNotNull(LinuxLayouts.C_POINTER);
        assertNotNull(LinuxLayouts.IOVEC);
        assertNotNull(LinuxLayouts.SOCKADDR_IN);
        assertNotNull(LinuxLayouts.SOCKADDR_IN6);
        assertNotNull(LinuxLayouts.MSGHDR);
        assertNotNull(LinuxLayouts.EPOLL_EVENT);
        assertNotNull(LinuxLayouts.TIMESPEC);
    }
}
