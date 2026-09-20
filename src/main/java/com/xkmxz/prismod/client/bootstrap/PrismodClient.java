package com.xkmxz.prismod.client.bootstrap;

import com.mojang.blaze3d.platform.InputConstants;
import com.xkmxz.prismod.client.filter.FilterManager;
import com.xkmxz.prismod.client.filter.FilterRegistry;
import com.xkmxz.prismod.client.render.WorldFilterRenderer;
import com.xkmxz.prismod.client.config.PrismodClientConfig;
import com.xkmxz.prismod.client.pack.PrismodPackLoader;
import com.xkmxz.prismod.client.ui.FilterConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

/** 只由 DistExecutor 的客户端分支创建，专用服务器不会解析此类。 */
public final class PrismodClient {
    private static final KeyMapping CYCLE = new KeyMapping("key.prismod.cycle", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.prismod");
    private static boolean conflictChecked;
    private static boolean hadWorld;

    private PrismodClient() { }

    public static void register() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(PrismodClient::registerKeys);
        bus.addListener(PrismodClient::registerReload);
        bus.addListener(PrismodClient::configChanged);
        PrismodPackLoader.ensureDirectories();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PrismodClientConfig.SPEC,
                PrismodPackLoader.CONFIG_FILE);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new FilterConfigScreen(parent)));
        MinecraftForge.EVENT_BUS.addListener(PrismodClient::tick);
    }

    public static CompletableFuture<Void> reloadPrismodResources() {
        Minecraft minecraft = Minecraft.getInstance();
        reloadPrismodResources(minecraft.getResourceManager());
        return CompletableFuture.completedFuture(null);
    }

    public static void reloadPrismodResources(net.minecraft.server.packs.resources.ResourceManager vanilla) {
        WorldFilterRenderer.reload();
        PrismodPackLoader.PrismodResourceManager resources = PrismodPackLoader.reload(vanilla);
        FilterRegistry.get().reload(resources);
        Minecraft.getInstance().getLanguageManager().onResourceManagerReload(resources);
        PrismodClientConfig.appendDiscoveredFilters();
        PrismodClientConfig.removeHiddenAndUnavailableFromCycleOrder();
        FilterManager.get().refreshConfig();
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(CYCLE);
    }

    private static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            reloadPrismodResources(manager);
        });
    }

    private static void configChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() != PrismodClientConfig.SPEC || event instanceof ModConfigEvent.Unloading) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(() -> reloadPrismodResources(mc.getResourceManager()));
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.level != null && mc.player != null;
        if (hadWorld && !inWorld) {
            FilterManager.get().resetSession();
            WorldFilterRenderer.close();
        }
        if (inWorld && !hadWorld) FilterManager.get().refreshConfig();
        hadWorld = inWorld;
        if (inWorld && !conflictChecked) {
            conflictChecked = true;
            if (!CYCLE.isUnbound()) {
                for (KeyMapping other : mc.options.keyMappings) {
                    if (other != CYCLE && CYCLE.same(other)) {
                        mc.player.displayClientMessage(Component.translatable("message.prismod.key_conflict", CYCLE.getTranslatedKeyMessage()), false);
                        break;
                    }
                }
            }
        }
        while (CYCLE.consumeClick()) {
            if (inWorld && mc.screen == null && !FilterManager.get().isForced()) {
                FilterManager.get().cycle();
                mc.player.displayClientMessage(Component.translatable("message.prismod.selected",
                        FilterManager.get().effectiveDisplayName()), true);
            }
        }
    }
}
