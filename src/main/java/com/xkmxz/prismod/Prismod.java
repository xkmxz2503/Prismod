package com.xkmxz.prismod;

import com.mojang.logging.LogUtils;
import com.xkmxz.prismod.client.bootstrap.PrismodClient;
import com.xkmxz.prismod.server.PrismodServer;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Prismod.MODID)
public class Prismod {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "prismod";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // 示例物品、方块、创造模式标签和通用事件暂未启用；恢复示例见 PRISMOD_GUIDE_PROMPT.md。

    public Prismod() {
        PrismodServer.initialize();
        // 使用嵌套 lambda，避免专用服务端执行安全引用校验时解析客户端方法引用。
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PrismodClient.register());
        LOGGER.info("Prismod initialized");
    }
}
