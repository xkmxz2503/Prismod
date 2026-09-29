package com.xkmxz.prismod.command.test.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.xkmxz.prismod.api.client.FilterApi;
import com.xkmxz.prismod.api.client.FilterOverride;
import com.xkmxz.prismod.api.client.FilterSubscription;
import com.xkmxz.prismod.api.common.model.FilterDescriptor;
import com.xkmxz.prismod.api.client.FilterSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

/** Client-only commands that simulate another mod consuming Prismod's public API. */
public final class PrismodClientApiCommands {
    private static final String OWNER = "prismod-api-test-mod";
    private static FilterOverride testOverride;
    private static FilterSubscription subscription;

    private PrismodClientApiCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("prismod_client")
                .then(Commands.literal("api")
                        .then(Commands.literal("help").executes(context -> help()))
                        .then(Commands.literal("list").executes(context -> list()))
                        .then(Commands.literal("snapshot").executes(context -> snapshot()))
                        .then(Commands.literal("force")
                                .then(Commands.argument("filter", ResourceLocationArgument.id())
                                        .executes(context -> force(context, 1.0F))
                                        .then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                .executes(context -> force(context,
                                                        FloatArgumentType.getFloat(context, "strength"))))))
                        .then(Commands.literal("clear").executes(context -> clear()))
                        .then(Commands.literal("watch").executes(context -> watch()))
                        .then(Commands.literal("owner_clear").executes(context -> ownerClear()))));
    }

    private static int help() {
        message("§bPrismod 客户端 API 测试命令：");
        message("§f/prismod_client api list §7- 获取实时滤镜列表");
        message("§f/prismod_client api snapshot §7- 获取当前 API 快照");
        message("§f/prismod_client api force <namespace:path> [strength] §7- 创建测试覆盖");
        message("§f/prismod_client api clear §7- 关闭测试覆盖");
        message("§f/prismod_client api watch §7- 切换状态监听");
        message("§f/prismod_client api owner_clear §7- 按 owner 清理测试状态");
        return 1;
    }

    private static int list() {
        var filters = FilterApi.getAvailableFilters();
        message("§b当前实时滤镜列表（" + filters.size() + "）：");
        for (FilterDescriptor filter : filters) {
            String status = filter.available() ? "§a可用" : "§c不可用";
            message("§f- " + filter.id() + " §7[" + status + "§7] owner=" + filter.ownerId()
                    + " default=" + filter.defaultStrength());
        }
        return filters.size();
    }

    private static int snapshot() {
        FilterSnapshot snapshot = FilterApi.getSnapshot();
        message("§b当前滤镜：§f" + snapshot.filter() + " §7strength=" + snapshot.strength()
                + " forced=" + snapshot.forced() + " renderAvailable=" + snapshot.renderAvailable()
                + " fallback=" + snapshot.fallbackReason() + " generation=" + snapshot.generation());
        return 1;
    }

    private static int force(CommandContext<CommandSourceStack> context, float strength) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "filter");
        if (testOverride != null) testOverride.close();
        testOverride = FilterApi.createOverride(OWNER, id, strength, 100);
        message("§a已通过 FilterApi 创建测试覆盖：" + id + " strength=" + strength);
        return 1;
    }

    private static int clear() {
        if (testOverride == null) {
            message("§7没有活动的测试覆盖。");
            return 0;
        }
        testOverride.close();
        testOverride = null;
        message("§a已关闭测试覆盖，恢复其他覆盖或用户选择。");
        return 1;
    }

    private static int watch() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
            message("§7已关闭 API 状态监听。");
            return 1;
        }
        subscription = FilterApi.subscribe(snapshot -> message("§d[API监听] effective=" + snapshot.filter()
                + " strength=" + snapshot.strength() + " fallback=" + snapshot.fallbackReason()));
        message("§a已开启 API 状态监听。执行 force、clear 或配置/资源重载时会输出变化。");
        return 1;
    }

    private static int ownerClear() {
        int count = FilterApi.clearOverrides(OWNER);
        testOverride = null;
        message("§a已按 owner 清理测试覆盖，清理数量：" + count);
        return count;
    }

    private static void message(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(text), false);
    }
}
