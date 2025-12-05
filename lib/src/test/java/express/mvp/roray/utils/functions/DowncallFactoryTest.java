package express.mvp.roray.utils.functions;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Tests for {@link DowncallFactory}. */
@EnabledOnOs(OS.LINUX)
class DowncallFactoryTest {

    @Test
    @DisplayName("forNativeLinker() should create factory with default lookup")
    void forNativeLinker_shouldCreateFactory() {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        assertNotNull(factory);
        assertNotNull(factory.linker());
        assertNotNull(factory.lookup());
    }

    @Test
    @DisplayName("downcall() should find and create handle for getpid")
    void downcall_shouldFindGetpid() throws Throwable {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        MethodHandle getpid =
                factory.downcall("getpid", FunctionDescriptorBuilder.returnsInt().build());

        assertNotNull(getpid);

        int pid = (int) getpid.invokeExact();
        assertTrue(pid > 0, "PID should be positive");
    }

    @Test
    @DisplayName("downcall() should find and create handle for getuid")
    void downcall_shouldFindGetuid() throws Throwable {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        MethodHandle getuid =
                factory.downcall("getuid", FunctionDescriptorBuilder.returnsInt().build());

        int uid = (int) getuid.invokeExact();
        assertTrue(uid >= 0, "UID should be non-negative");
    }

    @Test
    @DisplayName("downcall() should throw for non-existent symbol")
    void downcall_shouldThrowForNonExistentSymbol() {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        assertThrows(
                UnsatisfiedLinkError.class,
                () -> {
                    factory.downcall(
                            "nonexistent_function_xyz123",
                            FunctionDescriptorBuilder.returnsVoid().build());
                });
    }

    @Test
    @DisplayName("downcall() with options should work")
    void downcall_withOptions_shouldWork() throws Throwable {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        // Using critical option for a simple syscall
        MethodHandle getpid =
                factory.downcall(
                        "getpid",
                        FunctionDescriptorBuilder.returnsInt().build(),
                        Linker.Option.critical(false));

        int pid = (int) getpid.invokeExact();
        assertTrue(pid > 0);
    }

    @Test
    @DisplayName("findSymbol() should return valid address")
    void findSymbol_shouldReturnValidAddress() {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        MemorySegment symbol = factory.findSymbol("getpid");

        assertNotNull(symbol);
        assertNotEquals(MemorySegment.NULL, symbol);
    }

    @Test
    @DisplayName("findSymbol() should throw for non-existent symbol")
    void findSymbol_shouldThrowForNonExistentSymbol() {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        assertThrows(
                UnsatisfiedLinkError.class,
                () -> {
                    factory.findSymbol("nonexistent_symbol_abc");
                });
    }

    @Test
    @DisplayName("downcallVirtual() should create handle without symbol")
    void downcallVirtual_shouldCreateHandle() {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        // Virtual downcall - function pointer is passed at call time
        MethodHandle virtualCall =
                factory.downcallVirtual(FunctionDescriptorBuilder.returnsInt().build());

        assertNotNull(virtualCall);
        // The handle expects a MemorySegment (function pointer) as first arg
        // For returnsInt() with no args, the handle takes just the function pointer
        assertEquals(1, virtualCall.type().parameterCount());
        assertEquals(MemorySegment.class, virtualCall.type().parameterType(0));
        assertEquals(int.class, virtualCall.type().returnType());
    }

    @Test
    @DisplayName("withLookup() should use custom lookup")
    void withLookup_shouldUseCustomLookup() {
        SymbolLookup customLookup = Linker.nativeLinker().defaultLookup();
        DowncallFactory factory = DowncallFactory.withLookup(customLookup);

        assertNotNull(factory);
        assertEquals(customLookup, factory.lookup());
    }

    @Test
    @DisplayName("linker() and lookup() should return non-null")
    void linkerAndLookup_shouldReturnNonNull() {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        assertNotNull(factory.linker());
        assertNotNull(factory.lookup());
    }

    @Test
    @DisplayName("multiple factories should work independently")
    void multipleFactories_shouldWorkIndependently() throws Throwable {
        DowncallFactory factory1 = DowncallFactory.forNativeLinker();
        DowncallFactory factory2 = DowncallFactory.forNativeLinker();

        MethodHandle handle1 =
                factory1.downcall("getpid", FunctionDescriptorBuilder.returnsInt().build());
        MethodHandle handle2 =
                factory2.downcall("getpid", FunctionDescriptorBuilder.returnsInt().build());

        int pid1 = (int) handle1.invokeExact();
        int pid2 = (int) handle2.invokeExact();

        assertEquals(pid1, pid2, "Both should return same PID");
    }

    @Test
    @DisplayName("downcall with write() should work correctly")
    void downcall_write_shouldWork() throws Throwable {
        DowncallFactory factory = DowncallFactory.forNativeLinker();

        MethodHandle write =
                factory.downcall(
                        "write",
                        FunctionDescriptorBuilder.returnsLong()
                                .args(
                                        LinuxLayouts.FD,
                                        LinuxLayouts.C_POINTER,
                                        LinuxLayouts.C_SIZE_T)
                                .build());

        assertNotNull(write);
        // We don't actually call write() to avoid side effects in tests
    }
}
