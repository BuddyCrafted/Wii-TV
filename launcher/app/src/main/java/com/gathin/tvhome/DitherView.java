package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Choreographer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Animated ordered-dither backdrop, a port of React Bits' Dither: fractal noise waves are rendered into a
 * field one cell per pixel, then quantised to a few colour levels through an 8x8 Bayer matrix at full
 * resolution. The wave colour drifts round the hue wheel.
 *
 * A SurfaceView rather than a TextureView: SurfaceFlinger composites these frames under the window, so the
 * launcher's own views are not redrawn every time the backdrop moves. Frames are requested at half the
 * display rate, and stop while Home is paused.
 */
public class DitherView extends GLSurfaceView {

    private static final String TAG = "DitherView";
    private static final float INTRO_SECONDS = 1.8f;
    private static final float WAVE_SPEED = 0.05f;
    private static final float WAVE_FREQUENCY = 3f;
    private static final float WAVE_AMPLITUDE = 0.3f;
    private static final float COLOR_NUM = 4f;
    private static final float WAVE_SATURATION = 0.5f;
    private static final float WAVE_VALUE = 0.78f;
    private static final int BACKGROUND = 0xFF120F17;

    private static final String VERTEX =
            "#version 300 es\n"
            + "in vec2 aPosition;\n"
            + "void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }\n";

    private static final String WAVE_FRAGMENT =
            "#version 300 es\n"
            + "precision highp float;\n"
            + "uniform vec2 uResolution;\n"
            + "uniform float uCell;\n"
            + "uniform float uTime;\n"
            + "uniform float uWaveSpeed;\n"
            + "uniform float uWaveFrequency;\n"
            + "uniform float uWaveAmplitude;\n"
            + "uniform float uIntro;\n"
            + "uniform float uReach;\n"
            + "out vec4 outColor;\n"
            + "vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }\n"
            + "vec4 permute(vec4 x) { return mod289(((x * 34.0) + 1.0) * x); }\n"
            + "vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }\n"
            + "vec2 fade(vec2 t) { return t * t * t * (t * (t * 6.0 - 15.0) + 10.0); }\n"
            + "float cnoise(vec2 P) {\n"
            + "  vec4 Pi = floor(P.xyxy) + vec4(0.0, 0.0, 1.0, 1.0);\n"
            + "  vec4 Pf = fract(P.xyxy) - vec4(0.0, 0.0, 1.0, 1.0);\n"
            + "  Pi = mod289(Pi);\n"
            + "  vec4 ix = Pi.xzxz; vec4 iy = Pi.yyww; vec4 fx = Pf.xzxz; vec4 fy = Pf.yyww;\n"
            + "  vec4 i = permute(permute(ix) + iy);\n"
            + "  vec4 gx = fract(i * (1.0 / 41.0)) * 2.0 - 1.0;\n"
            + "  vec4 gy = abs(gx) - 0.5;\n"
            + "  vec4 tx = floor(gx + 0.5);\n"
            + "  gx = gx - tx;\n"
            + "  vec2 g00 = vec2(gx.x, gy.x); vec2 g10 = vec2(gx.y, gy.y);\n"
            + "  vec2 g01 = vec2(gx.z, gy.z); vec2 g11 = vec2(gx.w, gy.w);\n"
            + "  vec4 norm = taylorInvSqrt(vec4(dot(g00, g00), dot(g01, g01), dot(g10, g10), dot(g11, g11)));\n"
            + "  g00 *= norm.x; g01 *= norm.y; g10 *= norm.z; g11 *= norm.w;\n"
            + "  float n00 = dot(g00, vec2(fx.x, fy.x)); float n10 = dot(g10, vec2(fx.y, fy.y));\n"
            + "  float n01 = dot(g01, vec2(fx.z, fy.z)); float n11 = dot(g11, vec2(fx.w, fy.w));\n"
            + "  vec2 fadeXY = fade(Pf.xy);\n"
            + "  vec2 nx = mix(vec2(n00, n01), vec2(n10, n11), fadeXY.x);\n"
            + "  return 2.3 * mix(nx.x, nx.y, fadeXY.y);\n"
            + "}\n"
            + "float fbm(vec2 p) {\n"
            + "  float value = 0.0; float amp = 1.0;\n"
            + "  for (int i = 0; i < 4; i++) { value += amp * abs(cnoise(p)); p *= uWaveFrequency; amp *= uWaveAmplitude; }\n"
            + "  return value;\n"
            + "}\n"
            + "float pattern(vec2 p) { vec2 p2 = p - uTime * uWaveSpeed; return fbm(p + fbm(p2)); }\n"
            + "void main() {\n"
            + "  vec2 corner = (gl_FragCoord.xy - 0.5) * uCell;\n"
            + "  vec2 uv = corner / uResolution - 0.5;\n"
            + "  uv.x *= uResolution.x / uResolution.y;\n"
            + "  float f = pattern(uv);\n"
            + "  float show = clamp((uIntro * uReach - length(uv)) / 0.45, 0.0, 1.0);\n"
            + "  float v = clamp(f, 0.0, 1.0) * show * show * (3.0 - 2.0 * show);\n"
            // Two 8-bit channels carry the value, so the field keeps more than 256 levels.
            + "  outColor = vec4(floor(v * 255.0) / 255.0, fract(v * 255.0), 0.0, 1.0);\n"
            + "}\n";

