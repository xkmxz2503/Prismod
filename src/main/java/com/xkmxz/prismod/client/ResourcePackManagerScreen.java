package com.xkmxz.prismod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.swing.JFileChooser;
import java.awt.HeadlessException;
import java.nio.file.Path;
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
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.import"), button -> importPack())
                .bounds(panelLeft, panelTop + 18, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
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
        for (PrismodPackLoader.PackCandidate candidate : candidates) {
            String namespace = candidate.metadata().namespace();
            boolean enabled = draftPackEnabled.getOrDefault(namespace, true);
            boolean locked = namespace.equals(currentPackNamespace());
            Component label = Component.translatable("screen.prismod.pack_row",
                    candidate.metadata().displayName(candidate.fileName()), namespace,
                    Component.translatable(enabled ? "options.on" : "options.off"));
            Button row = addRenderableWidget(Button.builder(label, button -> {
                if (!locked) {
                    draftPackEnabled.put(namespace, !draftPackEnabled.getOrDefault(namespace, true));
                    rebuildRows();
                }
            }).bounds(panelLeft, listTop, panelWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable(locked
                            ? "screen.prismod.pack_locked" : "screen.prismod.pack_toggle", namespace)))
                    .build());
            row.active = !locked;
            rows.add(new Row(row, !locked));
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

    private void importPack() {
        try {
            JFileChooser chooser = new JFileChooser(PrismodPackLoader.resourcePacksDirectory().toFile());
            chooser.setDialogTitle(Component.translatable("screen.prismod.import").getString());
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            chooser.setMultiSelectionEnabled(false);
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
            Path selected = chooser.getSelectedFile().toPath();
            PrismodPackLoader.ImportResult result = PrismodPackLoader.importPack(selected);
            status = result.message();
            if (result.success()) {
                refreshCandidates();
                rebuildRows();
            }
        } catch (HeadlessException exception) {
            status = Component.translatable("screen.prismod.import_unavailable").getString();
        }
    }

    private void save() {
        List<String> disabled = draftPackEnabled.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .toList();
        PrismodClientConfig.setDisabledPacks(disabled);
        PrismodClientConfig.SPEC.save();
        PrismodClient.reloadResources();
        if (minecraft != null && parent instanceof FilterConfigScreen filterScreen) {
            minecraft.setScreen(new FilterConfigScreen(filterScreen.parentScreen()));
        } else {
            onClose();
        }
    }

    private void arrangeRows() {
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            AbstractWidget widget = row.button();
            int y = listTop + index * ROW_HEIGHT - scrollOffset;
            boolean visible = y + ROW_HEIGHT > listTop && y < listBottom;
            widget.setY(y);
            widget.visible = visible;
            widget.active = visible && row.enabled();
            widget.setTabOrderGroup(index + 1);
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
        if (status != null) graphics.drawString(font, Component.literal(status), panelLeft, listBottom + 2, 0xFFCC66);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private record Row(Button button, boolean enabled) {
    }
}
