package express.mvp.roray.ffm.utils.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class SegmentUtilsBenchmark {

    @Param({"32", "256"})
    int size;

    Arena arena;
    byte[] leftBytes;
    byte[] rightBytes;
    MemorySegment leftSegment;
    MemorySegment rightSegment;

    @Setup(Level.Trial)
    public void setup() {
        arena = Arena.ofConfined();
        leftBytes = new byte[size];
        rightBytes = new byte[size];
        leftSegment = arena.allocate(size);
        rightSegment = arena.allocate(size);
        for (int i = 0; i < size; i++) {
            byte value = (byte) (i * 31 + 7);
            leftBytes[i] = value;
            rightBytes[i] = value;
            leftSegment.set(Layouts.BYTE, i, value);
            rightSegment.set(Layouts.BYTE, i, value);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        arena.close();
    }

    @Benchmark
    public long hashByteArray() {
        return SegmentUtils.fnv1a64(leftBytes);
    }

    @Benchmark
    public long hashSegment() {
        return SegmentUtils.fnv1a64(leftSegment, size);
    }

    @Benchmark
    public boolean equalsBytesToSegment() {
        return SegmentUtils.contentEquals(leftBytes, leftSegment, size);
    }

    @Benchmark
    public boolean equalsSegmentToSegment() {
        return SegmentUtils.contentEquals(leftSegment, rightSegment, size);
    }
}
