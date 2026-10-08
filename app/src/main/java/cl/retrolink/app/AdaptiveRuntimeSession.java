package cl.retrolink.app;

import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.PerformanceHintManager;
import android.os.PowerManager;
import android.os.Process;

import java.util.concurrent.Executor;

/**
 * Safe runtime observer: metrics, ADPF hints when available and thermal fallback.
 * It never changes emulation speed, audio timing, controls, networking or save paths.
 */
public final class AdaptiveRuntimeSession implements AutoCloseable {
    public interface Listener {
        void onAdaptiveStatus(String status);
        void onOptionalEffectsAllowed(boolean allowed);
    }

    private static final long CALIBRATION_MIN_NS = 12_000_000_000L;
    private static final int CALIBRATION_MIN_FRAMES = 240;
    private static final long SUSTAINED_CHECK_NS = 60_000_000_000L;

    private final Context context;
    private final AdaptiveOptimizationEngine.Plan plan;
    private final Listener listener;
    private final FrameTimeMetrics metrics = new FrameTimeMetrics();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ThermalBridge thermalBridge;
    private HintBridge hintBridge;
    private long targetIntervalNs = 16_666_667L;
    private int thermalStatus;
    private boolean calibrated;
    private long nextSustainedCheckNs = SUSTAINED_CHECK_NS;
    private int consecutiveBadWindows;
    private int consecutiveGoodWindows;
    private boolean optionalEffectsAllowed = true;
    private volatile boolean closed;

    public AdaptiveRuntimeSession(Context context,
                                  AdaptiveOptimizationEngine.Plan plan,
                                  Listener listener) {
        this.context = context.getApplicationContext();
        this.plan = plan;
        this.listener = listener;
        this.thermalStatus = plan == null ? 0 : plan.capabilities.thermalStatus;
    }

