package express.mvp.roray.utils.functions;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Tests for {@link UpcallFactory}. */
@EnabledOnOs(OS.LINUX)
class UpcallFactoryTest {

    @Test
    @DisplayName("create() should return non-null factory")
    void create_shouldReturnFactory() {
        UpcallFactory factory = UpcallFactory.create();
        assertNotNull(factory);
        assertNotNull(factory.linker());
    }

    @Test
    @DisplayName("upcallStub with MethodHandle should create valid stub")
    void upcallStub_withMethodHandle_shouldCreateStub() throws Throwable {
        UpcallFactory factory = UpcallFactory.create();

        // Create a simple method handle
        MethodHandle target =
                MethodHandles.lookup()
                        .findStatic(
                                UpcallFactoryTest.class,
                                "simpleCallback",
                                MethodType.methodType(int.class, int.class));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment stub =
                    factory.upcallStub(
                            arena,
                            target,
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

            assertNotNull(stub);
            assertNotEquals(MemorySegment.NULL, stub);
        }
    }

    @Test
    @DisplayName("upcallStub with functional interface should work")
    void upcallStub_withFunctionalInterface_shouldWork() {
        UpcallFactory factory = UpcallFactory.create();
        AtomicInteger callCount = new AtomicInteger(0);

        try (Arena arena = Arena.ofConfined()) {
            IntCallback callback =
                    (value) -> {
                        callCount.incrementAndGet();
                        return value * 2;
                    };

            MemorySegment stub =
                    factory.upcallStub(
                            arena,
                            MethodHandles.lookup(),
                            callback,
                            IntCallback.class,
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

            assertNotNull(stub);
            assertNotEquals(MemorySegment.NULL, stub);
        }
    }

    @Test
    @DisplayName("upcallStub should throw for null arena")
    void upcallStub_shouldThrowForNullArena() throws Throwable {
        UpcallFactory factory = UpcallFactory.create();
        MethodHandle target =
                MethodHandles.lookup()
                        .findStatic(
                                UpcallFactoryTest.class,
                                "simpleCallback",
                                MethodType.methodType(int.class, int.class));

        assertThrows(
                NullPointerException.class,
                () -> {
                    factory.upcallStub(
                            null,
                            target,
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                });
    }

    @Test
    @DisplayName("upcallStub should throw for null target")
    void upcallStub_shouldThrowForNullTarget() {
        UpcallFactory factory = UpcallFactory.create();

        try (Arena arena = Arena.ofConfined()) {
            assertThrows(
                    NullPointerException.class,
                    () -> {
                        factory.upcallStub(
                                arena,
                                (MethodHandle) null,
                                FunctionDescriptor.of(ValueLayout.JAVA_INT));
                    });
        }
    }

    @Test
    @DisplayName("upcallStub should throw for null descriptor")
    void upcallStub_shouldThrowForNullDescriptor() throws Throwable {
        UpcallFactory factory = UpcallFactory.create();
        MethodHandle target =
                MethodHandles.lookup()
                        .findStatic(
                                UpcallFactoryTest.class,
                                "simpleCallback",
                                MethodType.methodType(int.class, int.class));

        try (Arena arena = Arena.ofConfined()) {
            assertThrows(
                    NullPointerException.class,
                    () -> {
                        factory.upcallStub(arena, target, null);
                    });
        }
    }

    @Test
    @DisplayName("upcallStub with non-interface should throw")
    void upcallStub_withNonInterface_shouldThrow() {
        UpcallFactory factory = UpcallFactory.create();

        try (Arena arena = Arena.ofConfined()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> {
                        factory.upcallStub(
                                arena,
                                MethodHandles.lookup(),
                                "not a functional interface",
                                String.class, // Not an interface
                                FunctionDescriptor.ofVoid());
                    });
        }
    }

    @Test
    @DisplayName("upcallStub with multi-method interface should throw")
    void upcallStub_withMultiMethodInterface_shouldThrow() {
        UpcallFactory factory = UpcallFactory.create();

        try (Arena arena = Arena.ofConfined()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> {
                        factory.upcallStub(
                                arena,
                                MethodHandles.lookup(),
                                new MultiMethodInterface() {
                                    @Override
                                    public void method1() {}

                                    @Override
                                    public void method2() {}
                                },
                                MultiMethodInterface.class,
                                FunctionDescriptor.ofVoid());
                    });
        }
    }

    @Test
    @DisplayName("upcallStub with void callback should work")
    void upcallStub_withVoidCallback_shouldWork() {
        UpcallFactory factory = UpcallFactory.create();
        AtomicInteger callCount = new AtomicInteger(0);

        try (Arena arena = Arena.ofConfined()) {
            VoidCallback callback = () -> callCount.incrementAndGet();

            MemorySegment stub =
                    factory.upcallStub(
                            arena,
                            MethodHandles.lookup(),
                            callback,
                            VoidCallback.class,
                            FunctionDescriptor.ofVoid());

            assertNotNull(stub);
        }
    }

    @Test
    @DisplayName("upcallStub with public lookup should work for public interfaces")
    void upcallStub_withPublicLookup_shouldWork() {
        UpcallFactory factory = UpcallFactory.create();

        try (Arena arena = Arena.ofConfined()) {
            Runnable callback = () -> {};

            MemorySegment stub =
                    factory.upcallStub(
                            arena, callback, Runnable.class, FunctionDescriptor.ofVoid());

            assertNotNull(stub);
        }
    }

    // Helper method for testing
    public static int simpleCallback(int value) {
        return value * 2;
    }

    // Test interfaces
    @FunctionalInterface
    public interface IntCallback {
        int apply(int value);
    }

    @FunctionalInterface
    public interface VoidCallback {
        void run();
    }

    // Non-functional interface (multiple abstract methods)
    public interface MultiMethodInterface {
        void method1();

        void method2();
    }
}
