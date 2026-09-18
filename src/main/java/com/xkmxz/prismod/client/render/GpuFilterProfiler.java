package com.xkmxz.prismod.client.render;

import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.client.filter.FilterKey;
import net.minecraftforge.fml.ModList;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.Arrays;

/** 可选诊断：异步时间戳查询，不等待 GPU，不与 Oculus 的 elapsed query 嵌套。 */
final class GpuFilterProfiler {
    private final boolean requested = Boolean.getBoolean("prismod.profileGpu");
    private final int[] starts = new int[8];
    private final int[] ends = new int[8];
    private final boolean[] pending = new boolean[8];
    private final double[] samples = new double[300];
    private int warmup;
    private int count;
    private int active = -1;
    private int slot;
    private FilterKey filter;
    private int width;
    private int height;

    void begin(FilterKey id, int newWidth, int newHeight) {
        if (!requested || !GL.getCapabilities().OpenGL33) return;
        if (id != filter || width != newWidth || height != newHeight) {
            close();
            filter = id;
            width = newWidth;
            height = newHeight;
        }
        if (warmup++ < 60 || count == samples.length) return;
        for (int i = 0; i < pending.length && count < samples.length; i++) {
            if (pending[i] && GL15.glGetQueryObjecti(ends[i], GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_TRUE) {
                long start = GL33.glGetQueryObjectui64(starts[i], GL15.GL_QUERY_RESULT);
                long end = GL33.glGetQueryObjectui64(ends[i], GL15.GL_QUERY_RESULT);
                samples[count++] = (end - start) / 1_000_000.0;
                pending[i] = false;
            }
        }
        if (count == samples.length) {
            double[] sorted = samples.clone();
            Arrays.sort(sorted);
            double p95 = sorted[(int) Math.ceil(sorted.length * 0.95) - 1];
            LogUtils.getLogger().info("Prismod GPU: filter={}, size={}x{}, oculusInstalled={}, samples=300, P95={}ms, within1ms={}",
                    filter, width, height, ModList.get().isLoaded("oculus"), p95, p95 <= 1.0);
            return;
        }
        slot = (slot + 1) % pending.length;
        if (pending[slot]) return;
        if (starts[slot] == 0) {
            starts[slot] = GL15.glGenQueries();
            ends[slot] = GL15.glGenQueries();
        }
        active = slot;
        GL33.glQueryCounter(starts[active], GL33.GL_TIMESTAMP);
    }

    void end() {
        if (active < 0) return;
        GL33.glQueryCounter(ends[active], GL33.GL_TIMESTAMP);
        pending[active] = true;
        active = -1;
    }

    void close() {
        for (int i = 0; i < starts.length; i++) {
            if (starts[i] != 0) GL15.glDeleteQueries(starts[i]);
            if (ends[i] != 0) GL15.glDeleteQueries(ends[i]);
            starts[i] = ends[i] = 0;
            pending[i] = false;
        }
        filter = null;
        warmup = count = slot = 0;
        active = -1;
    }
}