    private static final String DITHER_FRAGMENT =
            "#version 300 es\n"
            + "precision highp float;\n"
            + "precision highp int;\n"
            + "uniform sampler2D uField;\n"
            + "uniform ivec2 uFieldSize;\n"
            + "uniform float uCellDevice;\n"
            + "uniform float uColorNum;\n"
            + "uniform vec3 uWave;\n"
            + "uniform vec3 uBackground;\n"
            + "out vec4 outColor;\n"
            + "const float bayer8[64] = float[64](\n"
            + "  0.0/64.0, 48.0/64.0, 12.0/64.0, 60.0/64.0, 3.0/64.0, 51.0/64.0, 15.0/64.0, 63.0/64.0,\n"
            + "  32.0/64.0, 16.0/64.0, 44.0/64.0, 28.0/64.0, 35.0/64.0, 19.0/64.0, 47.0/64.0, 31.0/64.0,\n"
            + "  8.0/64.0, 56.0/64.0, 4.0/64.0, 52.0/64.0, 11.0/64.0, 59.0/64.0, 7.0/64.0, 55.0/64.0,\n"
            + "  40.0/64.0, 24.0/64.0, 36.0/64.0, 20.0/64.0, 43.0/64.0, 27.0/64.0, 39.0/64.0, 23.0/64.0,\n"
            + "  2.0/64.0, 50.0/64.0, 14.0/64.0, 62.0/64.0, 1.0/64.0, 49.0/64.0, 13.0/64.0, 61.0/64.0,\n"
            + "  34.0/64.0, 18.0/64.0, 46.0/64.0, 30.0/64.0, 33.0/64.0, 17.0/64.0, 45.0/64.0, 29.0/64.0,\n"
            + "  10.0/64.0, 58.0/64.0, 6.0/64.0, 54.0/64.0, 9.0/64.0, 57.0/64.0, 5.0/64.0, 53.0/64.0,\n"
            + "  42.0/64.0, 26.0/64.0, 38.0/64.0, 22.0/64.0, 41.0/64.0, 25.0/64.0, 37.0/64.0, 21.0/64.0);\n"
            + "vec3 toSrgb(vec3 c) {\n"
            + "  c = clamp(c, 0.0, 1.0);\n"
            + "  return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));\n"
            + "}\n"
            + "void main() {\n"
            + "  vec2 cell = floor(gl_FragCoord.xy / uCellDevice);\n"
            + "  vec4 s = texelFetch(uField, clamp(ivec2(cell), ivec2(0), uFieldSize - 1), 0);\n"
            + "  float f = s.r + s.g / 255.0;\n"
            + "  ivec2 c = ivec2(mod(cell, 8.0));\n"
            + "  float t = bayer8[c.y * 8 + c.x] - 0.25;\n"
            + "  float levels = max(uColorNum, 1.01) - 1.0;\n"
            + "  vec3 luma = vec3(0.2126, 0.7152, 0.0722);\n"
            + "  float base = dot(uBackground, luma);\n"
            + "  vec3 offset = (uWave - vec3(base)) * f + t / levels;\n"
            + "  float luminance = dot(uBackground + offset, luma);\n"
            + "  offset -= mix(0.2, 0.0, smoothstep(0.45, 0.8, luminance));\n"
            + "  bool rising = dot(uWave, luma) >= base;\n"
            + "  float lower = rising ? 0.0 : -max(uBackground.r, max(uBackground.g, uBackground.b));\n"
            + "  float upper = rising ? 1.0 - min(uBackground.r, min(uBackground.g, uBackground.b)) : 0.0;\n"
            + "  offset = floor(clamp(offset, lower, upper) * levels + 0.5) / levels;\n"
            + "  outColor = vec4(toSrgb(uBackground + offset), 1.0);\n"
            + "}\n";

