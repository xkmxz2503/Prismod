package com.xkmxz.prismod.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.client.filter.*;
import com.xkmxz.prismod.mixin.client.PostChainAccessor;
import com.xkmxz.prismod.mixin.client.BlendModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

/** 拥有独立链，不占用原版的旁观者 postEffect 槽位。仅从渲染线程调用。 */
public final class WorldFilterRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static PostChain chain;
    private static RenderTarget source;
    private static Uniform intensity;
    private static FilterKey loaded;
    private static int width;
    private static int height;
    private static final GpuFilterProfiler PROFILER = new GpuFilterProfiler();

    private WorldFilterRenderer() { }

    public static void render(float partialTick) {
        RenderSystem.assertOnRenderThread();
        Minecraft mc = Minecraft.getInstance();
        FilterSelection selection = FilterManager.get().effectiveSelection();
        FilterKey key = selection.key();
        if (mc.level == null || key.isOriginal() || selection.strength() <= 0) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0) return;

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        BlendMode previousBlendMode = BlendModeAccessor.prismod$getLastApplied();
        // 隔离调用方遗留的错误；本次 process/blit 的错误必须独立检查。
        while (GL11.glGetError() != GL11.GL_NO_ERROR) { }
        try {
            prepare(mc, main, key);
            intensity.set(selection.strength());
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.resetTextureMatrix();
            PROFILER.begin(key, main.width, main.height);
            // main -> swap。效果成功前绝不清空主目标，错误时仍能呈现原始世界。
            chain.process(partialTick);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("Prismod shader GL error: " + error);
            }
            RenderTarget swap = chain.getTempTarget("swap");
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, swap.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("Prismod color copy GL error: " + error);
            }
        } catch (Exception exception) {
            LOGGER.error("Prismod filter {} failed; falling back to the original view", key.serializedName(), exception);
            FilterManager.get().reportFilterFailure(key, exception);
            releaseChain();
        } finally {
            PROFILER.end();
            // PostPass 改变的 GL 状态必须恢复，避免影响 HUD 和其他模组。
            main.bindWrite(true);
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.depthMask(depthMask);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            BlendModeAccessor.prismod$setLastApplied(previousBlendMode);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        }
    }

    private static void prepare(Minecraft mc, RenderTarget main, FilterKey key) throws Exception {
        FilterDefinition definition = FilterRegistry.get().definition(key);
        if (definition == null || definition.postEffect() == null) {
            throw new IllegalStateException("No post effect registered for " + key.serializedName());
        }
        if (chain == null || source != main || loaded != key) {
            releaseChain();
            // 先取得空链的所有权，再加载可失败的资源，确保已创建的 FBO 能在 catch 中释放。
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main,
                    ResourceLocation.fromNamespaceAndPath("prismod", "shaders/post/empty.json"));
            ((PostChainAccessor) chain).prismod$load(mc.getTextureManager(), definition.postEffect());
            if (((PostChainAccessor) chain).prismod$getPasses().size() != 1) {
                throw new IllegalStateException("Prismod requires exactly one filter pass");
            }
            PostPass pass = ((PostChainAccessor) chain).prismod$getPasses().get(0);
            if (pass.inTarget != main || pass.outTarget != chain.getTempTarget("swap")) {
                throw new IllegalStateException("Prismod pass must render main to swap");
            }
            if (GL20.glGetProgrami(pass.getEffect().getId(), GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Prismod shader link failed");
            }
            intensity = pass.getEffect().getUniform("Intensity");
            if (intensity == null) throw new IllegalStateException("Missing Intensity uniform");
            source = main;
            loaded = key;
            width = height = -1;
        }
        if (width != main.width || height != main.height) {
            chain.resize(main.width, main.height);
            chain.getTempTarget("swap").bindWrite(false);
            chain.getTempTarget("swap").checkStatus();
            main.bindWrite(true);
            width = main.width;
            height = main.height;
        }
    }

    public static void reload() {
        RenderSystem.assertOnRenderThread();
        releaseChain();
        PROFILER.close();
        FilterManager.get().setRenderAvailable(true);
    }

    public static void close() {
        RenderSystem.assertOnRenderThread();
        releaseChain();
        PROFILER.close();
    }

    private static void releaseChain() {
        if (chain != null) {
            try { chain.close(); } catch (Exception e) { LOGGER.warn("释放 Prismod 渲染资源失败", e); }
        }
        chain = null;
        source = null;
        loaded = null;
        intensity = null;
    }
}
