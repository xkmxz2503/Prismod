package com.xkmxz.prismod.client.ui;

import com.xkmxz.prismod.client.bootstrap.PrismodClient;
import com.xkmxz.prismod.client.filter.registry.FilterDefinition;
import com.xkmxz.prismod.client.filter.registry.FilterRegistry;
import com.xkmxz.prismod.client.filter.state.FilterManager;
import com.xkmxz.prismod.client.config.PrismodClientConfig;
import com.xkmxz.prismod.client.pack.PrismodPackLoader;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 独立的 Prismod 自定义资源包管理界面。 */
public final class ResourcePackManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private final Screen parent;
    private final Map<String, Boolean> draftPackEnabled = new LinkedHashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private List<PrismodPackLoader.PackCandidate> candidates = List.of();
    private String status;
    private int panelTop;
    private int panelLeft;
    private int panelWidth;
    private int listTop;
    private int listBottom;
    private int scrollOffset;

    public ResourcePackManagerScreen(Screen parent) {
        super(Component.translatable("screen.prismod.resource_packs_title"));
        this.parent = parent;
        refreshCandidates();
    }

    @Override
    protected void init() {
        clearWidgets();
        rows.clear();
        panelWidth = Math.max(1, Math.min(500, width - 16));
        panelLeft = (width - panelWidth) / 2;
        int panelHeight = Math.min(Math.max(240, height - 16), height - 16);
        panelTop = Math.max(8, (height - panelHeight) / 2);
        listTop = panelTop + 72;
        listBottom = panelTop + panelHeight - 44;

        int half = (panelWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.import"), button -> openPackDirectory())
                .bounds(panelLeft, panelTop + 18, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.create_placeholder"), button ->
                        status = Component.translatable("screen.prismod.create_coming_soon").getString())
                .bounds(panelLeft + half + 4, panelTop + 18, half, 20).build());

        buildRows();
        arrangeRows();

        Button save = addRenderableWidget(Button.builder(Component.translatable("screen.prismod.save"), button -> save())
                .bounds(panelLeft, listBottom + 18, half, 20).build());
        Button cancel = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(panelLeft + half + 4, listBottom + 18, half, 20).build());
        save.setTabOrderGroup(100);
        cancel.setTabOrderGroup(100);
    }

    private void refreshCandidates() {
        candidates = PrismodPackLoader.scan(PrismodPackLoader.resourcePacksDirectory());
        Set<String> namespaces = candidates.stream()
                .map(candidate -> candidate.metadata().namespace())
                .collect(java.util.stream.Collectors.toSet());
        draftPackEnabled.keySet().retainAll(namespaces);
        for (PrismodPackLoader.PackCandidate candidate : candidates) {
            draftPackEnabled.putIfAbsent(candidate.metadata().namespace(),
                    PrismodClientConfig.isPackEnabled(candidate.metadata().namespace()));
        }
    }

    private void buildRows() {
        int toggleWidth = Math.max(80, panelWidth - 86);
        for (PrismodPackLoader.PackCandidate candidate : candidates) {
            String namespace = candidate.metadata().namespace();
            boolean enabled = draftPackEnabled.getOrDefault(namespace, true);
            boolean locked = namespace.equals(currentPackNamespace());
            Component label = Component.translatable("screen.prismod.pack_row",
                    candidate.metadata().displayName(candidate.fileName()), namespace,
                    Component.translatable(enabled ? "options.on" : "options.off"));
            Button toggle = addRenderableWidget(Button.builder(label, button -> {
                if (!locked) {
                    draftPackEnabled.put(namespace, !draftPackEnabled.getOrDefault(namespace, true));
                    rebuildRows();
                }
            }).bounds(panelLeft, listTop, toggleWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable(locked
                            ? "screen.prismod.pack_locked" : "screen.prismod.pack_toggle", namespace)))
                    .build());
            toggle.active = !locked;
            Button edit = addRenderableWidget(Button.builder(Component.translatable("screen.prismod.edit"), button -> openEditor(candidate))
                    .bounds(panelLeft + toggleWidth + 4, listTop, panelWidth - toggleWidth - 4, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.prismod.edit_pack", namespace))).build());
            rows.add(new Row(toggle, edit, !locked));
        }

    }

    private void openEditor(PrismodPackLoader.PackCandidate candidate) {
        try {
            if (minecraft != null) minecraft.setScreen(new ResourcePackEditorScreen(this, candidate));
        } catch (IOException exception) {
            status = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    private void rebuildRows() {
        clearWidgets();
        rows.clear();
        init();
    }

    private String currentPackNamespace() {
        FilterDefinition definition = FilterRegistry.get().definition(FilterManager.get().effectiveSelection().key());
        return definition == null ? null : definition.packNamespace();
    }

    private void openPackDirectory() {
        try {
            PrismodPackLoader.ensureDirectories();
            Util.getPlatform().openFile(PrismodPackLoader.resourcePacksDirectory().toFile());
            refreshCandidates();
            rebuildRows();
            status = Component.translatable("screen.prismod.import_hint").getString();
        } catch (RuntimeException exception) {
            status = Component.translatable("screen.prismod.import_unavailable").getString();
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }

        int imported = 0;
        String lastFailure = null;
        for (Path path : paths) {
            PrismodPackLoader.ImportResult result = PrismodPackLoader.importPack(path);
            if (result.success()) {
                imported++;
            } else {
                lastFailure = result.message();
            }
        }

        if (imported > 0) {
            refreshCandidates();
            rebuildRows();
        }
        if (imported == paths.size()) {
            status = Component.translatable("screen.prismod.import_success", imported).getString();
        } else if (imported > 0) {
            status = Component.translatable("screen.prismod.import_partial", imported, paths.size(), lastFailure)
                    .getString();
        } else {
            status = lastFailure == null
                    ? Component.translatable("screen.prismod.import_failed").getString()
                    : lastFailure;
        }
    }

    private void save() {
        List<String> disabled = draftPackEnabled.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .toList();
        PrismodClientConfig.setDisabledPacks(disabled);
        PrismodClientConfig.SPEC.save();
        PrismodClient.reloadPrismodResources();
        if (minecraft != null && parent instanceof FilterConfigScreen filterScreen) {
            minecraft.setScreen(new FilterConfigScreen(filterScreen.parentScreen()));
        } else {
            onClose();
        }
    }

    private void arrangeRows() {
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            int y = listTop + index * ROW_HEIGHT - scrollOffset;
            boolean visible = y + ROW_HEIGHT > listTop && y < listBottom;
            row.toggle().setY(y);
            row.edit().setY(y);
            row.toggle().visible = visible;
            row.edit().visible = visible;
            row.toggle().active = visible && row.enabled();
            row.edit().active = visible;
            row.toggle().setTabOrderGroup(index + 1);
            row.edit().setTabOrderGroup(index + 1);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelLeft && mouseX <= panelLeft + panelWidth
                && mouseY >= listTop && mouseY <= listBottom) {
            int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (listBottom - listTop));
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta) * ROW_HEIGHT, 0, maxScroll);
            arrangeRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 2, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.prismod.resource_packs_hint"),
                panelLeft, panelTop + 48, 0xBBBBBB);
        if (status != null) graphics.drawString(font, limitedStatus(panelWidth), panelLeft, listBottom + 2, 0xFFCC66);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component limitedStatus(int maxWidth) {
        if (font.width(status) <= maxWidth) return Component.literal(status);
        int textWidth = Math.max(12, maxWidth - font.width("..."));
        return Component.literal(font.plainSubstrByWidth(status, textWidth) + "...");
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private record Row(Button toggle, Button edit, boolean enabled) {
    }
}