    private boolean configured;
    private boolean running;
    private float cellPx = 4f;
    private float hueCycleSeconds = 60f;
    private int frame;
    // Only touched on the GL thread. Kept on the view, not the renderer's surface, so the waves carry on
    // where they were after Home is paused and the surface is made again.
    private float time;
    private float reveal;
    private long lastNanos;

    private final Choreographer.FrameCallback tick = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!running) return;
            if ((frame++ & 1) == 0) requestRender();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public DitherView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Starts the GL thread. Without this call the view stays an inert, never-attached surface. */
    public void configure(float pixelSizePx, int hueCycle) {
        if (configured) return;
        cellPx = Math.max(1f, pixelSizePx);
        hueCycleSeconds = Math.max(5, hueCycle);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(new DitherRenderer());
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        configured = true;
    }

    public void start() {
        if (!configured || running) return;
        running = true;
        onResume();
        Choreographer.getInstance().postFrameCallback(tick);
    }

    public void stop() {
        if (!configured || !running) return;
        running = false;
        Choreographer.getInstance().removeFrameCallback(tick);
        onPause();
    }

    private static float toLinear(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    private static void linearRgb(int color, float[] out) {
        out[0] = toLinear(Color.red(color) / 255f);
        out[1] = toLinear(Color.green(color) / 255f);
        out[2] = toLinear(Color.blue(color) / 255f);
    }

    private final class DitherRenderer implements Renderer {
        private boolean ok;
        private int wave, dither;
        private int fieldTex, fbo, vao;
        private int width = 1, height = 1, cell = 1, cols = 1, rows = 1;
        private int wResolution, wCell, wTime, wSpeed, wFrequency, wAmplitude, wIntro, wReach;
        private int dField, dFieldSize, dCell, dColorNum, dWave, dBackground;
        private final float[] background = new float[3];
        private final float[] waveRgb = new float[3];
        private final float[] hsv = new float[3];

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            linearRgb(BACKGROUND, background);
            wave = link(WAVE_FRAGMENT);
            dither = link(DITHER_FRAGMENT);
            ok = wave != 0 && dither != 0;
            if (!ok) return;
            wResolution = GLES30.glGetUniformLocation(wave, "uResolution");
            wCell = GLES30.glGetUniformLocation(wave, "uCell");
            wTime = GLES30.glGetUniformLocation(wave, "uTime");
            wSpeed = GLES30.glGetUniformLocation(wave, "uWaveSpeed");
            wFrequency = GLES30.glGetUniformLocation(wave, "uWaveFrequency");
            wAmplitude = GLES30.glGetUniformLocation(wave, "uWaveAmplitude");
            wIntro = GLES30.glGetUniformLocation(wave, "uIntro");
            wReach = GLES30.glGetUniformLocation(wave, "uReach");
            dField = GLES30.glGetUniformLocation(dither, "uField");
            dFieldSize = GLES30.glGetUniformLocation(dither, "uFieldSize");
            dCell = GLES30.glGetUniformLocation(dither, "uCellDevice");
            dColorNum = GLES30.glGetUniformLocation(dither, "uColorNum");
            dWave = GLES30.glGetUniformLocation(dither, "uWave");
            dBackground = GLES30.glGetUniformLocation(dither, "uBackground");

            int[] ids = new int[1];
            GLES30.glGenVertexArrays(1, ids, 0);
            vao = ids[0];
            GLES30.glBindVertexArray(vao);
            GLES30.glGenBuffers(1, ids, 0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0]);
            // One triangle that covers the whole viewport.
            FloatBuffer tri = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            tri.put(new float[]{-1f, -1f, 3f, -1f, -1f, 3f}).position(0);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 6 * 4, tri, GLES30.GL_STATIC_DRAW);
            GLES30.glEnableVertexAttribArray(0);
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0);

            GLES30.glGenTextures(1, ids, 0);
            fieldTex = ids[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fieldTex);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glGenFramebuffers(1, ids, 0);
            fbo = ids[0];
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int w, int h) {
            width = Math.max(1, w);
            height = Math.max(1, h);
            if (!ok) return;
            cell = Math.max(1, Math.round(cellPx));
            cols = Math.max(1, (width + cell - 1) / cell);
            rows = Math.max(1, (height + cell - 1) / cell);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fieldTex);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, cols, rows, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D, fieldTex, 0);
            int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                Log.w(TAG, "field framebuffer incomplete: 0x" + Integer.toHexString(status));
                ok = false;
            }
        }

        @Override
        public void onDrawFrame(GL10 unused) {
            long now = System.nanoTime();
            float dt = lastNanos == 0 ? 1f / 30f : Math.max(0.0005f, Math.min(0.05f, (now - lastNanos) / 1e9f));
            lastNanos = now;
            time += dt;
            if (reveal < 1f) reveal = Math.min(1f, reveal + dt / INTRO_SECONDS);

            if (!ok) {
                GLES30.glClearColor(Color.red(BACKGROUND) / 255f, Color.green(BACKGROUND) / 255f,
                        Color.blue(BACKGROUND) / 255f, 1f);
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                return;
            }

            hsv[0] = (time / hueCycleSeconds % 1f) * 360f;
            hsv[1] = WAVE_SATURATION;
            hsv[2] = WAVE_VALUE;
            linearRgb(Color.HSVToColor(hsv), waveRgb);
            float eased = reveal * reveal * (3f - 2f * reveal);

            GLES30.glBindVertexArray(vao);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
            GLES30.glViewport(0, 0, cols, rows);
            GLES30.glUseProgram(wave);
            GLES30.glUniform2f(wResolution, width, height);
            GLES30.glUniform1f(wCell, cell);
            GLES30.glUniform1f(wTime, time);
            GLES30.glUniform1f(wSpeed, WAVE_SPEED);
            GLES30.glUniform1f(wFrequency, WAVE_FREQUENCY);
            GLES30.glUniform1f(wAmplitude, WAVE_AMPLITUDE);
            GLES30.glUniform1f(wIntro, eased);
            GLES30.glUniform1f(wReach, 0.5f * (float) Math.hypot(width / (float) height, 1f) + 0.45f);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            GLES30.glViewport(0, 0, width, height);
            GLES30.glUseProgram(dither);
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fieldTex);
            GLES30.glUniform1i(dField, 0);
            GLES30.glUniform2i(dFieldSize, cols, rows);
            GLES30.glUniform1f(dCell, cell);
            GLES30.glUniform1f(dColorNum, COLOR_NUM);
            GLES30.glUniform3fv(dWave, 1, waveRgb, 0);
            GLES30.glUniform3fv(dBackground, 1, background, 0);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
        }

        private int compile(int type, String source) {
            int shader = GLES30.glCreateShader(type);
            if (shader == 0) return 0;
            GLES30.glShaderSource(shader, source);
            GLES30.glCompileShader(shader);
            int[] status = new int[1];
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
            if (status[0] != 0) return shader;
            Log.w(TAG, "shader compile failed: " + GLES30.glGetShaderInfoLog(shader));
            GLES30.glDeleteShader(shader);
            return 0;
        }

        private int link(String fragmentSource) {
            int vertex = compile(GLES30.GL_VERTEX_SHADER, VERTEX);
            int fragment = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
            int program = GLES30.glCreateProgram();
            if (vertex == 0 || fragment == 0 || program == 0) return 0;
            GLES30.glAttachShader(program, vertex);
            GLES30.glAttachShader(program, fragment);
            GLES30.glBindAttribLocation(program, 0, "aPosition");
            GLES30.glLinkProgram(program);
            GLES30.glDeleteShader(vertex);
            GLES30.glDeleteShader(fragment);
            int[] status = new int[1];
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0);
            if (status[0] != 0) return program;
            Log.w(TAG, "program link failed: " + GLES30.glGetProgramInfoLog(program));
            GLES30.glDeleteProgram(program);
            return 0;
        }
    }
}
