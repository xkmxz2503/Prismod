package com.xkmxz.prismod.mixin.client;

import com.xkmxz.prismod.client.render.WorldFilterRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    // 1.20.1 中此调用仅位于世界分支，原版后处理之后、GUI 投影和 HUD 之前。
    @Inject(method = "render(FJZ)V", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", ordinal = 0,
            shift = At.Shift.AFTER))
    private void prismod$filterWorld(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        WorldFilterRenderer.render(partialTick);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void prismod$close(CallbackInfo ci) {
        WorldFilterRenderer.close();
    }
}
