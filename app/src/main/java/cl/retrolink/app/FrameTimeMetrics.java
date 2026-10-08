package cl.retrolink.app;

import java.util.Arrays;
import java.util.Locale;

/** Fixed-memory frame/work timing collector used only on-device. */
public final class FrameTimeMetrics {
    private static final int CAPACITY = 600;
    private final long[] workNs = new long[CAPACITY];
    private final long[] intervalNs = new long[CAPACITY];
    private int size;
    private int cursor;
    private long startNs;
    private long lastFrameNs;
    private long totalFrames;

    public synchronized void reset() {
        size = 0;
        cursor = 0;
        startNs = 0L;
        lastFrameNs = 0L;
        totalFrames = 0L;
    }

    public synchronized void recordFrame(long workDurationNs) {
        long now = System.nanoTime();
        if (startNs == 0L) startNs = now;
        long interval = lastFrameNs == 0L ? 0L : now - lastFrameNs;
        if (interval > 500_000_000L) {
            // App pause/background time is not emulation time and must not trigger a downgrade.
            size = 0;
            cursor = 0;
            totalFrames = 0L;
            startNs = now;
            interval = 0L;
        }
        lastFrameNs = now;

        workNs[cursor] = Math.max(0L, workDurationNs);
        intervalNs[cursor] = Math.max(0L, interval);
        cursor = (cursor + 1) % CAPACITY;
        if (size < CAPACITY) size++;
        totalFrames++;
    }

    public synchronized long elapsedNs() {
        return startNs == 0L ? 0L : Math.max(0L, System.nanoTime() - startNs);
    }

    public synchronized int sampleCount() {
        return size;
    }

    public synchronized Snapshot snapshot(long targetIntervalNs) {
        if (size == 0 || startNs == 0L) return Snapshot.empty(targetIntervalNs);
        long[] work = compact(workNs, false);
        long[] intervals = compact(intervalNs, true);
        Arrays.sort(work);
        Arrays.sort(intervals);

        long elapsed = Math.max(1L, System.nanoTime() - startNs);
        double realFps = totalFrames * 1_000_000_000.0 / elapsed;
        double targetFps = targetIntervalNs > 0L ? 1_000_000_000.0 / targetIntervalNs : 0.0;
        double speed = targetFps > 0.0 ? realFps * 100.0 / targetFps : 0.0;
        return new Snapshot(size, totalFrames, realFps, speed,
                nsToMs(percentile(work, 0.50)), nsToMs(percentile(work, 0.95)),
                nsToMs(percentile(work, 0.99)),
                nsToMs(percentile(intervals, 0.50)), nsToMs(percentile(intervals, 0.95)),
                nsToMs(percentile(intervals, 0.99)), elapsed / 1_000_000L);
    }

    private long[] compact(long[] source, boolean ignoreZero) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            int index = (cursor - size + i + CAPACITY) % CAPACITY;
            long value = source[index];
            if (!ignoreZero || value > 0L) count++;
        }
        long[] out = new long[Math.max(1, count)];
        int pos = 0;
        for (int i = 0; i < size; i++) {
            int index = (cursor - size + i + CAPACITY) % CAPACITY;
            long value = source[index];
            if (ignoreZero && value <= 0L) continue;
            if (pos < out.length) out[pos++] = value;
        }
        return out;
    }

    private static long percentile(long[] sorted, double p) {
        if (sorted == null || sorted.length == 0) return 0L;
        int index = (int)Math.ceil(p * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static double nsToMs(long value) { return value / 1_000_000.0; }

    public static final class Snapshot {
        public final int windowSamples;
        public final long totalFrames;
        public final double realFps;
        public final double speedPercent;
        public final double p50WorkMs;
        public final double p95WorkMs;
        public final double p99WorkMs;
        public final double p50IntervalMs;
        public final double p95IntervalMs;
        public final double p99IntervalMs;
        public final long elapsedMs;

        Snapshot(int windowSamples, long totalFrames, double realFps, double speedPercent,
                 double p50WorkMs, double p95WorkMs, double p99WorkMs,
                 double p50IntervalMs, double p95IntervalMs, double p99IntervalMs,
                 long elapsedMs) {
            this.windowSamples = windowSamples;
            this.totalFrames = totalFrames;
            this.realFps = realFps;
            this.speedPercent = speedPercent;
            this.p50WorkMs = p50WorkMs;
            this.p95WorkMs = p95WorkMs;
            this.p99WorkMs = p99WorkMs;
            this.p50IntervalMs = p50IntervalMs;
            this.p95IntervalMs = p95IntervalMs;
            this.p99IntervalMs = p99IntervalMs;
            this.elapsedMs = elapsedMs;
        }

        static Snapshot empty(long targetIntervalNs) {
            return new Snapshot(0, 0L, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L);
        }

        public String compactLabel() {
            return String.format(Locale.US,
                    "%.1f FPS · %.0f%% · p95 %.2f ms · p99 %.2f ms",
                    realFps, speedPercent, p95IntervalMs, p99IntervalMs);
        }
    }
}
