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
 * PS1 software core + GPU presentation.
 *
 * The PlayStation core remains PCSX-ReARMed with NEON 2X internal resolution.
 * The PS1 framebuffer is first stabilized at native enhanced resolution by
 * a motion-compensated temporal pass. The resolved history is then fed into
 * the existing one-pass spatial upscaler at full display resolution. This
 * two-pass order keeps the expensive temporal work at PS1 resolution.
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

    private static final String RETROSR_TEMPORAL_FRAGMENT =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "uniform sampler2D uSourceNow;\n" +
            "uniform sampler2D uSourcePrev;\n" +
            "uniform sampler2D uHistory;\n" +
            "uniform vec2 uTexel;\n" +
            "uniform float uHistoryValid;\n" +
            "uniform int uSearchMode;\n" +
            "in vec2 vUv;\n" +
            "out vec4 outColor;\n" +
            "\n" +
            "float lum(vec3 c) {\n" +
            "  return dot(c, vec3(0.2126, 0.7152, 0.0722));\n" +
            "}\n" +
            "\n" +
            "vec3 sourceNowRgb(vec2 uv) {\n" +
            "  vec4 raw = textureLod(uSourceNow, clamp(uv, vec2(0.0), vec2(1.0)), 0.0);\n" +
            "  return raw.bgr;\n" +
            "}\n" +
            "\n" +
            "vec3 sourcePrevRgb(vec2 uv) {\n" +
            "  vec4 raw = textureLod(uSourcePrev, clamp(uv, vec2(0.0), vec2(1.0)), 0.0);\n" +
            "  return raw.bgr;\n" +
            "}\n" +
            "\n" +
            "vec3 historyRgb(vec2 uv) {\n" +
            "  vec4 raw = textureLod(uHistory, clamp(uv, vec2(0.0), vec2(1.0)), 0.0);\n" +
            "  return raw.bgr;\n" +
            "}\n" +
            "\n" +
            "float motionError(vec3 current, vec2 uv, vec2 offset) {\n" +
            "  vec3 previous = sourcePrevRgb(uv + offset);\n" +
            "  vec3 delta = abs(current - previous);\n" +
            "  return dot(delta, vec3(0.24, 0.62, 0.14));\n" +
            "}\n" +
            "\n" +
            "void testCandidate(vec3 current, vec2 uv, vec2 offset,\n" +
            "                   inout float bestError, inout vec2 bestOffset) {\n" +
            "  float candidate = motionError(current, uv, offset);\n" +
            "  if (candidate < bestError) {\n" +
            "    bestError = candidate;\n" +
            "    bestOffset = offset;\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "void main() {\n" +
            "  vec3 current = sourceNowRgb(vUv);\n" +
            "\n" +
            "  float bestError = 1000.0;\n" +
            "  vec2 bestOffset = vec2(0.0);\n" +
            "  testCandidate(current, vUv, vec2(0.0), bestError, bestOffset);\n" +
            "  testCandidate(current, vUv, vec2( uTexel.x, 0.0), bestError, bestOffset);\n" +
            "  testCandidate(current, vUv, vec2(-uTexel.x, 0.0), bestError, bestOffset);\n" +
            "  testCandidate(current, vUv, vec2(0.0,  uTexel.y), bestError, bestOffset);\n" +
            "  testCandidate(current, vUv, vec2(0.0, -uTexel.y), bestError, bestOffset);\n" +
            "\n" +
            "  if (uSearchMode > 0) {\n" +
            "    testCandidate(current, vUv, vec2( uTexel.x,  uTexel.y), bestError, bestOffset);\n" +
            "    testCandidate(current, vUv, vec2(-uTexel.x,  uTexel.y), bestError, bestOffset);\n" +
            "    testCandidate(current, vUv, vec2( uTexel.x, -uTexel.y), bestError, bestOffset);\n" +
            "    testCandidate(current, vUv, vec2(-uTexel.x, -uTexel.y), bestError, bestOffset);\n" +
            "  }\n" +
            "\n" +
            "  vec2 historyUv = clamp(vUv + bestOffset, uTexel * 0.5, vec2(1.0) - uTexel * 0.5);\n" +
            "  vec3 history = historyRgb(historyUv);\n" +
            "\n" +
            "  vec3 north = sourceNowRgb(vUv + vec2(0.0, -uTexel.y));\n" +
            "  vec3 south = sourceNowRgb(vUv + vec2(0.0,  uTexel.y));\n" +
            "  vec3 east = sourceNowRgb(vUv + vec2( uTexel.x, 0.0));\n" +
            "  vec3 west = sourceNowRgb(vUv + vec2(-uTexel.x, 0.0));\n" +
            "  vec3 northEast = sourceNowRgb(vUv + vec2( uTexel.x, -uTexel.y));\n" +
            "  vec3 northWest = sourceNowRgb(vUv + vec2(-uTexel.x, -uTexel.y));\n" +
            "  vec3 southEast = sourceNowRgb(vUv + vec2( uTexel.x,  uTexel.y));\n" +
            "  vec3 southWest = sourceNowRgb(vUv + vec2(-uTexel.x,  uTexel.y));\n" +
            "\n" +
            "  vec3 neighborhoodMin = min(current, min(min(north, south), min(east, west)));\n" +
            "  neighborhoodMin = min(neighborhoodMin, min(min(northEast, northWest), min(southEast, southWest)));\n" +
            "  vec3 neighborhoodMax = max(current, max(max(north, south), max(east, west)));\n" +
            "  neighborhoodMax = max(neighborhoodMax, max(max(northEast, northWest), max(southEast, southWest)));\n" +
            "\n" +
            "  vec3 localRange = neighborhoodMax - neighborhoodMin;\n" +
            "  vec3 clipMargin = vec3(1.5 / 255.0) + localRange * 0.08;\n" +
            "  vec3 clippedHistory = clamp(history, neighborhoodMin - clipMargin, neighborhoodMax + clipMargin);\n" +
            "\n" +
            "  float historyDifference = dot(abs(current - clippedHistory), vec3(0.24, 0.62, 0.14));\n" +
            "  float motionConfidence = 1.0 - smoothstep(0.020, 0.165, bestError);\n" +
            "  float historyConfidence = 1.0 - smoothstep(0.030, 0.185, historyDifference);\n" +
            "  float edgeStrength = max(abs(lum(east) - lum(west)), abs(lum(north) - lum(south)));\n" +
            "  float edgeTrust = mix(1.0, 0.90, smoothstep(0.08, 0.32, edgeStrength));\n" +
            "\n" +
            "  float historyWeight = 0.74 * motionConfidence * historyConfidence * edgeTrust * uHistoryValid;\n" +
            "  if (bestError > 0.23 || historyDifference > 0.23) {\n" +
            "    historyWeight = 0.0;\n" +
            "  }\n" +
            "\n" +
            "  vec3 resolved = mix(current, clippedHistory, clamp(historyWeight, 0.0, 0.74));\n" +
            "  vec3 localBlur = (north + south + east + west) * 0.25;\n" +
            "  vec3 protectedDetail = current - localBlur;\n" +
            "  resolved += protectedDetail * (0.035 * (1.0 - historyWeight));\n" +
            "  resolved = clamp(resolved, neighborhoodMin - clipMargin, neighborhoodMax + clipMargin);\n" +
            "\n" +
            "  // Store in the same byte-channel layout as the CPU-uploaded PS1 texture.\n" +
            "  // RETROSR_GSR_FRAGMENT performs the matching .bgr read in the second pass.\n" +
            "  outColor = vec4(clamp(resolved, vec3(0.0), vec3(1.0)).bgr, 1.0);\n" +
            "}\n";

    private static final int HIGH_QUALITY_SEARCH_PIXELS = 600_000;
    private static final int SCENE_SAMPLE_COLS = 12;
    private static final int SCENE_SAMPLE_ROWS = 8;
    private static final int SCENE_SAMPLE_COUNT = SCENE_SAMPLE_COLS * SCENE_SAMPLE_ROWS;

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
    private volatile boolean historyResetRequested = true;
    private volatile String shaderLabel = "RETROSR FUSION · INICIANDO";
    private Thread emulationThread;
    private CountDownLatch stopped = new CountDownLatch(1);

    private int[] capturePixels;
    private int[] latestPixels;
    private int latestWidth;
    private int latestHeight;
    private long latestSerial;
    private long uploadedSerial;

    private final int[] previousSceneSamples = new int[SCENE_SAMPLE_COUNT];
    private boolean sceneSamplesValid;
    private int sceneSampleWidth;
    private int sceneSampleHeight;

    private final short[] audioBuffer = new short[8192];
    private final NativeAudioSink audio = new NativeAudioSink();

    private int spatialProgram;
    private int temporalProgram;
    private int vao;

    private final int[] sourceTextures = new int[2];
    private int sourceWidth;
    private int sourceHeight;
    private int currentSourceIndex = -1;
    private int previousSourceIndex = -1;

    private final int[] historyTextures = new int[2];
    private final int[] historyFbos = new int[2];
    private int historyIndex = -1;
    private boolean historyValid;
    private boolean historyTargetsReady;
    private int historyWidth;
    private int historyHeight;

    private int surfaceWidth;
    private int surfaceHeight;
    private IntBuffer uploadBuffer;

    private int spatialTextureUniform;
    private int spatialViewportUniform;

    private int temporalSourceNowUniform;
    private int temporalSourcePrevUniform;
    private int temporalHistoryUniform;
    private int temporalTexelUniform;
    private int temporalHistoryValidUniform;
    private int temporalSearchModeUniform;

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

    public synchronized void configure(CoreRegistry.Core core, String corePath, String romPath,
                                       String systemDir, String saveDir,
                                       EmulatorSurfaceView.Listener listener) {
        if (core != CoreRegistry.PS1)
            throw new IllegalArgumentException("Ps1GpuCoreView solo admite PS1");
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
        if (ready) resetRequested = true;
        historyResetRequested = true;
    }

    public void onResumeCore() {
        try { super.onResume(); } catch (Throwable ignored) {}
        paused = false;
        historyResetRequested = true;
        maybeStart();
        requestRender();
    }

    public void onPauseCore() {
        paused = true;
        historyResetRequested = true;
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
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);

        spatialProgram = createProgram(VERTEX_SHADER, RETROSR_GSR_FRAGMENT);
        boolean spatialPrimary = spatialProgram != 0;
        if (!spatialPrimary) {
            spatialProgram = createProgram(VERTEX_SHADER, FALLBACK_FRAGMENT);
        }
        temporalProgram = createProgram(VERTEX_SHADER, RETROSR_TEMPORAL_FRAGMENT);

        if (spatialProgram == 0) {
            postError("No se pudo inicializar el escalador GPU");
            glReady = false;
            return;
        }

        spatialTextureUniform = GLES30.glGetUniformLocation(spatialProgram, "uTexture");
        spatialViewportUniform = GLES30.glGetUniformLocation(spatialProgram, "uViewportInfo");

        if (temporalProgram != 0) {
            temporalSourceNowUniform = GLES30.glGetUniformLocation(temporalProgram, "uSourceNow");
            temporalSourcePrevUniform = GLES30.glGetUniformLocation(temporalProgram, "uSourcePrev");
            temporalHistoryUniform = GLES30.glGetUniformLocation(temporalProgram, "uHistory");
            temporalTexelUniform = GLES30.glGetUniformLocation(temporalProgram, "uTexel");
            temporalHistoryValidUniform = GLES30.glGetUniformLocation(temporalProgram, "uHistoryValid");
            temporalSearchModeUniform = GLES30.glGetUniformLocation(temporalProgram, "uSearchMode");
        }

        int[] vaos = new int[1];
        GLES30.glGenVertexArrays(1, vaos, 0);
        vao = vaos[0];

        GLES30.glGenTextures(2, sourceTextures, 0);
        for (int textureId : sourceTextures) configureTexture(textureId);

        sourceWidth = sourceHeight = 0;
        currentSourceIndex = previousSourceIndex = -1;
        uploadedSerial = 0L;
        historyValid = false;
        historyIndex = -1;
        historyResetRequested = true;

        if (temporalProgram != 0) {
            shaderLabel = spatialPrimary
                    ? "RETROSR FUSION · TEMPORAL 2-PASS"
                    : "RETROSR FUSION · COMPAT 2-PASS";
        } else {
            shaderLabel = "RETROSR GPU · SPATIAL FALLBACK";
        }

        glReady = true;
        maybeStart();
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
        historyResetRequested = true;
        requestRender();
    }

    @Override public void onDrawFrame(GL10 gl) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, Math.max(1, surfaceWidth), Math.max(1, surfaceHeight));
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        if (!glReady || spatialProgram == 0 || sourceTextures[0] == 0) return;

        int w;
        int h;
        long serial;
        boolean newFrame;
        synchronized (frameLock) {
            w = latestWidth;
            h = latestHeight;
            serial = latestSerial;
            if (latestPixels == null || w <= 0 || h <= 0) return;

            newFrame = serial != uploadedSerial;
            if (newFrame) {
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

        if (newFrame) {
            uploadSourceFrame(uploadBuffer, w, h);
            uploadedSerial = serial;
        }
        if (currentSourceIndex < 0) return;

        boolean resetHistory = historyResetRequested;
        historyResetRequested = false;
        if (resetHistory) {
            historyValid = false;
            historyIndex = -1;
        }

        if (temporalProgram != 0 && historyTargetsReady
                && (newFrame || resetHistory || !historyValid)) {
            int searchMode = ((long) sourceWidth * sourceHeight <= HIGH_QUALITY_SEARCH_PIXELS) ? 1 : 0;
            resolveTemporalSource(searchMode,
                    historyValid && previousSourceIndex >= 0);

            shaderLabel = searchMode > 0
                    ? "RETROSR FUSION · TEMPORAL HQ 2-PASS"
                    : "RETROSR FUSION · TEMPORAL ECO 2-PASS";
        }

        int presentationTexture = temporalProgram != 0 && historyValid
                ? historyTextures[historyIndex]
                : sourceTextures[currentSourceIndex];

        float scale = Math.min(surfaceWidth / (float) w, surfaceHeight / (float) h);
        int renderWidth = Math.max(1, Math.round(w * scale));
        int renderHeight = Math.max(1, Math.round(h * scale));
        int left = (surfaceWidth - renderWidth) / 2;
        int bottom = (surfaceHeight - renderHeight) / 2;

        renderSpatial(presentationTexture, w, h, left, bottom, renderWidth, renderHeight);
    }

    private synchronized void maybeStart() {
        if (!configured.get() || !glReady || running.get()) return;
        stopped = new CountDownLatch(1);
        running.set(true);
        emulationThread = new Thread(this::runCore, "RetroLinkPs1FusionCore");
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
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_neon_enhancement_enable", "enabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_neon_enhancement_no_main", "disabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_neon_enhancement_tex_adj_v2", "enabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_frameskip_type", "disabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_show_bios_bootlogo", "disabled");
        NativeLibretro.nativeSetFrontendOption("pcsx_rearmed_multitap", "disabled");
    }

    private void runCore() {
        try {
            NativeLibretro.nativeClearFrontendOptions();
            applyPs1Options();
            postStats("Etapa 2/5 · cargando PS1 · " + shaderLabel);

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

                if (!NativeLibretro.nativeRunFrame()) {
                    postError("El núcleo PS1 dejó de ejecutar frames · etapa="
                            + NativeLibretro.nativeGetStage());
                    break;
                }

                int samples = NativeLibretro.nativeDrainAudio(audioBuffer);
                if (samples > 0) audio.write(audioBuffer, samples);
                captureLatestFrame();

                statFrames++;
                long now = System.nanoTime();
                if (now - statStart >= 1_000_000_000L) {
                    double real = statFrames * 1_000_000_000.0
                            / Math.max(1L, now - statStart);
                    postStats(String.format(Locale.US,
                            "PS1 · %.1f/%.2f FPS · %dx%d · %s",
                            real, fps,
                            NativeLibretro.nativeGetVideoWidth(),
                            NativeLibretro.nativeGetVideoHeight(),
                            shaderLabel));
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

        if (detectSceneCut(capturePixels, w, h)) {
            historyResetRequested = true;
        }

        synchronized (frameLock) {
            if (latestPixels == null || latestPixels.length != count)
                latestPixels = new int[count];
            System.arraycopy(capturePixels, 0, latestPixels, 0, count);
            latestWidth = w;
            latestHeight = h;
            latestSerial = serial;
        }

        // P2 receives the native enhanced framebuffer to preserve netplay latency.
        // RetroSR Fusion stays on the P1 GPU and never adds a CPU readback.
        if (FrameStreamServer.hasRemoteClients()) {
            try {
                Bitmap remote = Bitmap.createBitmap(
                        capturePixels, w, h, Bitmap.Config.ARGB_8888);
                if (remote != null) EmulatorFrameHub.publishOwned(remote);
            } catch (Throwable ignored) {}
        }

        requestRender();
    }

    private boolean detectSceneCut(int[] pixels, int width, int height) {
        if (!sceneSamplesValid || width != sceneSampleWidth || height != sceneSampleHeight) {
            fillSceneSamples(pixels, width, height);
            sceneSamplesValid = true;
            sceneSampleWidth = width;
            sceneSampleHeight = height;
            return true;
        }

        long differenceSum = 0L;
        int largeChanges = 0;
        int index = 0;
        for (int row = 0; row < SCENE_SAMPLE_ROWS; row++) {
            int y = Math.min(height - 1, ((row * 2 + 1) * height) / (SCENE_SAMPLE_ROWS * 2));
            for (int col = 0; col < SCENE_SAMPLE_COLS; col++) {
                int x = Math.min(width - 1, ((col * 2 + 1) * width) / (SCENE_SAMPLE_COLS * 2));
                int now = pixels[y * width + x];
                int previous = previousSceneSamples[index];

                int dr = Math.abs(((now >>> 16) & 0xff) - ((previous >>> 16) & 0xff));
                int dg = Math.abs(((now >>> 8) & 0xff) - ((previous >>> 8) & 0xff));
                int db = Math.abs((now & 0xff) - (previous & 0xff));
                int weighted = (dr + (dg << 1) + db) >> 2;
                differenceSum += weighted;
                if (weighted > 56) largeChanges++;

                previousSceneSamples[index++] = now;
            }
        }

        double average = differenceSum / (double) SCENE_SAMPLE_COUNT;
        return average > 42.0 && largeChanges > (SCENE_SAMPLE_COUNT * 3 / 5);
    }

    private void fillSceneSamples(int[] pixels, int width, int height) {
        int index = 0;
        for (int row = 0; row < SCENE_SAMPLE_ROWS; row++) {
            int y = Math.min(height - 1, ((row * 2 + 1) * height) / (SCENE_SAMPLE_ROWS * 2));
            for (int col = 0; col < SCENE_SAMPLE_COLS; col++) {
                int x = Math.min(width - 1, ((col * 2 + 1) * width) / (SCENE_SAMPLE_COLS * 2));
                previousSceneSamples[index++] = pixels[y * width + x];
            }
        }
    }

    private void uploadSourceFrame(IntBuffer pixels, int width, int height) {
        boolean resized = width != sourceWidth || height != sourceHeight;
        if (resized) {
            allocateSourceTextures(width, height);
            ensureHistoryTargets(width, height);
            currentSourceIndex = -1;
            previousSourceIndex = -1;
            historyValid = false;
            historyIndex = -1;
            historyResetRequested = true;
        }

        int nextIndex = currentSourceIndex < 0 ? 0 : 1 - currentSourceIndex;
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sourceTextures[nextIndex]);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4);
        pixels.position(0);
        GLES30.glTexSubImage2D(
                GLES30.GL_TEXTURE_2D, 0, 0, 0,
                width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);

        previousSourceIndex = currentSourceIndex;
        currentSourceIndex = nextIndex;
    }

    private void allocateSourceTextures(int width, int height) {
        sourceWidth = width;
        sourceHeight = height;
        for (int textureId : sourceTextures) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
            GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
                    width, height, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    private boolean ensureHistoryTargets(int width, int height) {
        if (historyTargetsReady && historyWidth == width && historyHeight == height)
            return true;

        destroyHistoryTargets();

        GLES30.glGenTextures(2, historyTextures, 0);
        GLES30.glGenFramebuffers(2, historyFbos, 0);

        for (int i = 0; i < 2; i++) {
            configureTexture(historyTextures[i]);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, historyTextures[i]);
            GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
                    width, height, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, historyFbos[i]);
            GLES30.glFramebufferTexture2D(
                    GLES30.GL_FRAMEBUFFER,
                    GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D,
                    historyTextures[i],
                    0);
            if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
                    != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "Framebuffer temporal PS1 incompleto en índice " + i);
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
                destroyHistoryTargets();
                return false;
            }

            GLES30.glViewport(0, 0, width, height);
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        historyTargetsReady = true;
        historyWidth = width;
        historyHeight = height;
        historyValid = false;
        historyIndex = -1;
        return true;
    }

    private void resolveTemporalSource(int searchMode, boolean canUseHistory) {
        int writeIndex = historyIndex < 0 ? 0 : 1 - historyIndex;
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, historyFbos[writeIndex]);
        GLES30.glViewport(0, 0, sourceWidth, sourceHeight);
        GLES30.glUseProgram(temporalProgram);

        bindTexture(0, sourceTextures[currentSourceIndex], temporalSourceNowUniform);
        int previousTexture = previousSourceIndex >= 0
                ? sourceTextures[previousSourceIndex]
                : sourceTextures[currentSourceIndex];
        bindTexture(1, previousTexture, temporalSourcePrevUniform);

        int historyTexture = canUseHistory && historyIndex >= 0
                ? historyTextures[historyIndex]
                : sourceTextures[currentSourceIndex];
        bindTexture(2, historyTexture, temporalHistoryUniform);

        GLES30.glUniform2f(
                temporalTexelUniform,
                1.0f / Math.max(1, sourceWidth),
                1.0f / Math.max(1, sourceHeight));
        GLES30.glUniform1f(temporalHistoryValidUniform, canUseHistory ? 1.0f : 0.0f);
        GLES30.glUniform1i(temporalSearchModeUniform, searchMode);

        drawFullscreenTriangle();

        for (int unit = 0; unit < 3; unit++) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glUseProgram(0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);

        historyIndex = writeIndex;
        historyValid = true;
    }

    private void renderSpatial(int textureId, int sourceW, int sourceH,
                               int left, int bottom, int width, int height) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(left, bottom, width, height);
        GLES30.glUseProgram(spatialProgram);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
        GLES30.glUniform1i(spatialTextureUniform, 0);
        GLES30.glUniform4f(
                spatialViewportUniform,
                1.0f / Math.max(1, sourceW),
                1.0f / Math.max(1, sourceH),
                (float) sourceW,
                (float) sourceH);
        drawFullscreenTriangle();
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        GLES30.glUseProgram(0);
    }

    private void drawFullscreenTriangle() {
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        GLES30.glBindVertexArray(0);
    }

    private void bindTexture(int unit, int textureId, int uniform) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
        GLES30.glUniform1i(uniform, unit);
    }

    private void configureTexture(int textureId) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
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

    private void destroyHistoryTargets() {
        GLES30.glDeleteTextures(2, historyTextures, 0);
        GLES30.glDeleteFramebuffers(2, historyFbos, 0);
        historyTextures[0] = historyTextures[1] = 0;
        historyFbos[0] = historyFbos[1] = 0;
        historyTargetsReady = false;
        historyWidth = historyHeight = 0;
        historyValid = false;
        historyIndex = -1;
    }

    private void destroyGlResources() {
        destroyHistoryTargets();

        GLES30.glDeleteTextures(2, sourceTextures, 0);
        sourceTextures[0] = sourceTextures[1] = 0;
        sourceWidth = sourceHeight = 0;
        currentSourceIndex = previousSourceIndex = -1;

        if (vao != 0) {
            int[] vaos = {vao};
            GLES30.glDeleteVertexArrays(1, vaos, 0);
            vao = 0;
        }
        if (spatialProgram != 0) {
            GLES30.glDeleteProgram(spatialProgram);
            spatialProgram = 0;
        }
        if (temporalProgram != 0) {
            GLES30.glDeleteProgram(temporalProgram);
            temporalProgram = 0;
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
