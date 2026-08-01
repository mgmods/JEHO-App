package com.Dramizo.Series.util;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mikoo/YY VAP entry videos: left RGB + right grayscale alpha, composited to a
 * transparent TextureView. Keep this view at alpha=1 — animate a parent instead.
 */
public final class AlphaMaskVideoView extends TextureView
        implements TextureView.SurfaceTextureListener, SurfaceTexture.OnFrameAvailableListener {

    public enum MaskLayout {
        LEFT_RIGHT,
        TOP_BOTTOM,
        NONE
    }

    public interface FailureListener {
        void onFailed(@Nullable String reason);
    }

    private static final String TAG = "AlphaMaskVideo";
    // Raw 0..1 UV in the vertex; map to rgb/a plates THEN apply SurfaceTexture matrix.
    // (Applying ST first then remapping samples the wrong half → black|video SBS look.)
    private static final String VERTEX =
            "attribute vec4 aPosition;\n"
                    + "attribute vec4 aTexCoord;\n"
                    + "varying vec2 vTexCoord;\n"
                    + "void main() {\n"
                    + "  gl_Position = aPosition;\n"
                    + "  vTexCoord = aTexCoord.xy;\n"
                    + "}\n";

    private static final String FRAG_LEFT_RIGHT =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float;\n"
                    + "varying vec2 vTexCoord;\n"
                    + "uniform samplerExternalOES sTexture;\n"
                    + "uniform mat4 uSTMatrix;\n"
                    + "uniform vec4 uRgbRect;\n"
                    + "uniform vec4 uARect;\n"
                    + "void main() {\n"
                    + "  vec2 rgbLocal = vec2(uRgbRect.x + vTexCoord.x * uRgbRect.z,\n"
                    + "                       uRgbRect.y + vTexCoord.y * uRgbRect.w);\n"
                    + "  vec2 aLocal = vec2(uARect.x + vTexCoord.x * uARect.z,\n"
                    + "                     uARect.y + vTexCoord.y * uARect.w);\n"
                    + "  vec2 rgbUv = (uSTMatrix * vec4(rgbLocal, 0.0, 1.0)).xy;\n"
                    + "  vec2 aUv = (uSTMatrix * vec4(aLocal, 0.0, 1.0)).xy;\n"
                    + "  vec4 rgb = texture2D(sTexture, rgbUv);\n"
                    + "  vec4 mask = texture2D(sTexture, aUv);\n"
                    + "  float a = max(mask.r, max(mask.g, mask.b));\n"
                    + "  gl_FragColor = vec4(rgb.rgb * a, a);\n"
                    + "}\n";

    private static final String FRAG_TOP_BOTTOM =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float;\n"
                    + "varying vec2 vTexCoord;\n"
                    + "uniform samplerExternalOES sTexture;\n"
                    + "uniform mat4 uSTMatrix;\n"
                    + "void main() {\n"
                    + "  vec2 rgbLocal = vec2(vTexCoord.x, vTexCoord.y * 0.5);\n"
                    + "  vec2 aLocal = vec2(vTexCoord.x, 0.5 + vTexCoord.y * 0.5);\n"
                    + "  vec2 rgbUv = (uSTMatrix * vec4(rgbLocal, 0.0, 1.0)).xy;\n"
                    + "  vec2 aUv = (uSTMatrix * vec4(aLocal, 0.0, 1.0)).xy;\n"
                    + "  vec4 rgb = texture2D(sTexture, rgbUv);\n"
                    + "  vec4 mask = texture2D(sTexture, aUv);\n"
                    + "  float a = max(mask.r, max(mask.g, mask.b));\n"
                    + "  gl_FragColor = vec4(rgb.rgb * a, a);\n"
                    + "}\n";

    private static final String FRAG_NONE =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float;\n"
                    + "varying vec2 vTexCoord;\n"
                    + "uniform samplerExternalOES sTexture;\n"
                    + "uniform mat4 uSTMatrix;\n"
                    + "void main() {\n"
                    + "  vec2 uv = (uSTMatrix * vec4(vTexCoord, 0.0, 1.0)).xy;\n"
                    + "  gl_FragColor = texture2D(sTexture, uv);\n"
                    + "}\n";

    private final float[] stMatrix = new float[16];
    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
    private final AtomicBoolean firstFrameDrawn = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable private ExoPlayer player;
    @Nullable private SurfaceTexture decoderTexture;
    @Nullable private Surface decoderSurface;
    @Nullable private RenderThread renderThread;
    @Nullable private String pendingUrl;
    @Nullable private FailureListener failureListener;
    private MaskLayout maskLayout = MaskLayout.LEFT_RIGHT;
    private boolean loop = true;
    private float volume = 1f;
    private volatile boolean surfaceReady;
    private volatile boolean glReady;
    private int textureId;
    /** Normalized UV rects from vapc (x,y,w,h). Defaults = classic 50/50 SBS. */
    private volatile float[] rgbRect = {0f, 0f, 0.5f, 1f};
    private volatile float[] aRect = {0.5f, 0f, 0.5f, 1f};
    @Nullable private volatile VapLayout vapLayout;
    private final Runnable failWatchdog = () -> {
        if (!firstFrameDrawn.get()) {
            Log.w(TAG, "no frame within watchdog — failing over");
            notifyFailed("timeout");
        }
    };

    public AlphaMaskVideoView(Context context) {
        this(context, null);
    }

    public AlphaMaskVideoView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOpaque(false);
        setSurfaceTextureListener(this);
    }

    public void setMaskLayout(MaskLayout layout) {
        this.maskLayout = layout != null ? layout : MaskLayout.LEFT_RIGHT;
    }

    public void setLooping(boolean looping) {
        this.loop = looping;
        if (player != null) {
            player.setRepeatMode(looping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        }
    }

    /** Entry intros include audio tracks — 1f = full, 0f = mute. */
    public void setVolume(float vol) {
        this.volume = Math.max(0f, Math.min(1f, vol));
        if (player != null) {
            player.setVolume(this.volume);
        }
    }

    public void setFailureListener(@Nullable FailureListener listener) {
        this.failureListener = listener;
    }

    /** Apply parsed vapc layout (call before or while playing). */
    public void setVapLayout(@Nullable VapLayout layout) {
        if (layout == null) {
            vapLayout = null;
            rgbRect = new float[]{0f, 0f, 0.5f, 1f};
            aRect = new float[]{0.5f, 0f, 0.5f, 1f};
            return;
        }
        vapLayout = layout;
        rgbRect = new float[]{layout.rgbX, layout.rgbY, layout.rgbW, layout.rgbH};
        aRect = new float[]{layout.aX, layout.aY, layout.aW, layout.aH};
    }

    @Nullable
    public VapLayout getVapLayout() {
        return vapLayout;
    }

    public boolean hasDrawnFrame() {
        return firstFrameDrawn.get();
    }

    public void play(String url) {
        pendingUrl = url;
        firstFrameDrawn.set(false);
        mainHandler.removeCallbacks(failWatchdog);
        mainHandler.postDelayed(failWatchdog, 5000);
        if (vapLayout != null) {
            tryStartPlayback();
            return;
        }
        // Resolve vapc off the UI thread so half-res alpha plates map correctly.
        new Thread(() -> {
            VapLayout layout = VapLayout.fetch(url);
            if (layout != null) setVapLayout(layout);
            else setVapLayout(VapLayout.leftRightHalf());
            mainHandler.post(this::tryStartPlayback);
        }, "vapc-fetch").start();
    }

    public void release() {
        mainHandler.removeCallbacks(failWatchdog);
        pendingUrl = null;
        failureListener = null;
        releasePlayer();
        RenderThread thread = renderThread;
        renderThread = null;
        glReady = false;
        surfaceReady = false;
        if (thread != null) thread.shutdown();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        surfaceReady = true;
        RenderThread old = renderThread;
        if (old != null) old.shutdown();
        renderThread = new RenderThread(surface);
        renderThread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        surfaceReady = false;
        glReady = false;
        releasePlayer();
        RenderThread thread = renderThread;
        renderThread = null;
        if (thread != null) thread.shutdown();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        frameAvailable.set(true);
        RenderThread thread = renderThread;
        if (thread != null) thread.requestDraw();
    }

    private void tryStartPlayback() {
        if (pendingUrl == null || pendingUrl.isEmpty()) return;
        if (!surfaceReady || !glReady) return;
        startPlayback(pendingUrl);
    }

    private void startPlayback(String url) {
        RenderThread thread = renderThread;
        if (thread == null || !glReady) return;
        releasePlayerOnly();
        thread.runOnGl(() -> {
            if (textureId == 0) {
                int[] tex = new int[1];
                GLES20.glGenTextures(1, tex, 0);
                textureId = tex[0];
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            }
            if (decoderTexture != null) {
                try { decoderTexture.release(); } catch (Exception ignored) {}
            }
            if (decoderSurface != null) {
                try { decoderSurface.release(); } catch (Exception ignored) {}
            }
            decoderTexture = new SurfaceTexture(textureId);
            decoderTexture.setOnFrameAvailableListener(AlphaMaskVideoView.this, mainHandler);
            decoderSurface = new Surface(decoderTexture);
            final Surface surface = decoderSurface;
            mainHandler.post(() -> {
                if (!surfaceReady || surface == null || !surface.isValid()) return;
                try {
                    Context app = getContext().getApplicationContext();
                    player = new ExoPlayer.Builder(app).build();
                    player.setRepeatMode(loop ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
                    player.setVolume(volume);
                    player.setVideoSurface(surface);
                    player.addListener(new Player.Listener() {
                        @Override
                        public void onPlayerError(PlaybackException error) {
                            Log.w(TAG, "exo error", error);
                            notifyFailed(error.getMessage());
                        }

                        @Override
                        public void onRenderedFirstFrame() {
                            firstFrameDrawn.set(true);
                            mainHandler.removeCallbacks(failWatchdog);
                        }
                    });
                    player.setMediaItem(MediaItem.fromUri(url));
                    player.prepare();
                    player.setPlayWhenReady(true);
                } catch (Exception e) {
                    Log.w(TAG, "player start failed", e);
                    notifyFailed(e.getMessage());
                }
            });
        });
    }

    private void notifyFailed(@Nullable String reason) {
        mainHandler.removeCallbacks(failWatchdog);
        FailureListener l = failureListener;
        if (l != null) {
            failureListener = null;
            mainHandler.post(() -> l.onFailed(reason));
        }
    }

    private void releasePlayerOnly() {
        if (player != null) {
            try {
                player.setPlayWhenReady(false);
                player.clearVideoSurface();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private void releasePlayer() {
        releasePlayerOnly();
        if (decoderSurface != null) {
            try { decoderSurface.release(); } catch (Exception ignored) {}
            decoderSurface = null;
        }
        if (decoderTexture != null) {
            try {
                decoderTexture.setOnFrameAvailableListener(null);
                decoderTexture.release();
            } catch (Exception ignored) {}
            decoderTexture = null;
        }
    }

    private final class RenderThread extends Thread {
        private final SurfaceTexture outputTexture;
        private final Object lock = new Object();
        private volatile boolean running = true;
        private volatile boolean drawRequested = true;
        @Nullable private Runnable glTask;
        private android.opengl.EGLDisplay eglDisplay;
        private android.opengl.EGLContext eglContext;
        private android.opengl.EGLSurface eglSurface;
        private int program;
        private int aPosition;
        private int aTexCoord;
        private int uSTMatrix;
        private int sTexture;
        private int uRgbRect;
        private int uARect;
        private FloatBuffer vertBuffer;
        private FloatBuffer texBuffer;

        RenderThread(SurfaceTexture output) {
            super("AlphaMaskGL");
            this.outputTexture = output;
        }

        void shutdown() {
            running = false;
            synchronized (lock) {
                lock.notifyAll();
            }
            try {
                join(900);
            } catch (InterruptedException ignored) {
                interrupt();
            }
        }

        void requestDraw() {
            synchronized (lock) {
                drawRequested = true;
                lock.notifyAll();
            }
        }

        void runOnGl(Runnable task) {
            synchronized (lock) {
                glTask = task;
                lock.notifyAll();
            }
        }

        @Override
        public void run() {
            if (!initEgl()) {
                mainHandler.post(() -> notifyFailed("egl"));
                return;
            }
            if (!initGl()) {
                mainHandler.post(() -> notifyFailed("shader"));
                releaseGl();
                return;
            }
            glReady = true;
            mainHandler.post(AlphaMaskVideoView.this::tryStartPlayback);
            while (running) {
                Runnable task;
                boolean draw;
                synchronized (lock) {
                    while (running && glTask == null && !drawRequested && !frameAvailable.get()) {
                        try {
                            lock.wait(16);
                        } catch (InterruptedException e) {
                            interrupt();
                            break;
                        }
                    }
                    task = glTask;
                    glTask = null;
                    draw = drawRequested || frameAvailable.get();
                    drawRequested = false;
                }
                if (task != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        Log.w(TAG, "gl task failed", e);
                    }
                }
                if (draw) drawFrame();
            }
            glReady = false;
            releaseGl();
        }

        private boolean initEgl() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false;
            int[] ver = new int[2];
            if (!EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)) return false;
            int[] attribList = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_NONE
            };
            android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
            int[] num = new int[1];
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, num, 0) || num[0] <= 0) {
                return false;
            }
            int[] ctxAttr = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0);
            eglSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay, configs[0], outputTexture, new int[]{EGL14.EGL_NONE}, 0);
            return eglContext != null
                    && eglSurface != null
                    && EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
        }

        private boolean initGl() {
            String frag = maskLayout == MaskLayout.TOP_BOTTOM ? FRAG_TOP_BOTTOM
                    : maskLayout == MaskLayout.NONE ? FRAG_NONE
                    : FRAG_LEFT_RIGHT;
            program = buildProgram(VERTEX, frag);
            if (program == 0) return false;
            aPosition = GLES20.glGetAttribLocation(program, "aPosition");
            aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord");
            uSTMatrix = GLES20.glGetUniformLocation(program, "uSTMatrix");
            sTexture = GLES20.glGetUniformLocation(program, "sTexture");
            uRgbRect = GLES20.glGetUniformLocation(program, "uRgbRect");
            uARect = GLES20.glGetUniformLocation(program, "uARect");
            // Standard GL quad; SurfaceTexture ST matrix handles decoder Y-flip.
            float[] verts = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};
            float[] tex = {0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f};
            vertBuffer = ByteBuffer.allocateDirect(verts.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertBuffer.put(verts).position(0);
            texBuffer = ByteBuffer.allocateDirect(tex.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            texBuffer.put(tex).position(0);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            return true;
        }

        private void drawFrame() {
            if (decoderTexture == null || program == 0) return;
            if (frameAvailable.compareAndSet(true, false)) {
                try {
                    decoderTexture.updateTexImage();
                    decoderTexture.getTransformMatrix(stMatrix);
                    firstFrameDrawn.set(true);
                    mainHandler.removeCallbacks(failWatchdog);
                } catch (Exception e) {
                    return;
                }
            } else if (!firstFrameDrawn.get()) {
                return;
            }
            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());
            GLES20.glViewport(0, 0, w, h);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(sTexture, 0);
            GLES20.glUniformMatrix4fv(uSTMatrix, 1, false, stMatrix, 0);
            if (uRgbRect >= 0) {
                float[] r = rgbRect;
                GLES20.glUniform4f(uRgbRect, r[0], r[1], r[2], r[3]);
            }
            if (uARect >= 0) {
                float[] a = aRect;
                GLES20.glUniform4f(uARect, a[0], a[1], a[2], a[3]);
            }
            vertBuffer.position(0);
            GLES20.glEnableVertexAttribArray(aPosition);
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertBuffer);
            texBuffer.position(0);
            GLES20.glEnableVertexAttribArray(aTexCoord);
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(aPosition);
            GLES20.glDisableVertexAttribArray(aTexCoord);
            EGL14.eglSwapBuffers(eglDisplay, eglSurface);
        }

        private void releaseGl() {
            if (program != 0) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            if (textureId != 0) {
                int[] t = {textureId};
                GLES20.glDeleteTextures(1, t, 0);
                textureId = 0;
            }
            if (eglDisplay != null) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT);
                if (eglSurface != null) EGL14.eglDestroySurface(eglDisplay, eglSurface);
                if (eglContext != null) EGL14.eglDestroyContext(eglDisplay, eglContext);
                EGL14.eglTerminate(eglDisplay);
            }
            eglDisplay = null;
            eglContext = null;
            eglSurface = null;
        }

        private int buildProgram(String vertex, String fragment) {
            int vs = loadShader(GLES20.GL_VERTEX_SHADER, vertex);
            int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragment);
            if (vs == 0 || fs == 0) return 0;
            int prog = GLES20.glCreateProgram();
            GLES20.glAttachShader(prog, vs);
            GLES20.glAttachShader(prog, fs);
            GLES20.glLinkProgram(prog);
            int[] link = new int[1];
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, link, 0);
            if (link[0] == 0) {
                Log.e(TAG, "link error: " + GLES20.glGetProgramInfoLog(prog));
                GLES20.glDeleteProgram(prog);
                return 0;
            }
            return prog;
        }

        private int loadShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "shader error: " + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                return 0;
            }
            return shader;
        }
    }
}
