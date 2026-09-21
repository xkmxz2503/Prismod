package com.xkmxz.prismod.client.ui;

import com.google.gson.JsonObject;
import com.xkmxz.prismod.client.pack.ResourcePackEditorDraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Structured editor for one filter.json and its manifest entry. */
public final class ResourcePackFilterScreen extends Screen {
    private final Screen parent;
    private final ResourcePackEditorDraft draft;
    private String filterId;
    private EditBox idBox;
    private EditBox displayNameBox;
    private EditBox sourceBox;
    private EditBox strengthBox;
    private EditBox previewBox;
    private EditBox colorSpaceBox;
    private Button typeButton;
    private String type;
    private String status;
    private int layoutLeft;
    private int layoutRight;
    private int layoutColumn;
    private int layoutTop;
    private int actionTop;

    public ResourcePackFilterScreen(Screen parent, ResourcePackEditorDraft draft, String filterId) {
        super(Component.translatable("screen.prismod.filter_editor_title"));
        this.parent = parent;
        this.draft = draft;
        this.filterId = filterId;
    }

    @Override
    protected void init() {
        clearWidgets();
        int margin = Math.min(24, Math.max(12, width / 24));
        int gap = Math.min(12, Math.max(6, width / 60));
        int contentWidth = Math.max(240, width - margin * 2);
        int column = Math.max(120, (contentWidth - gap) / 2);
        int left = margin;
        int right = left + column + gap;
        int top = 18;
        layoutLeft = left;
        layoutRight = right;
        layoutColumn = column;
        layoutTop = top;
        actionTop = Math.max(top + 166, height - 52);
        JsonObject filter = draft.filterManifest(filterId);
        type = value(filter, "type", "post_chain");
        idBox = field(left, top + 24, column, "screen.prismod.filter_id", filterId);
        displayNameBox = field(left, top + 60, column, "screen.prismod.filter_display_name", value(filter, "display_name", ""));
        sourceBox = field(left, top + 96, column, "screen.prismod.filter_source", value(filter, "source", ""));
        strengthBox = field(left, top + 132, column, "screen.prismod.filter_default_strength", value(filter, "default_strength", "1.0"));
        previewBox = field(right, top + 24, column, "screen.prismod.filter_preview_file", value(filter, "preview", ""));
        colorSpaceBox = field(right, top + 60, column, "screen.prismod.filter_color_space", value(filter, "color_space", "sRGB"));
        typeButton = addRenderableWidget(Button.builder(typeLabel(), button -> {
                    type = "lut3d".equals(type) ? "post_chain" : "lut3d";
                    button.setMessage(typeLabel());
                }).bounds(right, top + 96, column, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.save_filter"), button -> saveFilter())
                .bounds(left, actionTop, column, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.delete_filter"), button -> deleteFilter())
                .bounds(right, actionTop, column, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(right, actionTop + 24, column, 20).build());
    }

    private EditBox field(int x, int y, int width, String label, String value) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, width, 20, Component.translatable(label)));
        box.setValue(value);
        return box;
    }

    private Component typeLabel() {
        return Component.translatable("screen.prismod.filter_type", type);
    }

    private static String value(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    private void saveFilter() {
        String newId = idBox.getValue().trim();
        if (newId.isBlank() || !net.minecraft.resources.ResourceLocation.isValidPath(newId)) {
            status = Component.translatable("screen.prismod.filter_rename_invalid").getString();
            return;
        }
        if (!newId.equals(filterId) && draft.filters().stream().anyMatch(filter -> filter.id().equals(newId))) {
            status = Component.translatable("screen.prismod.filter_rename_conflict").getString();
            return;
        }
        try {
            Float.parseFloat(strengthBox.getValue().trim());
            if (!newId.equals(filterId)) {
                draft.renameFilter(filterId, newId);
                filterId = newId;
            }
            JsonObject filter = draft.filterManifest(filterId);
            filter.addProperty("schema", "prismod.filter");
            filter.addProperty("format_version", 1);
            filter.addProperty("type", type);
            filter.addProperty("display_name", displayNameBox.getValue().trim());
            filter.addProperty("source", sourceBox.getValue().trim());
            filter.addProperty("default_strength", Float.parseFloat(strengthBox.getValue().trim()));
            if (previewBox.getValue().isBlank()) filter.remove("preview");
            else filter.addProperty("preview", previewBox.getValue().trim());
            if ("lut3d".equals(type)) filter.addProperty("color_space", colorSpaceBox.getValue().trim());
            else filter.remove("color_space");
            draft.writeFilterManifest(filterId, filter);
            status = Component.translatable("screen.prismod.filter_saved").getString();
        } catch (Exception exception) {
            status = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    private void deleteFilter() {
        draft.deleteFilter(filterId);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.prismod.filter_id"), layoutLeft, layoutTop + 12, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.filter_display_name"), layoutLeft, layoutTop + 48, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.filter_source"), layoutLeft, layoutTop + 84, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.filter_default_strength"), layoutLeft, layoutTop + 120, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.filter_preview_file"), layoutRight, layoutTop + 12, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.filter_color_space"), layoutRight, layoutTop + 48, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.filter_type_label"), layoutRight, layoutTop + 84, 0xBBBBBB);
        if (status != null) graphics.drawString(font, limitedStatus(width - layoutLeft * 2), layoutLeft,
                Math.max(8, actionTop - 16), 0xFFCC66);
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
