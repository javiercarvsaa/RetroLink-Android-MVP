package cl.retrolink.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import cl.retrolink.app.net.FrameStreamServer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * PS1 software core + RetroSR Fusion temporal GPU presentation.
 *
 * The PlayStation core remains PCSX-ReARMed with NEON 2X internal resolution.
 * The final framebuffer is stabilized by a motion-aware temporal pass at native
 * resolution and then reconstructed by the existing single-pass spatial upscaler.
 */
public final class Ps1GpuCoreView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private static final String TAG = "RetroLinkPs1Fusion";

    private static final String VERTEX_SHADER =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "out vec2 vUv;\n" +
            "void main() {\n" +
            "  vec2 p;\n" +
            "  if (gl_VertexID == 0) p = vec2(-1.0, -1.0);\n" +
            "  else if (gl_VertexID == 1) p = vec2(3.0, -1.0);\n" +
            "  else p = vec2(-1.0, 3.0);\n" +
            "  vUv = p * 0.5 + 0.5;\n" +
            "  gl_Position = vec4(p, 0.0, 1.0);\n" +
            "}\n";

    /*
     * RetroSR GPU 1-pass reconstruction.
     * Algorithm structure is derived from Qualcomm Snapdragon GSR v1
     * (BSD-3-Clause) but uses ordinary GLES 3 texture samples instead of
     * textureGather, improving compatibility across Android GLES 3 drivers.
     */
    private static final String RETROSR_GSR_FRAGMENT =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform vec4 uViewportInfo;\n" +
            "in vec2 vUv;\n" +
            "out vec4 outColor;\n" +
            "const float EDGE_THRESHOLD = 8.0 / 255.0;\n" +
            "const float EDGE_SHARPNESS = 1.75;\n" +
            "float fastLanczos2(float x) {\n" +
            "  float wA = x - 4.0;\n" +
            "  float wB = x * wA - wA;\n" +
            "  wA *= wA;\n" +
            "  return wB * wA;\n" +
            "}\n" +
            "vec2 weightY(float dx, float dy, float c, float stdv) {\n" +
            "  float x = (dx * dx + dy * dy) * 0.55 + clamp(abs(c) * stdv, 0.0, 1.0);\n" +
            "  float w = fastLanczos2(x);\n" +
            "  return vec2(w, w * c);\n" +
            "}\n" +
            "vec3 readRgb(vec2 uv) {\n" +
            "  vec4 raw = textureLod(uTexture, clamp(uv, vec2(0.0), vec2(1.0)), 0.0);\n" +
            "  return raw.bgr;\n" +
            "}\n" +
            "float lum(vec3 c) {\n" +
            "  return dot(c, vec3(0.2126, 0.7152, 0.0722));\n" +
            "}\n" +
            "vec3 tap(vec2 pixel, vec2 texel) {\n" +
            "  return readRgb((pixel + vec2(0.5)) * texel);\n" +
            "}\n" +
            "void main() {\n" +
            "  vec2 uv = vec2(vUv.x, 1.0 - vUv.y);\n" +
            "  vec2 texel = uViewportInfo.xy;\n" +
            "  vec2 sizePx = uViewportInfo.zw;\n" +
            "  vec3 baseColor = readRgb(uv);\n" +
            "  vec2 src = uv * sizePx - vec2(0.5);\n" +
            "  vec2 base = floor(src);\n" +
            "  vec2 f = fract(src);\n" +
            "  vec3 c00 = tap(base + vec2(0.0, 0.0), texel);\n" +
            "  vec3 c10 = tap(base + vec2(1.0, 0.0), texel);\n" +
            "  vec3 c01 = tap(base + vec2(0.0, 1.0), texel);\n" +
            "  vec3 c11 = tap(base + vec2(1.0, 1.0), texel);\n" +
            "  float l00 = lum(c00);\n" +
            "  float l10 = lum(c10);\n" +
            "  float l01 = lum(c01);\n" +
            "  float l11 = lum(c11);\n" +
            "  float edgeVote = abs(l00 - l10) + abs(l00 - l01) + abs(l11 - l10) + abs(l11 - l01);\n" +
            "  if (edgeVote > EDGE_THRESHOLD) {\n" +
            "    vec3 s0 = tap(base + vec2(-1.0, -1.0), texel);\n" +
            "    vec3 s1 = tap(base + vec2( 0.0, -1.0), texel);\n" +
            "    vec3 s2 = tap(base + vec2( 1.0, -1.0), texel);\n" +
            "    vec3 s3 = tap(base + vec2( 2.0, -1.0), texel);\n" +
            "    vec3 s4 = tap(base + vec2(-1.0,  0.0), texel);\n" +
            "    vec3 s5 = c00;\n" +
            "    vec3 s6 = c10;\n" +
            "    vec3 s7 = tap(base + vec2( 2.0,  0.0), texel);\n" +
            "    vec3 s8 = tap(base + vec2(-1.0,  1.0), texel);\n" +
            "    vec3 s9 = c01;\n" +
            "    vec3 s10 = c11;\n" +
            "    vec3 s11 = tap(base + vec2(2.0, 1.0), texel);\n" +
            "    float y0 = lum(s0); float y1 = lum(s1); float y2 = lum(s2); float y3 = lum(s3);\n" +
            "    float y4 = lum(s4); float y5 = l00; float y6 = l10; float y7 = lum(s7);\n" +
            "    float y8 = lum(s8); float y9 = l01; float y10 = l11; float y11 = lum(s11);\n" +
            "    float meanY = (y5 + y6 + y9 + y10) * 0.25;\n" +
            "    y0 -= meanY; y1 -= meanY; y2 -= meanY; y3 -= meanY;\n" +
            "    y4 -= meanY; y5 -= meanY; y6 -= meanY; y7 -= meanY;\n" +
            "    y8 -= meanY; y9 -= meanY; y10 -= meanY; y11 -= meanY;\n" +
            "    float sumAbs = abs(y0)+abs(y1)+abs(y2)+abs(y3)+abs(y4)+abs(y5)+\n" +
            "                   abs(y6)+abs(y7)+abs(y8)+abs(y9)+abs(y10)+abs(y11);\n" +
            "    float stdv = 2.181818 / max(sumAbs, 0.0001);\n" +
            "    vec2 wy = weightY(f.x + 1.0, f.y + 1.0, y0, stdv);\n" +
            "    wy += weightY(f.x,       f.y + 1.0, y1, stdv);\n" +
            "    wy += weightY(f.x - 1.0, f.y + 1.0, y2, stdv);\n" +
            "    wy += weightY(f.x - 2.0, f.y + 1.0, y3, stdv);\n" +
            "    wy += weightY(f.x + 1.0, f.y,       y4, stdv);\n" +
            "    wy += weightY(f.x,       f.y,       y5, stdv);\n" +
            "    wy += weightY(f.x - 1.0, f.y,       y6, stdv);\n" +
            "    wy += weightY(f.x - 2.0, f.y,       y7, stdv);\n" +
            "    wy += weightY(f.x + 1.0, f.y - 1.0, y8, stdv);\n" +
            "    wy += weightY(f.x,       f.y - 1.0, y9, stdv);\n" +
            "    wy += weightY(f.x - 1.0, f.y - 1.0, y10, stdv);\n" +
            "    wy += weightY(f.x - 2.0, f.y - 1.0, y11, stdv);\n" +
            "    float denom = abs(wy.x) < 0.0001 ? (wy.x < 0.0 ? -0.0001 : 0.0001) : wy.x;\n" +
            "    float reconstructed = wy.y / denom;\n" +
            "    float minY = min(min(l00, l10), min(l01, l11)) - meanY;\n" +
            "    float maxY = max(max(l00, l10), max(l01, l11)) - meanY;\n" +
            "    reconstructed = clamp(EDGE_SHARPNESS * reconstructed, minY, maxY);\n" +
            "    float deltaY = reconstructed - (lum(baseColor) - meanY);\n" +
            "    deltaY = clamp(deltaY, -23.0 / 255.0, 23.0 / 255.0);\n" +
            "    baseColor = clamp(baseColor + vec3(deltaY), vec3(0.0), vec3(1.0));\n" +
            "  }\n" +
            "  outColor = vec4(baseColor, 1.0);\n" +
            "}\n";


    /*
     * RetroSR Fusion Temporal.
     *
     * Operates at the native PCSX-ReARMed framebuffer resolution before the
     * existing spatial upscaler. A cross-shaped motion search aligns nearby
     * history samples, neighborhood clipping prevents trails, and a
     * confidence-weighted blend stabilizes sub-pixel detail and PS1 dithering.
     */
    private static final String RETROSR_TEMPORAL_FRAGMENT =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "uniform sampler2D uCurrent;\n" +
            "uniform sampler2D uHistory;\n" +
            "uniform vec2 uTexel;\n" +
            "uniform float uHistoryWeight;\n" +
            "uniform float uHistoryValid;\n" +
            "uniform float uMotionLevel;\n" +
            "in vec2 vUv;\n" +
            "out vec4 outColor;\n" +
            "vec3 readCurrent(vec2 uv) {\n" +
            "  return texture(uCurrent, clamp(uv, vec2(0.0), vec2(1.0))).bgr;\n" +
            "}\n" +
            "vec3 readHistory(vec2 uv) {\n" +
            "  return texture(uHistory, clamp(uv, vec2(0.0), vec2(1.0))).bgr;\n" +
            "}\n" +
            "float colorDelta(vec3 a, vec3 b) {\n" +
            "  vec3 d = abs(a - b);\n" +
            "  return max(max(d.r, d.g), d.b) * 0.65 + dot(d, vec3(0.2126, 0.7152, 0.0722)) * 0.35;\n" +
            "}\n" +
            "void main() {\n" +
            "  vec2 uv = vUv;\n" +
            "  vec2 tx = vec2(uTexel.x, 0.0);\n" +
            "  vec2 ty = vec2(0.0, uTexel.y);\n" +
            "  vec3 c = readCurrent(uv);\n" +
            "  vec3 n = readCurrent(uv - ty);\n" +
            "  vec3 s = readCurrent(uv + ty);\n" +
            "  vec3 e = readCurrent(uv + tx);\n" +
            "  vec3 w = readCurrent(uv - tx);\n" +
            "  vec3 lo = min(c, min(min(n, s), min(e, w)));\n" +
            "  vec3 hi = max(c, max(max(n, s), max(e, w)));\n" +
            "  vec3 h0 = readHistory(uv);\n" +
            "  vec3 h1 = readHistory(uv + tx);\n" +
            "  vec3 h2 = readHistory(uv - tx);\n" +
            "  vec3 h3 = readHistory(uv + ty);\n" +
            "  vec3 h4 = readHistory(uv - ty);\n" +
            "  vec3 best = h0;\n" +
            "  float bestD = colorDelta(c, h0);\n" +
            "  float d1 = colorDelta(c, h1); if (d1 < bestD) { bestD = d1; best = h1; }\n" +
            "  float d2 = colorDelta(c, h2); if (d2 < bestD) { bestD = d2; best = h2; }\n" +
            "  float d3 = colorDelta(c, h3); if (d3 < bestD) { bestD = d3; best = h3; }\n" +
            "  float d4 = colorDelta(c, h4); if (d4 < bestD) { bestD = d4; best = h4; }\n" +
            "  vec3 span = hi - lo;\n" +
            "  float localRange = max(max(span.r, span.g), span.b);\n" +
            "  vec3 guard = vec3(0.010 + localRange * 0.12);\n" +
            "  best = clamp(best, lo - guard, hi + guard);\n" +
            "  float clippedD = colorDelta(c, best);\n" +
            "  float lowGate = 0.022 + localRange * 0.16;\n" +
            "  float highGate = 0.125 + localRange * 0.42;\n" +
            "  float motionConfidence = 1.0 - smoothstep(lowGate, highGate, clippedD);\n" +
            "  float matchConfidence = 1.0 - smoothstep(0.020, 0.115, bestD);\n" +
            "  float temporalWeight = uHistoryValid * uHistoryWeight * motionConfidence *\n" +
            "                         mix(0.58, 1.0, matchConfidence);\n" +
            "  vec3 fused = mix(c, best, clamp(temporalWeight, 0.0, 0.78));\n" +
            "  float flat = 1.0 - smoothstep(0.035, 0.145, localRange);\n" +
            "  vec3 crossAverage = (n + s + e + w) * 0.25;\n" +
            "  float ditherBlend = 0.018 * flat * motionConfidence * (1.0 - uMotionLevel);\n" +
            "  fused = mix(fused, crossAverage, ditherBlend);\n" +
            "  outColor = vec4(clamp(fused, vec3(0.0), vec3(1.0)).bgr, 1.0);\n" +
            "}\n";

    private static final String FALLBACK_FRAGMENT =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform vec4 uViewportInfo;\n" +
            "in vec2 vUv;\n" +
            "out vec4 outColor;\n" +
            "vec3 readRgb(vec2 uv) { vec4 raw = texture(uTexture, clamp(uv, vec2(0.0), vec2(1.0))); return raw.bgr; }\n" +
            "void main() {\n" +
            "  vec2 uv = vec2(vUv.x, 1.0 - vUv.y);\n" +
            "  vec2 t = uViewportInfo.xy;\n" +
            "  vec3 c = readRgb(uv);\n" +
            "  vec3 n = readRgb(uv + vec2(0.0, -t.y));\n" +
            "  vec3 s = readRgb(uv + vec2(0.0,  t.y));\n" +
            "  vec3 e = readRgb(uv + vec2( t.x, 0.0));\n" +
            "  vec3 w = readRgb(uv + vec2(-t.x, 0.0));\n" +
            "  vec3 lo = min(c, min(min(n, s), min(e, w)));\n" +
            "  vec3 hi = max(c, max(max(n, s), max(e, w)));\n" +
            "  vec3 blur = (n + s + e + w) * 0.25;\n" +
            "  vec3 sharpened = c + (c - blur) * 0.40;\n" +
            "  outColor = vec4(clamp(sharpened, lo, hi), 1.0);\n" +
            "}\n";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean configured = new AtomicBoolean(false);
    private final Object frameLock = new Object();

    private String corePath;
    private String romPath;
    private String systemDir;
    private String saveDir;
    private EmulatorSurfaceView.Listener listener;

    private volatile boolean glReady;
    private volatile boolean paused;
    private volatile boolean ready;
    private volatile boolean resetRequested;
    private volatile boolean ps1DefaultsEnabled;
    private volatile AdaptiveOptimizationEngine.Plan adaptivePlan;
    private AdaptiveRuntimeSession adaptiveSession;
    private volatile boolean temporalAllowedByPlan = true;
    private Thread emulationThread;
    private CountDownLatch stopped = new CountDownLatch(1);

    private int[] capturePixels;
    private int[] latestPixels;
    private int latestWidth;
    private int latestHeight;
    private long latestSerial;
    private long uploadedSerial;

    private final short[] audioBuffer = new short[8192];
    private final NativeAudioSink audio = new NativeAudioSink();

    private int program;
    private int temporalProgram;
    private int texture;
    private final int[] historyTextures = new int[2];
    private final int[] historyFramebuffers = new int[2];
    private int historyReadIndex;
    private int historyWriteIndex = 1;
    private int historyWidth;
    private int historyHeight;
    private boolean historyValid;
    private int vao;
    private int textureWidth;
    private int textureHeight;
    private int surfaceWidth;
    private int surfaceHeight;
    private int uTexture;
    private int uViewportInfo;
    private int tCurrent;
    private int tHistory;
    private int tTexel;
    private int tHistoryWeight;
    private int tHistoryValid;
    private int tMotionLevel;
    private IntBuffer uploadBuffer;
    private volatile boolean sceneCutPending = true;
    private volatile float frameMotion = 1.0f;
    private volatile boolean temporalEnabled = true;
    private int slowIntervals;
    private int stableIntervals;
    private String spatialLabel = "RETROSR GPU";

    public Ps1GpuCoreView(Context context) {
        this(context, null);
    }

    public Ps1GpuCoreView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        setKeepScreenOn(true);
    }

    public synchronized void setAdaptivePlan(AdaptiveOptimizationEngine.Plan plan) {
        this.adaptivePlan = plan;
        this.temporalAllowedByPlan = plan == null || plan.ps1Temporal;
    }

    public synchronized void configure(CoreRegistry.Core core, String corePath, String romPath,
                                       String systemDir, String saveDir,
                                       EmulatorSurfaceView.Listener listener) {
        if (core != CoreRegistry.PS1)
            throw new IllegalArgumentException("Ps1GpuCoreView solo admite PS1");
        if (adaptivePlan == null) {
            adaptivePlan = AdaptiveOptimizationEngine.resolve(
                    getContext(), CoreRegistry.PS1, romPath, false);
            temporalAllowedByPlan = adaptivePlan.ps1Temporal;
        }
        this.corePath = corePath;
        this.romPath = romPath;
        this.systemDir = systemDir;
        this.saveDir = saveDir;
        this.listener = listener;
        configured.set(true);
        maybeStart();
    }

    public synchronized void configurePs1Defaults() {
        ps1DefaultsEnabled = true;
    }

    public boolean isCoreReady() {
        return ready;
    }

    public void resetCore() {
        if (ready) {
            resetRequested = true;
            sceneCutPending = true;
        }
    }

    public void onResumeCore() {
        try { super.onResume(); } catch (Throwable ignored) {}
        paused = false;
        sceneCutPending = true;
        maybeStart();
        requestRender();
    }

    public void onPauseCore() {
        paused = true;
        audio.pause();
        try { super.onPause(); } catch (Throwable ignored) {}
    }

    public boolean shutdownCoreAndWait(long timeoutMs) {
        running.set(false);
        paused = false;
        Thread t = emulationThread;
        if (t != null) t.interrupt();
        try {
            return stopped.await(Math.max(100L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        destroyGlResources();
        GLES30.glClearColor(0f, 0f, 0f, 1f);

        program = createProgram(VERTEX_SHADER, RETROSR_GSR_FRAGMENT);
        if (program != 0) {
            spatialLabel = "GSR 1-PASS";
        } else {
            program = createProgram(VERTEX_SHADER, FALLBACK_FRAGMENT);
            spatialLabel = "ADAPTIVE SPATIAL";
        }

        if (program == 0) {
            postError("No se pudo inicializar el escalador GPU");
            glReady = false;
            return;
        }

        temporalProgram = createProgram(VERTEX_SHADER, RETROSR_TEMPORAL_FRAGMENT);
        temporalEnabled = temporalProgram != 0 && temporalAllowedByPlan;

        uTexture = GLES30.glGetUniformLocation(program, "uTexture");
        uViewportInfo = GLES30.glGetUniformLocation(program, "uViewportInfo");

        if (temporalProgram != 0) {
            tCurrent = GLES30.glGetUniformLocation(temporalProgram, "uCurrent");
            tHistory = GLES30.glGetUniformLocation(temporalProgram, "uHistory");
            tTexel = GLES30.glGetUniformLocation(temporalProgram, "uTexel");
            tHistoryWeight = GLES30.glGetUniformLocation(temporalProgram, "uHistoryWeight");
            tHistoryValid = GLES30.glGetUniformLocation(temporalProgram, "uHistoryValid");
            tMotionLevel = GLES30.glGetUniformLocation(temporalProgram, "uMotionLevel");
        }

        int[] vaos = new int[1];
        GLES30.glGenVertexArrays(1, vaos, 0);
        vao = vaos[0];

        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        texture = textures[0];
        configureTexture(texture);

        textureWidth = textureHeight = 0;
        uploadedSerial = 0L;
        historyValid = false;
        sceneCutPending = true;
        slowIntervals = stableIntervals = 0;
        glReady = true;
        maybeStart();
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        requestRender();
    }

    @Override public void onDrawFrame(GL10 gl) {
        if (!glReady || program == 0 || texture == 0) {
            clearDefaultFramebuffer();
            return;
        }

        int w;
        int h;
        long serial;
        boolean hasNewFrame;
        synchronized (frameLock) {
            w = latestWidth;
            h = latestHeight;
            serial = latestSerial;
            if (latestPixels == null || w <= 0 || h <= 0) {
                clearDefaultFramebuffer();
                return;
            }

            hasNewFrame = serial != uploadedSerial;
            if (hasNewFrame) {
                int count = w * h;
                if (uploadBuffer == null || uploadBuffer.capacity() < count) {
                    uploadBuffer = ByteBuffer.allocateDirect(count * 4)
                            .order(ByteOrder.nativeOrder())
                            .asIntBuffer();
                }
                uploadBuffer.clear();
                uploadBuffer.put(latestPixels, 0, count);
                uploadBuffer.position(0);
            }
        }

        if (hasNewFrame) {
            uploadCurrentTexture(w, h);
            uploadedSerial = serial;

            if (temporalProgram != 0 && temporalEnabled && ensureHistoryResources(w, h)) {
                runTemporalPass(w, h);
            } else {
                historyValid = false;
            }
        }

        int displayTexture = historyValid && temporalEnabled
                ? historyTextures[historyReadIndex]
                : texture;
        presentTexture(displayTexture, w, h);
    }

    private void clearDefaultFramebuffer() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, Math.max(1, surfaceWidth), Math.max(1, surfaceHeight));
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
    }

    private void uploadCurrentTexture(int w, int h) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4);
        if (textureWidth != w || textureHeight != h) {
            GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
                    w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
                    uploadBuffer);
            textureWidth = w;
            textureHeight = h;
            sceneCutPending = true;
            historyValid = false;
        } else {
            GLES30.glTexSubImage2D(
                    GLES30.GL_TEXTURE_2D, 0, 0, 0,
                    w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
                    uploadBuffer);
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    private boolean ensureHistoryResources(int w, int h) {
        if (historyWidth == w && historyHeight == h
                && historyTextures[0] != 0 && historyTextures[1] != 0
                && historyFramebuffers[0] != 0 && historyFramebuffers[1] != 0) {
            return true;
        }

        destroyHistoryResources();

        GLES30.glGenTextures(2, historyTextures, 0);
        GLES30.glGenFramebuffers(2, historyFramebuffers, 0);
        for (int i = 0; i < 2; i++) {
            configureTexture(historyTextures[i]);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, historyTextures[i]);
            GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
                    w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, historyFramebuffers[i]);
            GLES30.glFramebufferTexture2D(
                    GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D, historyTextures[i], 0);
            if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
                    != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "Temporal framebuffer incompleto: " + i);
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
                destroyHistoryResources();
                temporalEnabled = false;
                if (temporalProgram != 0) {
                    GLES30.glDeleteProgram(temporalProgram);
                    temporalProgram = 0;
                }
                return false;
            }
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        historyWidth = w;
        historyHeight = h;
        historyReadIndex = 0;
        historyWriteIndex = 1;
        historyValid = false;
        sceneCutPending = true;
        return true;
    }

    private void runTemporalPass(int w, int h) {
        boolean canUseHistory = historyValid && !sceneCutPending;
        float motion = clamp(frameMotion, 0.0f, 1.0f);
        float weight = clamp(0.74f - motion * 2.15f, 0.18f, 0.74f);
        if (!canUseHistory) weight = 0.0f;

        GLES30.glBindFramebuffer(
                GLES30.GL_FRAMEBUFFER, historyFramebuffers[historyWriteIndex]);
        GLES30.glViewport(0, 0, w, h);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glUseProgram(temporalProgram);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        GLES30.glUniform1i(tCurrent, 0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(
                GLES30.GL_TEXTURE_2D,
                canUseHistory ? historyTextures[historyReadIndex] : texture);
        GLES30.glUniform1i(tHistory, 1);

        GLES30.glUniform2f(tTexel,
                1.0f / Math.max(1, w),
                1.0f / Math.max(1, h));
        GLES30.glUniform1f(tHistoryWeight, weight);
        GLES30.glUniform1f(tHistoryValid, canUseHistory ? 1.0f : 0.0f);
        GLES30.glUniform1f(tMotionLevel, motion);

        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        GLES30.glBindVertexArray(0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glUseProgram(0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

        int oldRead = historyReadIndex;
        historyReadIndex = historyWriteIndex;
        historyWriteIndex = oldRead;
        historyValid = true;
        sceneCutPending = false;
    }

    private void presentTexture(int sourceTexture, int w, int h) {
        clearDefaultFramebuffer();

        float scale = Math.min(surfaceWidth / (float) w, surfaceHeight / (float) h);
        int renderWidth = Math.max(1, Math.round(w * scale));
        int renderHeight = Math.max(1, Math.round(h * scale));
        int left = (surfaceWidth - renderWidth) / 2;
        int bottom = (surfaceHeight - renderHeight) / 2;
        GLES30.glViewport(left, bottom, renderWidth, renderHeight);

        GLES30.glUseProgram(program);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sourceTexture);
        GLES30.glUniform1i(uTexture, 0);
        GLES30.glUniform4f(uViewportInfo,
                1.0f / Math.max(1, w),
                1.0f / Math.max(1, h),
                (float) w,
                (float) h);
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        GLES30.glBindVertexArray(0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glUseProgram(0);
    }

    private void configureTexture(int textureId) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    private synchronized void maybeStart() {
        if (!configured.get() || !glReady || running.get()) return;
        stopped = new CountDownLatch(1);
        running.set(true);
        emulationThread = new Thread(this::runCore, "RetroLinkPs1GpuCore");
        try { emulationThread.setPriority(Thread.NORM_PRIORITY + 1); } catch (Throwable ignored) {}
        emulationThread.start();
    }

    private void applyPs1Options() {
        if (!ps1DefaultsEnabled) return;
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_region", "auto");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_bios", "auto");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_memcard1", "serial");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_memcard2", "shared");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_drc", "enabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_neon_enhancement_enable", adaptivePlan != null && adaptivePlan.ps1EnhancedResolution ? "enabled" : "disabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_neon_enhancement_no_main", "disabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_neon_enhancement_tex_adj_v2", adaptivePlan != null && adaptivePlan.ps1EnhancedResolution ? "enabled" : "disabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_frameskip_type", "disabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_show_bios_bootlogo", "disabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_multitap", "disabled");
    }

    private void runCore() {
        try {
            NativeLibretro.nativeClearFrontendOptions();
            applyPs1Options();
            postStats("Etapa 2/5 · cargando PS1 · " + currentRenderLabel());

            String error = NativeLibretro.nativeInit(corePath, romPath, systemDir, saveDir);
            if (error != null && !error.isEmpty()) {
                postError(error + " · etapa=" + NativeLibretro.nativeGetStage());
                return;
            }

            double fps = NativeLibretro.nativeGetFps();
            if (fps < 20 || fps > 120) fps = 60.0;
            int sampleRate = NativeLibretro.nativeGetSampleRate();
            audio.start(sampleRate);
            ready = true;
            postReady(NativeLibretro.nativeGetCoreInfo(), fps, sampleRate);

            long interval = (long) (1_000_000_000.0 / fps);
            adaptiveSession = new AdaptiveRuntimeSession(getContext(), adaptivePlan,
                    new AdaptiveRuntimeSession.Listener() {
                        @Override public void onAdaptiveStatus(String value) {
                            postStats("PS1 · " + value);
                        }
                        @Override public void onOptionalEffectsAllowed(boolean allowed) {
                            if (!allowed) {
                                temporalAllowedByPlan = false;
                                temporalEnabled = false;
                                sceneCutPending = true;
                            }
                        }
                    });
            adaptiveSession.startForCurrentThread(interval);
            long next = System.nanoTime();
            long statStart = next;
            int statFrames = 0;

            while (running.get()) {
                if (paused || !glReady) {
                    audio.pause();
                    sleepMs(20);
                    next = System.nanoTime();
                    continue;
                }
                audio.resume();

                for (int player = 1; player <= 4; player++) {
                    InputHub.State in = InputHub.get(player);
                    NativeLibretro.nativeSetInput(player, in.mask, in.x, in.y);
                }

                if (resetRequested) {
                    NativeLibretro.nativeReset();
                    resetRequested = false;
                }

                long adaptiveWorkStart = System.nanoTime();
                if (!NativeLibretro.nativeRunFrame()) {
                    postError("El núcleo PS1 dejó de ejecutar frames · etapa="
                            + NativeLibretro.nativeGetStage());
                    break;
                }

                int samples = NativeLibretro.nativeDrainAudio(audioBuffer);
                if (samples > 0) audio.write(audioBuffer, samples);
                captureLatestFrame();
                AdaptiveRuntimeSession session = adaptiveSession;
                if (session != null) session.recordFrame(System.nanoTime() - adaptiveWorkStart);

                statFrames++;
                long now = System.nanoTime();
                if (now - statStart >= 1_000_000_000L) {
                    double real = statFrames * 1_000_000_000.0
                            / Math.max(1L, now - statStart);
                    updateTemporalPerformance(real, fps);
                    postStats(String.format(Locale.US,
                            "PS1 · %.1f/%.2f FPS · %dx%d · %s",
                            real, fps,
                            NativeLibretro.nativeGetVideoWidth(),
                            NativeLibretro.nativeGetVideoHeight(),
                            currentRenderLabel())
                            + (adaptiveSession == null ? "" : " · " + adaptiveSession.compactStatus()));
                    statFrames = 0;
                    statStart = now;
                }

                next += interval;
                long wait = next - System.nanoTime();
                if (wait > 0) sleepNs(wait);
                else if (wait < -interval * 3) next = System.nanoTime();
            }
        } catch (Throwable t) {
            postError(t.getClass().getSimpleName() + ": "
                    + String.valueOf(t.getMessage()) + " · etapa=" + safeStage());
        } finally {
            if (adaptiveSession != null) {
                adaptiveSession.close();
                adaptiveSession = null;
            }
            ready = false;
            audio.stop();
            try { NativeLibretro.nativeShutdown(); } catch (Throwable ignored) {}
            running.set(false);
            stopped.countDown();
        }
    }

    private void captureLatestFrame() {
        int w = NativeLibretro.nativeGetVideoWidth();
        int h = NativeLibretro.nativeGetVideoHeight();
        if (w <= 0 || h <= 0 || w > 2048 || h > 2048) return;

        int count = w * h;
        if (capturePixels == null || capturePixels.length != count)
            capturePixels = new int[count];

        long serial = NativeLibretro.nativeCopyVideoFrame(capturePixels);
        if (serial <= 0 || serial == latestSerial) return;

        synchronized (frameLock) {
            boolean sameShape = latestPixels != null
                    && latestPixels.length == count
                    && latestWidth == w
                    && latestHeight == h;
            float motion = sameShape
                    ? estimateFrameMotion(capturePixels, latestPixels, count)
                    : 1.0f;
            frameMotion = motion;
            if (!sameShape || motion > 0.22f) sceneCutPending = true;

            if (latestPixels == null || latestPixels.length != count)
                latestPixels = new int[count];
            System.arraycopy(capturePixels, 0, latestPixels, 0, count);
            latestWidth = w;
            latestHeight = h;
            latestSerial = serial;
        }

        if (FrameStreamServer.hasRemoteClients()) {
            try {
                Bitmap remote = Bitmap.createBitmap(
                        capturePixels, w, h, Bitmap.Config.ARGB_8888);
                if (remote != null) EmulatorFrameHub.publishOwned(remote);
            } catch (Throwable ignored) {}
        }

        requestRender();
    }

    private static float estimateFrameMotion(int[] current, int[] previous, int count) {
        int step = Math.max(1, count / 1536);
        long difference = 0L;
        int samples = 0;
        for (int i = 0; i < count; i += step) {
            int a = current[i];
            int b = previous[i];
            difference += Math.abs(((a >>> 16) & 0xff) - ((b >>> 16) & 0xff));
            difference += Math.abs(((a >>> 8) & 0xff) - ((b >>> 8) & 0xff));
            difference += Math.abs((a & 0xff) - (b & 0xff));
            samples++;
        }
        if (samples == 0) return 1.0f;
        return clamp(difference / (samples * 765.0f), 0.0f, 1.0f);
    }

    private void updateTemporalPerformance(double realFps, double targetFps) {
        if (temporalProgram == 0 || targetFps <= 0.0) return;

        if (realFps < targetFps * 0.88) {
            slowIntervals++;
            stableIntervals = 0;
        } else if (realFps > targetFps * 0.965) {
            stableIntervals++;
            slowIntervals = Math.max(0, slowIntervals - 1);
        } else {
            slowIntervals = Math.max(0, slowIntervals - 1);
            stableIntervals = Math.max(0, stableIntervals - 1);
        }

        if (temporalEnabled && slowIntervals >= 3) {
            temporalEnabled = false;
            sceneCutPending = true;
            slowIntervals = 0;
            stableIntervals = 0;
        } else if (!temporalEnabled && temporalAllowedByPlan && stableIntervals >= 8) {
            temporalEnabled = true;
            sceneCutPending = true;
            slowIntervals = 0;
            stableIntervals = 0;
        }
    }

    private String currentRenderLabel() {
        if (temporalProgram != 0 && temporalEnabled)
            return "RETROSR FUSION · TEMPORAL + " + spatialLabel;
        if (temporalProgram != 0)
            return "RETROSR FUSION · AUTO SPATIAL + " + spatialLabel;
        return "RETROSR GPU · " + spatialLabel;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource);
        if (vertex == 0) return 0;
        int fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragment == 0) {
            GLES30.glDeleteShader(vertex);
            return 0;
        }

        int result = GLES30.glCreateProgram();
        GLES30.glAttachShader(result, vertex);
        GLES30.glAttachShader(result, fragment);
        GLES30.glLinkProgram(result);

        int[] linked = new int[1];
        GLES30.glGetProgramiv(result, GLES30.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            Log.e(TAG, "Program link failed: " + GLES30.glGetProgramInfoLog(result));
            GLES30.glDeleteProgram(result);
            result = 0;
        }

        GLES30.glDeleteShader(vertex);
        GLES30.glDeleteShader(fragment);
        return result;
    }

    private int compileShader(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile failed: " + GLES30.glGetShaderInfoLog(shader));
            GLES30.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private void destroyHistoryResources() {
        if (historyFramebuffers[0] != 0 || historyFramebuffers[1] != 0) {
            GLES30.glDeleteFramebuffers(2, historyFramebuffers, 0);
            historyFramebuffers[0] = historyFramebuffers[1] = 0;
        }
        if (historyTextures[0] != 0 || historyTextures[1] != 0) {
            GLES30.glDeleteTextures(2, historyTextures, 0);
            historyTextures[0] = historyTextures[1] = 0;
        }
        historyWidth = historyHeight = 0;
        historyReadIndex = 0;
        historyWriteIndex = 1;
        historyValid = false;
    }

    private void destroyGlResources() {
        destroyHistoryResources();
        if (texture != 0) {
            int[] textures = {texture};
            GLES30.glDeleteTextures(1, textures, 0);
            texture = 0;
        }
        if (vao != 0) {
            int[] vaos = {vao};
            GLES30.glDeleteVertexArrays(1, vaos, 0);
            vao = 0;
        }
        if (temporalProgram != 0) {
            GLES30.glDeleteProgram(temporalProgram);
            temporalProgram = 0;
        }
        if (program != 0) {
            GLES30.glDeleteProgram(program);
            program = 0;
        }
    }

    private String safeStage() {
        try { return NativeLibretro.nativeGetStage(); }
        catch (Throwable ignored) { return "unknown"; }
    }

    private void postReady(String coreInfo, double fps, int sampleRate) {
        EmulatorSurfaceView.Listener target = listener;
        if (target != null) post(() -> target.onCoreReady(coreInfo, fps, sampleRate));
    }

    private void postError(String error) {
        EmulatorSurfaceView.Listener target = listener;
        if (target != null) post(() -> target.onCoreError(error));
    }

    private void postStats(String stats) {
        EmulatorSurfaceView.Listener target = listener;
        if (target != null) post(() -> target.onStats(stats));
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static void sleepNs(long ns) {
        if (ns <= 0) return;
        long ms = ns / 1_000_000L;
        int extra = (int) (ns % 1_000_000L);
        try { Thread.sleep(ms, extra); } catch (InterruptedException ignored) {}
    }
}
