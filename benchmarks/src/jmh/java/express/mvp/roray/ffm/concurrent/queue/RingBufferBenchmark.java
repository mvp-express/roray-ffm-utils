package express.mvp.roray.ffm.concurrent.queue;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class RingBufferBenchmark {

    @Param({ "1024", "65536" })
    int capacity;

    RingBuffer ringBuffer;
    ArrayBlockingQueue<Integer> jdkQueue;

    // For JCTools comparison if we add it later
    // MpmcArrayQueue<Integer> jcQueue;

    @Setup(Level.Trial)
    public void setup() {
        ringBuffer = new RingBufferImpl(capacity);
        jdkQueue = new ArrayBlockingQueue<>(capacity);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        ringBuffer.close();
    }

    @Benchmark
    public void testRingBufferOfferPoll(Blackhole bh) {
        // Single producer single consumer pattern in a loop
        // Note: This is a synthetic test. Real contention requires @Group threads.
        // Here we just test raw instruction overhead.

        if (ringBuffer.offer(42)) {
            bh.consume(ringBuffer.poll());
        }
    }

    @Benchmark
    public void testJdkQueueOfferPoll(Blackhole bh) {
        if (jdkQueue.offer(42)) {
            bh.consume(jdkQueue.poll());
        }
    }
}
