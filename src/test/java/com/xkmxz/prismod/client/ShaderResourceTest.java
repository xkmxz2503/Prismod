package com.xkmxz.prismod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 无需启动游戏或加载 OpenGL 的资源接线检查。 */
class ShaderResourceTest {
    static final String[] FILTERS = {"grayscale", "warm", "cool", "vintage", "night_vision"};

    @ParameterizedTest(name = "{0} 的着色器与单次后处理资源接线")
    @ValueSource(strings = {"grayscale", "warm", "cool", "vintage", "night_vision"})
    void resourcesExposeTheExpectedInterfaceAndSinglePass(String filter) throws IOException {
        JsonObject program = json("program/" + filter + ".json");
        assertEquals("prismod:fullscreen", program.get("vertex").getAsString());
        assertEquals("prismod:" + filter, program.get("fragment").getAsString());
        assertFalse(resource("program/fullscreen.vsh").isBlank());
        assertFalse(resource("program/" + filter + ".fsh").isBlank());
        assertEquals("Position", program.getAsJsonArray("attributes").get(0).getAsString());
        assertEquals(1, program.getAsJsonArray("attributes").size());
        JsonArray samplers = program.getAsJsonArray("samplers");
        assertEquals(1, samplers.size());
        assertEquals("DiffuseSampler", samplers.get(0).getAsJsonObject().get("name").getAsString());

        Map<String, JsonObject> uniforms = new HashMap<>();
        for (JsonElement element : program.getAsJsonArray("uniforms")) {
            JsonObject uniform = element.getAsJsonObject();
            String name = uniform.get("name").getAsString();
            assertNull(uniforms.put(name, uniform), "uniform 不应重名: " + name);
        }
        assertEquals(Set.of("ProjMat", "OutSize", "ScreenSize", "Intensity"), uniforms.keySet());
        assertUniform(uniforms.get("ProjMat"), "matrix4x4", 16);
        assertUniform(uniforms.get("OutSize"), "float", 2);
        assertUniform(uniforms.get("ScreenSize"), "float", 2);
        assertUniform(uniforms.get("Intensity"), "float", 1);
        assertEquals(1.0F, uniforms.get("Intensity").getAsJsonArray("values").get(0).getAsFloat());

        JsonObject post = json("post/" + filter + ".json");
        assertEquals(1, post.getAsJsonArray("targets").size());
        assertEquals("swap", post.getAsJsonArray("targets").get(0).getAsString());
        JsonArray passes = post.getAsJsonArray("passes");
        assertEquals(1, passes.size(), "后处理只运行一次滤镜，颜色回拷由渲染器负责");
        JsonObject pass = passes.get(0).getAsJsonObject();
        assertEquals("prismod:" + filter, pass.get("name").getAsString());
        assertEquals("minecraft:main", pass.get("intarget").getAsString());
        assertEquals("swap", pass.get("outtarget").getAsString());
        JsonArray passUniforms = pass.getAsJsonArray("uniforms");
        assertEquals(1, passUniforms.size());
        JsonObject intensity = passUniforms.get(0).getAsJsonObject();
        assertEquals("Intensity", intensity.get("name").getAsString());
        assertEquals(1, intensity.getAsJsonArray("values").size());
        assertEquals(1.0F, intensity.getAsJsonArray("values").get(0).getAsFloat());
    }

    private static void assertUniform(JsonObject uniform, String type, int count) {
        assertEquals(type, uniform.get("type").getAsString());
        assertEquals(count, uniform.get("count").getAsInt());
        assertEquals(count, uniform.getAsJsonArray("values").size());
        for (JsonElement value : uniform.getAsJsonArray("values")) {
            assertTrue(Float.isFinite(value.getAsFloat()));
        }
    }

    private static JsonObject json(String name) throws IOException {
        return JsonParser.parseString(resource(name)).getAsJsonObject();
    }

    static String resource(String name) throws IOException {
        String path = "/assets/prismod/shaders/" + name;
        try (InputStream input = ShaderResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(input, "缺少资源: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
