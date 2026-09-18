package com.xkmxz.prismod.client.filter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * 使用真实驱动编译资源并回读离屏像素；通过 -PprismodRenderTests 显式启用。
 * 1080p 计时仅覆盖独立滤镜绘制和颜色回拷，不代表游戏或 Oculus 场景验收。
 */
@EnabledIfSystemProperty(named = "prismod.renderTests", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FilterShaderGlTest {
    private static final float EPSILON = 0.0002F;
    private static final int WIDTH = 8;
    private static final int HEIGHT = 2;
    // 每个像素使用不同颜色或透明度，覆盖暗部、极值、饱和色与不会发生裁剪的中间色。
    private static final float[] COLORS = {
            0, 0, 0, 0,             1, 1, 1, 1,             .4F, .4F, .4F, .5F,    1, 0, 0, .2F,
            0, 1, 0, .4F,          0, 0, 1, .6F,          .2F, .3F, .4F, .8F,    .6F, .3F, .2F, 1,
            .01F, .02F, .03F, .1F, .8F, .7F, .6F, .3F,    1, 0, 1, .7F,          0, 1, 1, .9F,
            1, 1, 0, 1,            .1F, .1F, .1F, .25F,   .7F, .7F, .7F, .75F,  .25F, .5F, .75F, 1
    };

    private long window;
    private GLFWErrorCallback errorCallback;

    @BeforeAll
    void createHiddenContext() {
        errorCallback = GLFWErrorCallback.createPrint(System.err);
        errorCallback.set();
        assertTrue(glfwInit(), "启用渲染测试时必须成功初始化 GLFW，不能静默跳过");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(64, 64, "Prismod 着色器测试", 0L, 0L);
        assertNotEquals(0L, window, "无法建立 OpenGL 3.3 core 上下文");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glfwSwapInterval(0);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DITHER);
        glDisable(GL_FRAMEBUFFER_SRGB);
        System.out.printf("PRISMOD_GPU_HARDWARE vendor=%s; renderer=%s; version=%s%n",
                glGetString(GL_VENDOR), glGetString(GL_RENDERER), glGetString(GL_VERSION));
        assertNoGlError("创建上下文");
    }

    @AfterAll
    void destroyHiddenContext() {
        if (window != 0L) {
            GL.setCapabilities(null);
            glfwMakeContextCurrent(0L);
            glfwDestroyWindow(window);
        }
        glfwTerminate();
        glfwSetErrorCallback(null);
        if (errorCallback != null) {
            errorCallback.free();
        }
    }

    @ParameterizedTest(name = "真实 GPU 验证 {0} 的 0/0.5/1 强度")
    @ValueSource(strings = {"grayscale", "warm", "cool", "vintage", "night_vision"})
    void rendersKnownColorsWithCorrectStrengthAndAlpha(String filter) throws IOException {
        try (Scene scene = new Scene(filter, WIDTH, HEIGHT, GL_RGBA32F, COLORS)) {
            float[] zero = scene.renderAndRead(0.0F);
            float[] half = scene.renderAndRead(0.5F);
            float[] full = scene.renderAndRead(1.0F);
            assertArrayEquals(COLORS, zero, EPSILON, "零强度必须逐像素还原原图");
            for (int i = 0; i < COLORS.length; i++) {
                assertTrue(Float.isFinite(full[i]) && Float.isFinite(half[i]), "输出不得为 NaN 或无穷");
                assertTrue(full[i] >= -EPSILON && full[i] <= 1.0F + EPSILON, "输出应在颜色范围内");
                if (i % 4 == 3) {
                    assertEquals(COLORS[i], half[i], EPSILON, "半强度必须保持透明度");
                    assertEquals(COLORS[i], full[i], EPSILON, "完整强度必须保持透明度");
                } else {
                    assertEquals((COLORS[i] + full[i]) * .5F, half[i], EPSILON,
                            "半强度应为原图与完整滤镜的线性插值，分量 " + i);
                }
            }
            assertFilterAppearance(filter, full);
            assertArrayEquals(zero, scene.renderAndRead(-1.0F), EPSILON, "负强度应裁剪为零");
            assertArrayEquals(full, scene.renderAndRead(2.0F), EPSILON, "过大强度应裁剪为一");
            scene.copyToMain();
            assertArrayEquals(full, scene.readMain(), EPSILON, "颜色回拷必须保留滤镜像素和透明度");
            assertNoGlError(filter + " 渲染与回拷");
        }
        assertNoGlError(filter + " 资源释放");
    }

    private static void assertFilterAppearance(String filter, float[] colors) {
        int gray = 2 * 4;
        switch (filter) {
            case "grayscale" -> {
                for (int i = 0; i < colors.length; i += 4) {
                    assertEquals(colors[i], colors[i + 1], EPSILON);
                    assertEquals(colors[i], colors[i + 2], EPSILON);
                    assertEquals(luma(COLORS, i), colors[i], EPSILON, "黑白应保持感知亮度");
                }
            }
            case "warm", "cool" -> {
                if (filter.equals("warm")) {
                    assertTrue(colors[gray] > colors[gray + 2] + .02F, "暖色应偏红");
                } else {
                    assertTrue(colors[gray + 2] > colors[gray] + .02F, "冷色应偏蓝");
                }
                assertEquals(luma(COLORS, gray), luma(colors, gray), EPSILON,
                        "没有通道裁剪的灰色应保持感知亮度");
                for (int i = 0; i < colors.length; i += 4) {
                    assertEquals(luma(COLORS, i), luma(colors, i), .08F,
                            "高饱和度与高亮处裁剪引起的亮度差应受限");
                }
            }
            case "vintage" -> {
                assertTrue(colors[gray] > colors[gray + 1] && colors[gray + 1] > colors[gray + 2],
                        "复古色调应略偏暖");
                assertTrue(luma(colors, 0) > .03F, "复古应抬升黑位");
                assertTrue(luma(colors, 4) < .97F, "复古应压低白位");
            }
            case "night_vision" -> {
                assertTrue(colors[gray + 1] > colors[gray] && colors[gray + 1] > colors[gray + 2],
                        "夜视应以绿色为主");
                assertTrue(luma(colors, 0) > .03F, "夜视应抬升黑位");
                assertTrue(luma(colors, 8 * 4) > luma(COLORS, 8 * 4) + .03F, "夜视应提亮暗部");
            }
            default -> fail("未验证的滤镜: " + filter);
        }
    }

    private static float luma(float[] color, int offset) {
        return color[offset] * .2126F + color[offset + 1] * .7152F + color[offset + 2] * .0722F;
    }

    @Test
    void reports1080pGpuTimeForFilterAndColorCopy() throws IOException {
        final int warmupFrames = 60;
        final int sampleFrames = 300;
        assertTrue(glGetQueryi(GL_TIMESTAMP, GL_QUERY_COUNTER_BITS) > 0,
                "驱动必须提供 GPU timestamp 查询");
        for (String filter : FILTERS) {
            try (Scene scene = new Scene(filter, 1920, 1080, GL_RGBA8, null)) {
                for (int frame = 0; frame < warmupFrames; frame++) {
                    scene.resetMain();
                    scene.draw(1.0F);
                    scene.copyToMain();
                }
                glFinish();
                int[] queries = new int[sampleFrames * 2];
                glGenQueries(queries);
                try {
                    for (int frame = 0; frame < sampleFrames; frame++) {
                        // 模拟每帧新画面；场景清屏不计入滤镜耗时，避免反复滤同一结果。
                        scene.resetMain();
                        glQueryCounter(queries[frame * 2], GL_TIMESTAMP);
                        scene.draw(1.0F);
                        scene.copyToMain();
                        glQueryCounter(queries[frame * 2 + 1], GL_TIMESTAMP);
                    }
                    double[] millis = new double[sampleFrames];
                    for (int frame = 0; frame < sampleFrames; frame++) {
                        long start = glGetQueryObjectui64(queries[frame * 2], GL_QUERY_RESULT);
                        long end = glGetQueryObjectui64(queries[frame * 2 + 1], GL_QUERY_RESULT);
                        assertTrue(end >= start, "GPU 时间戳应单调递增");
                        millis[frame] = (end - start) / 1_000_000.0;
                    }
                    Arrays.sort(millis);
                    System.out.printf(Locale.ROOT,
                            "PRISMOD_GPU_PROFILE filter=%s resolution=1920x1080 format=RGBA8 warmup=%d samples=%d "
                                    + "p50_ms=%.4f p95_ms=%.4f max_ms=%.4f scope=shader_and_color_copy%n",
                            filter, warmupFrames, sampleFrames, millis[149], millis[284], millis[299]);
                    assertNoGlError(filter + " GPU 时间采样");
                } finally {
                    glDeleteQueries(queries);
                }
            }
            assertNoGlError(filter + " 计时资源释放");
        }
    }

    private static void assertNoGlError(String phase) {
        assertEquals(GL_NO_ERROR, glGetError(), phase + " 出现 OpenGL 错误");
    }

    private static final class Scene implements AutoCloseable {
        private final int width;
        private final int height;
        private int program;
        private int vao;
        private int vbo;
        private int intensity;
        private Target main;
        private Target swap;

        Scene(String filter, int width, int height, int internalFormat, float[] input) throws IOException {
            this.width = width;
            this.height = height;
            try {
                program = linkProgram(filter);
                main = new Target(width, height, internalFormat, input);
                swap = new Target(width, height, internalFormat, null);
                vao = glGenVertexArrays();
                glBindVertexArray(vao);
                vbo = glGenBuffers();
                glBindBuffer(GL_ARRAY_BUFFER, vbo);
                glBufferData(GL_ARRAY_BUFFER, new float[]{
                        0, 0, 0, 1, width, 0, 0, 1, 0, height, 0, 1, width, height, 0, 1
                }, GL_STATIC_DRAW);
                int position = glGetAttribLocation(program, "Position");
                assertTrue(position >= 0, "Position 顶点属性应存在");
                glEnableVertexAttribArray(position);
                glVertexAttribPointer(position, 4, GL_FLOAT, false, 4 * Float.BYTES, 0L);
                glUseProgram(program);
                glUniformMatrix4fv(uniform("ProjMat"), false, new float[]{
                        2.0F / width, 0, 0, 0, 0, 2.0F / height, 0, 0,
                        0, 0, -1, 0, -1, -1, 0, 1
                });
                glUniform2f(uniform("OutSize"), width, height);
                glUniform2f(uniform("ScreenSize"), width, height);
                glUniform1i(uniform("DiffuseSampler"), 0);
                intensity = uniform("Intensity");
                assertNoGlError(filter + " 初始化");
            } catch (IOException | RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private int uniform(String name) {
            int location = glGetUniformLocation(program, name);
            assertTrue(location >= 0, "着色器必须暴露 uniform: " + name);
            return location;
        }

        void draw(float strength) {
            glBindFramebuffer(GL_FRAMEBUFFER, swap.framebuffer);
            glViewport(0, 0, width, height);
            glUseProgram(program);
            glBindVertexArray(vao);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, main.texture);
            glUniform1f(intensity, strength);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        }

        float[] renderAndRead(float strength) {
            draw(strength);
            return read(swap);
        }

        void copyToMain() {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, swap.framebuffer);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, main.framebuffer);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        }

        void resetMain() {
            glBindFramebuffer(GL_FRAMEBUFFER, main.framebuffer);
            glClearColor(.2F, .35F, .5F, 1.0F);
            glClear(GL_COLOR_BUFFER_BIT);
        }

        float[] readMain() {
            return read(main);
        }

        private float[] read(Target target) {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, target.framebuffer);
            float[] pixels = new float[width * height * 4];
            glReadPixels(0, 0, width, height, GL_RGBA, GL_FLOAT, pixels);
            return pixels;
        }

        @Override
        public void close() {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glUseProgram(0);
            glBindTexture(GL_TEXTURE_2D, 0);
            if (vbo != 0) glDeleteBuffers(vbo);
            if (vao != 0) glDeleteVertexArrays(vao);
            if (main != null) main.close();
            if (swap != null) swap.close();
            if (program != 0) glDeleteProgram(program);
        }
    }

    private static int linkProgram(String filter) throws IOException {
        int vertex = 0;
        int fragment = 0;
        int program = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, resource("program/fullscreen.vsh"));
            fragment = compile(GL_FRAGMENT_SHADER, resource("program/" + filter + ".fsh"));
            program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glBindFragDataLocation(program, 0, "fragColor");
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS),
                    filter + " 链接失败: " + glGetProgramInfoLog(program));
            return program;
        } catch (IOException | RuntimeException | Error failure) {
            if (program != 0) glDeleteProgram(program);
            throw failure;
        } finally {
            if (vertex != 0) glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            fail("真实驱动编译着色器失败: " + log);
        }
        return shader;
    }

    private static final String[] FILTERS = {"grayscale", "warm", "cool", "vintage", "night_vision"};

    private static String resource(String name) throws IOException {
        String path = "/assets/prismod/shaders/" + name;
        try (InputStream input = FilterShaderGlTest.class.getResourceAsStream(path)) {
            assertNotNull(input, "缺少资源: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class Target implements AutoCloseable {
        private int texture;
        private int framebuffer;

        Target(int width, int height, int internalFormat, float[] pixels) {
            try {
                texture = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                if (pixels == null) {
                    glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                            GL_RGBA, GL_FLOAT, (FloatBuffer) null);
                } else {
                    glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL_RGBA, GL_FLOAT, pixels);
                }
                framebuffer = glGenFramebuffers();
                glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
                assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER),
                        "离屏 framebuffer 必须完整");
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        @Override
        public void close() {
            if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
            if (texture != 0) glDeleteTextures(texture);
        }
    }
}
