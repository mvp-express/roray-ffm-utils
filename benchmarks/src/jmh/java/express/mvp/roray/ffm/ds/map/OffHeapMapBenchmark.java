package express.mvp.roray.ffm.ds.map;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class OffHeapMapBenchmark {

    @Param({ "1024", "65536" })
    int capacity;

    OffHeapLongIntMap offHeapMap;
    Map<Long, Integer> jdkMap;
    Map<Long, Integer> concurrentMap;

    long key = 12345L;

    @Setup(Level.Trial)
    public void setup() {
        offHeapMap = new OffHeapLongIntMapImpl(capacity);
        jdkMap = new HashMap<>(capacity);
        concurrentMap = new ConcurrentHashMap<>(capacity);

        // Pre-populate to avoid resize noise during benchmark
        for (int i = 0; i < capacity / 2; i++) {
            offHeapMap.put(i, i);
            jdkMap.put((long) i, i);
            concurrentMap.put((long) i, i);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        offHeapMap.close();
    }

    @Benchmark
    public void testOffHeapGet(Blackhole bh) {
        bh.consume(offHeapMap.getPacked(key));
    }

    @Benchmark
    public void testJdkMapGet(Blackhole bh) {
        bh.consume(jdkMap.get(key));
    }

    @Benchmark
    public void testConcurrentMapGet(Blackhole bh) {
        bh.consume(concurrentMap.get(key));
    }

    @Benchmark
    public void testOffHeapPut(Blackhole bh) {
        // Overwrite existing key to avoid resizing/full map issues in loop
        offHeapMap.put(key, 999);
    }

    @Benchmark
    public void testJdkMapPut(Blackhole bh) {
        jdkMap.put(key, 999);
    }
}
