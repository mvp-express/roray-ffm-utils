package express.mvp.roray.ffm.functions;

import express.mvp.roray.ffm.utils.functions.DowncallFactory;
import express.mvp.roray.ffm.utils.functions.FunctionDescriptorBuilder;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark to validate that the FFM helper abstractions introduce
 * zero runtime overhead compared to raw FFM API usage.
 *
 * <p>The key insight is that both approaches produce the same {@link MethodHandle}
 * stored in a {@code static final} field, allowing the JIT to inline the call.
 * The helper only affects setup time, not call time.
 *
 * <p>Expected result: Both benchmarks should be identical within measurement noise.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgs = {"--enable-native-access=ALL-UNNAMED"})
public class DowncallOverheadBenchmark {

    // ========================================================================
    // RAW FFM - Baseline
    // ========================================================================
    
    private static final MethodHandle GETPID_RAW;
    private static final MethodHandle GETUID_RAW;
    private static final MethodHandle GETEUID_RAW;
    
    static {
        Linker linker = Linker.nativeLinker();
        SymbolLookup lookup = linker.defaultLookup();
        
        GETPID_RAW = linker.downcallHandle(
            lookup.find("getpid").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );
        
        GETUID_RAW = linker.downcallHandle(
            lookup.find("getuid").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );
        
        GETEUID_RAW = linker.downcallHandle(
            lookup.find("geteuid").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
        );
    }

    // ========================================================================
    // HELPER-BASED - Using FunctionDescriptorBuilder + DowncallFactory
    // ========================================================================
    
    private static final DowncallFactory FACTORY = DowncallFactory.forNativeLinker();
    
    private static final MethodHandle GETPID_HELPER = FACTORY.downcall(
        "getpid",
        FunctionDescriptorBuilder.returnsInt().build()
    );
    
    private static final MethodHandle GETUID_HELPER = FACTORY.downcall(
        "getuid",
        FunctionDescriptorBuilder.returnsInt().build()
    );
    
    private static final MethodHandle GETEUID_HELPER = FACTORY.downcall(
        "geteuid",
        FunctionDescriptorBuilder.returnsInt().build()
    );

    // ========================================================================
    // Benchmarks - Simple syscalls (getpid, getuid, geteuid)
    // ========================================================================
    
    @Benchmark
    public int rawGetpid() throws Throwable {
        return (int) GETPID_RAW.invokeExact();
    }
    
    @Benchmark
    public int helperGetpid() throws Throwable {
        return (int) GETPID_HELPER.invokeExact();
    }
    
    @Benchmark
    public int rawGetuid() throws Throwable {
        return (int) GETUID_RAW.invokeExact();
    }
    
    @Benchmark
    public int helperGetuid() throws Throwable {
        return (int) GETUID_HELPER.invokeExact();
    }
    
    @Benchmark
    public int rawGeteuid() throws Throwable {
        return (int) GETEUID_RAW.invokeExact();
    }
    
    @Benchmark
    public int helperGeteuid() throws Throwable {
        return (int) GETEUID_HELPER.invokeExact();
    }
    
    // ========================================================================
    // Batch calls - to amplify any overhead differences
    // ========================================================================
    
    @Benchmark
    public int rawBatch10() throws Throwable {
        int sum = 0;
        sum += (int) GETPID_RAW.invokeExact();
        sum += (int) GETPID_RAW.invokeExact();
        sum += (int) GETPID_RAW.invokeExact();
        sum += (int) GETPID_RAW.invokeExact();
        sum += (int) GETPID_RAW.invokeExact();
        sum += (int) GETPID_RAW.invokeExact();
        sum += (int) GETPID_RAW.invokeExact();
        sum += (int) GETPID_RAW.invokeExact();
        sum += (int) GETPID_RAW.invokeExact();
        sum += (int) GETPID_RAW.invokeExact();
        return sum;
    }
    
    @Benchmark
    public int helperBatch10() throws Throwable {
        int sum = 0;
        sum += (int) GETPID_HELPER.invokeExact();
        sum += (int) GETPID_HELPER.invokeExact();
        sum += (int) GETPID_HELPER.invokeExact();
        sum += (int) GETPID_HELPER.invokeExact();
        sum += (int) GETPID_HELPER.invokeExact();
        sum += (int) GETPID_HELPER.invokeExact();
        sum += (int) GETPID_HELPER.invokeExact();
        sum += (int) GETPID_HELPER.invokeExact();
        sum += (int) GETPID_HELPER.invokeExact();
        sum += (int) GETPID_HELPER.invokeExact();
        return sum;
    }
}
