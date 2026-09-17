package com.xkmxz.prismod.mixin.client;

import com.mojang.blaze3d.shaders.BlendMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** BlendMode 缓存与 GL 状态必须一起恢复，防止 HUD 暗角的乘法混合被覆盖。 */
@Mixin(BlendMode.class)
public interface BlendModeAccessor {
    @Accessor("lastApplied")
    static BlendMode prismod$getLastApplied() {
        throw new AssertionError("Mixin 尚未应用");
    }

    @Accessor("lastApplied")
    static void prismod$setLastApplied(BlendMode mode) {
        throw new AssertionError("Mixin 尚未应用");
    }
}
