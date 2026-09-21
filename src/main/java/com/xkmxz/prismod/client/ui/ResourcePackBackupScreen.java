package com.xkmxz.prismod.client.ui;

import com.xkmxz.prismod.client.pack.ResourcePackEditorService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** 独立管理资源包版本备份和滤镜删除备份。 */
public final class ResourcePackBackupScreen extends Screen {
    private enum Mode { VERSIONS, RECYCLE }

    private final Screen parent;
    private Mode mode = Mode.VERSIONS;
    private ResourcePackEditorService.BackupSnapshot snapshot =
            new ResourcePackEditorService.BackupSnapshot(List.of(), List.of(), List.of());
    private String status;
    private int left;
    private int top;
    private int panelWidth;
    private int listTop;
    private int scroll;

    public ResourcePackBackupScreen(Screen parent) {
        super(Component.translatable("screen.prismod.backup_manager_title"));
        this.parent = parent;
        refresh();
    }

    @Override
    protected void init() {
        clearWidgets();
        panelWidth = Math.min(720, Math.max(260, this.width - 32));
        left = (this.width - panelWidth) / 2;
        top = 18;
        listTop = top + 92;
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.backup_versions"), button -> setMode(Mode.VERSIONS))
                .bounds(left, top + 18, (panelWidth - 4) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.backup_recycle"), button -> setMode(Mode.RECYCLE))
                .bounds(left + (panelWidth + 4) / 2, top + 18, (panelWidth - 4) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(mode == Mode.VERSIONS
                        ? "screen.prismod.backup_clear_all_versions" : "screen.prismod.backup_clear_all_recycle"),
                        button -> confirmClearAll())
                .bounds(left, top + 48, panelWidth, 20).build());
        buildRows();
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left, height - 28, panelWidth, 20).build());
    }

    private void setMode(Mode next) {
        mode = next;
        scroll = 0;
        init();
    }

    private void refresh() {
        try {
            snapshot = ResourcePackEditorService.inspectBackups();
        } catch (IOException exception) {
            status = message(exception);
        }
    }

    private void buildRows() {
        List<ResourcePackEditorService.BackupEntry> entries = mode == Mode.VERSIONS
                ? snapshot.versionBackups() : snapshot.recycleBatches();
        for (int index = 0; index < entries.size(); index++) {
            ResourcePackEditorService.BackupEntry entry = entries.get(index);
            int y = listTop + index * 26 - scroll;
            String batch = entry.batchName() == null ? "" : entry.batchName();
            String label = Component.translatable("screen.prismod.backup_entry", entry.packName(), batch,
                    entry.fileCount(), formatBytes(entry.bytes()), formatTime(entry.timestamp())).getString();
            int actionWidth = 82;
            int packActionWidth = mode == Mode.RECYCLE ? 82 : 0;
            int labelWidth = panelWidth - actionWidth - packActionWidth - 12;
            Button item = addRenderableWidget(Button.builder(Component.literal(label), button -> confirmClearEntry(entry))
                    .bounds(left, y, labelWidth, 20).build());
            item.visible = y + 20 > listTop && y < height - 36;
            Button clear = addRenderableWidget(Button.builder(Component.translatable("screen.prismod.backup_clear_item"),
                            button -> confirmClearEntry(entry))
                    .bounds(left + labelWidth + 4, y, actionWidth, 20).build());
            clear.visible = item.visible;
            if (mode == Mode.RECYCLE) {
                Button clearPack = addRenderableWidget(Button.builder(Component.translatable("screen.prismod.backup_clear_pack"),
                                button -> confirmClearPack(entry.packName()))
                        .bounds(left + labelWidth + actionWidth + 8, y, packActionWidth, 20).build());
                clearPack.visible = item.visible;
            }
        }
    }

    private void confirmClearAll() {
        Component question = Component.translatable(mode == Mode.VERSIONS
                ? "screen.prismod.backup_confirm_all_versions" : "screen.prismod.backup_confirm_all_recycle");
        if (minecraft != null) minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                clearAll();
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("screen.prismod.backup_confirm_title"), question));
    }

    private void confirmClearEntry(ResourcePackEditorService.BackupEntry entry) {
        Component question = Component.translatable("screen.prismod.backup_confirm_item", entry.packName(),
                entry.batchName() == null ? "" : entry.batchName());
        if (minecraft != null) minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                clearEntry(entry);
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("screen.prismod.backup_confirm_title"), question));
    }

    private void confirmClearPack(String packName) {
        Component question = Component.translatable("screen.prismod.backup_confirm_pack", packName);
        if (minecraft != null) minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                clearPack(packName);
            } else {
                minecraft.setScreen(this);
            }
        }, Component.translatable("screen.prismod.backup_confirm_title"), question));
    }

    private void clearAll() {
        try {
            ResourcePackEditorService.CleanupResult result = mode == Mode.VERSIONS
                    ? ResourcePackEditorService.clearVersionBackups(null)
                    : ResourcePackEditorService.clearRecycleBackups(null, null);
            status = cleanupStatus(result);
            refresh();
            init();
            if (minecraft != null) minecraft.setScreen(this);
        } catch (IOException exception) {
            status = Component.translatable("screen.prismod.backup_clear_failed", message(exception)).getString();
            init();
            if (minecraft != null) minecraft.setScreen(this);
        }
    }

    private void clearEntry(ResourcePackEditorService.BackupEntry entry) {
        try {
            ResourcePackEditorService.CleanupResult result = mode == Mode.VERSIONS
                    ? ResourcePackEditorService.clearVersionBackups(entry.packName())
                    : ResourcePackEditorService.clearRecycleBackups(entry.packName(), entry.batchName());
            status = cleanupStatus(result);
            refresh();
            init();
            if (minecraft != null) minecraft.setScreen(this);
        } catch (IOException exception) {
            status = Component.translatable("screen.prismod.backup_clear_failed", message(exception)).getString();
            init();
            if (minecraft != null) minecraft.setScreen(this);
        }
    }

    private void clearPack(String packName) {
        try {
            ResourcePackEditorService.CleanupResult result = mode == Mode.VERSIONS
                    ? ResourcePackEditorService.clearVersionBackups(packName)
                    : ResourcePackEditorService.clearRecycleBackups(packName, null);
            status = cleanupStatus(result);
            refresh();
            init();
            if (minecraft != null) minecraft.setScreen(this);
        } catch (IOException exception) {
            status = Component.translatable("screen.prismod.backup_clear_failed", message(exception)).getString();
            init();
            if (minecraft != null) minecraft.setScreen(this);
        }
    }

    private static String cleanupStatus(ResourcePackEditorService.CleanupResult result) {
        if (result.versionBackups() == 0 && result.recycleBatches() == 0) {
            return Component.translatable("screen.prismod.backup_cleared_none").getString();
        }
        return Component.translatable("screen.prismod.backup_cleared", result.versionBackups(),
                result.recycleBatches()).getString();
    }

    private static String message(IOException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return Component.translatable("screen.prismod.backup_bytes", bytes).getString();
        if (bytes < 1024 * 1024) return Component.translatable("screen.prismod.backup_kilobytes", bytes / 1024).getString();
        return Component.translatable("screen.prismod.backup_megabytes", bytes / (1024 * 1024)).getString();
    }

    private static String formatTime(long timestamp) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(timestamp));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int count = mode == Mode.VERSIONS ? snapshot.versionBackups().size() : snapshot.recycleBatches().size();
        int max = Math.max(0, count * 26 - (height - listTop - 36));
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 26, 0, max);
        init();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, this.width / 2, top + 2, 0xFFFFFF);
        graphics.drawString(font, Component.translatable(mode == Mode.VERSIONS
                ? "screen.prismod.backup_versions_hint" : "screen.prismod.backup_recycle_hint"), left, top + 74, 0xBBBBBB);
        boolean empty = mode == Mode.VERSIONS ? snapshot.versionBackups().isEmpty() : snapshot.recycleBatches().isEmpty();
        if (empty) {
            graphics.drawString(font, Component.translatable(mode == Mode.VERSIONS
                    ? "screen.prismod.backup_empty_versions" : "screen.prismod.backup_empty_recycle"), left, listTop, 0xAAAAAA);
        }
        if (status != null) graphics.drawString(font, limitedStatus(panelWidth), left, height - 46, 0xFFCC66);
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
}