    /** Must be called on the workload thread so ADPF receives the correct tid. */
    public void startForCurrentThread(long targetIntervalNs) {
        if (closed) return;
        this.targetIntervalNs = Math.max(1_000_000L, targetIntervalNs);
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                thermalBridge = new Api29ThermalBridge(context, this::handleThermalStatus);
            } catch (Throwable ignored) { thermalBridge = null; }
        }
        if (Build.VERSION.SDK_INT >= 31 && plan != null && plan.runtimeHintsEnabled) {
            try {
                hintBridge = new Api31HintBridge(context, Process.myTid(), this.targetIntervalNs,
                        plan.mode == OptimizationProfileStore.MODE_BATTERY);
            } catch (Throwable ignored) { hintBridge = null; }
        }
        publish("Calibración pasiva iniciada · " + (plan == null ? "SAFE" : plan.shortLabel()));
    }

    public void recordFrame(long actualWorkNs) {
        if (closed) return;
        metrics.recordFrame(actualWorkNs);
        HintBridge hint = hintBridge;
        if (hint != null) hint.report(actualWorkNs);
        if (!calibrated && plan != null && plan.calibrationEnabled
                && metrics.elapsedNs() >= CALIBRATION_MIN_NS
                && metrics.sampleCount() >= CALIBRATION_MIN_FRAMES) {
            calibrateOnce();
        }
        if (plan != null && metrics.elapsedNs() >= nextSustainedCheckNs
                && metrics.sampleCount() >= CALIBRATION_MIN_FRAMES) {
            evaluateSustainedWindow();
            nextSustainedCheckNs += SUSTAINED_CHECK_NS;
        }
    }

    public FrameTimeMetrics.Snapshot snapshot() {
        return metrics.snapshot(targetIntervalNs);
    }

    public String compactStatus() {
        FrameTimeMetrics.Snapshot snapshot = snapshot();
        if (snapshot.windowSamples == 0) return plan == null ? "SAFE" : plan.shortLabel();
        return (plan == null ? "SAFE" : plan.shortLabel()) + " · " + snapshot.compactLabel();
    }

    public int thermalStatus() { return thermalStatus; }

    private void calibrateOnce() {
        calibrated = true;
        FrameTimeMetrics.Snapshot snapshot = snapshot();
        int level = plan.level;
        boolean insufficient = snapshot.speedPercent < 92.0
                || snapshot.p95IntervalMs > targetIntervalNs / 1_000_000.0 * 1.25
                || thermalStatus >= AdaptiveOptimizationEngine.thermalModerate();
        int stable = insufficient ? AdaptiveOptimizationEngine.lowerLevel(level) : level;
        OptimizationProfileStore.setStableLevel(context, plan.gameKey, stable);
        String outcome = insufficient ? "AJUSTE SEGURO SIGUIENTE INICIO" : "ESTABLE";
        OptimizationProfileStore.recordSnapshot(context, plan.gameKey, snapshot,
                thermalStatus, stable, currentPssMb(), outcome);
        publish(outcome + " · " + snapshot.compactLabel());
    }


    private void evaluateSustainedWindow() {
        FrameTimeMetrics.Snapshot snapshot = snapshot();
        double targetMs = targetIntervalNs / 1_000_000.0;
        boolean bad = snapshot.speedPercent < 94.0
                || snapshot.p95IntervalMs > targetMs * 1.20
                || thermalStatus >= AdaptiveOptimizationEngine.thermalModerate();
        if (bad) {
            consecutiveBadWindows++;
            consecutiveGoodWindows = 0;
        } else {
            consecutiveGoodWindows++;
            consecutiveBadWindows = 0;
        }

        int current = OptimizationProfileStore.stableLevel(context, plan.gameKey, plan.level);
        int next = current;
        String outcome = "SOSTENIDO ESTABLE";
        if (consecutiveBadWindows >= 2) {
            next = AdaptiveOptimizationEngine.lowerLevel(current);
            consecutiveBadWindows = 0;
            outcome = next < current ? "SOSTENIDO · BAJAR SIGUIENTE INICIO" : "SOSTENIDO · MÍNIMO SEGURO";
        } else if (consecutiveGoodWindows >= 3
                && plan.mode == OptimizationProfileStore.MODE_AUTO
                && thermalStatus < AdaptiveOptimizationEngine.thermalModerate()) {
            next = Math.min(plan.capabilities.tier, current + 1);
            consecutiveGoodWindows = 0;
            outcome = next > current ? "SOSTENIDO · SUBIR SIGUIENTE INICIO" : "SOSTENIDO ESTABLE";
        }
        OptimizationProfileStore.setStableLevel(context, plan.gameKey, next);
        OptimizationProfileStore.recordSnapshot(context, plan.gameKey, snapshot,
                thermalStatus, next, currentPssMb(), outcome);
        publish(outcome + " · " + snapshot.compactLabel() + " · PSS " + currentPssMb() + " MB");
    }

    private static int currentPssMb() {
        try { return (int) Math.min((long) Integer.MAX_VALUE, Math.max(0L, Debug.getPss() / 1024L)); }
        catch (Throwable ignored) { return 0; }
    }

    private void handleThermalStatus(int status) {
        thermalStatus = status;
        if (plan != null && status >= AdaptiveOptimizationEngine.thermalSevere()) {
            int stable = AdaptiveOptimizationEngine.lowerLevel(plan.level);
            OptimizationProfileStore.setStableLevel(context, plan.gameKey, stable);
            if (optionalEffectsAllowed) {
                optionalEffectsAllowed = false;
                Listener target = listener;
                if (target != null) main.post(() -> target.onOptionalEffectsAllowed(false));
            }
            publish("Protección térmica · efectos opcionales desactivados");
        } else {
            publish("Estado térmico " + status);
        }
    }

    private void publish(String text) {
        Listener target = listener;
        if (target != null) main.post(() -> target.onAdaptiveStatus(text));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        ThermalBridge thermal = thermalBridge;
        thermalBridge = null;
        if (thermal != null) thermal.close();
        HintBridge hint = hintBridge;
        hintBridge = null;
        if (hint != null) hint.close();
    }

    private interface ThermalBridge extends AutoCloseable {
        @Override void close();
    }

    private interface HintBridge extends AutoCloseable {
        void report(long actualNs);
        @Override void close();
    }

    private interface ThermalCallback { void onStatus(int status); }

    private static final class Api29ThermalBridge implements ThermalBridge {
        private final PowerManager manager;
        private final PowerManager.OnThermalStatusChangedListener listener;

        Api29ThermalBridge(Context context, ThermalCallback callback) {
            manager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            if (manager == null) throw new IllegalStateException("PowerManager unavailable");
            Executor executor = command -> new Handler(Looper.getMainLooper()).post(command);
            listener = callback::onStatus;
            manager.addThermalStatusListener(executor, listener);
            callback.onStatus(manager.getCurrentThermalStatus());
        }

        @Override public void close() {
            try { manager.removeThermalStatusListener(listener); } catch (Throwable ignored) {}
        }
    }

    private static final class Api31HintBridge implements HintBridge {
        private final PerformanceHintManager.Session session;

        Api31HintBridge(Context context, int tid, long targetNs, boolean preferEfficiency) {
            PerformanceHintManager manager = context.getSystemService(PerformanceHintManager.class);
            session = manager == null ? null : manager.createHintSession(new int[]{tid}, targetNs);
            if (session != null && Build.VERSION.SDK_INT >= 35 && preferEfficiency) {
                try { session.setPreferPowerEfficiency(true); } catch (Throwable ignored) {}
            }
        }

        @Override public void report(long actualNs) {
            if (session == null) return;
            try { session.reportActualWorkDuration(Math.max(1L, actualNs)); }
            catch (Throwable ignored) {}
        }

        @Override public void close() {
            if (session == null) return;
            try { session.close(); } catch (Throwable ignored) {}
        }
    }
}
