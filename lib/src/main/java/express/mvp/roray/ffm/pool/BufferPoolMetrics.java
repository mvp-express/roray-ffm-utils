package express.mvp.roray.ffm.pool;

/** Immutable snapshot of buffer pool performance metrics. */
public record BufferPoolMetrics(
        long totalAcquisitions,
        long successfulAcquisitions,
        long failedAcquisitions,
        long avgWaitTimeNanos,
        long maxWaitTimeNanos,
        int currentAvailable,
        int poolSize) {

    public double successRate() {
        return totalAcquisitions == 0 ? 0.0 : (double) successfulAcquisitions / totalAcquisitions;
    }

    public double failureRate() {
        return totalAcquisitions == 0 ? 0.0 : (double) failedAcquisitions / totalAcquisitions;
    }

    public double utilization() {
        return poolSize == 0 ? 0.0 : 1.0 - (double) currentAvailable / poolSize;
    }

    public double avgWaitTimeMillis() {
        return avgWaitTimeNanos / 1_000_000.0;
    }

    public double maxWaitTimeMillis() {
        return maxWaitTimeNanos / 1_000_000.0;
    }

    @Override
    public String toString() {
        return String.format(
                "BufferPoolMetrics[total=%d, success=%d, failed=%d, avgWait=%.3fms, maxWait=%.3fms,"
                        + " available=%d/%d, utilization=%.1f%%]",
                totalAcquisitions,
                successfulAcquisitions,
                failedAcquisitions,
                avgWaitTimeMillis(),
                maxWaitTimeMillis(),
                currentAvailable,
                poolSize,
                utilization() * 100);
    }
}
