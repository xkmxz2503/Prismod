package com.xkmxz.prismod.client.ui;

import com.google.gson.JsonParser;
import com.xkmxz.prismod.client.pack.ResourcePackEditorDraft;
import com.xkmxz.prismod.client.pack.ResourcePackEditorService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wizard for creating one runnable filter in an editor draft. */
public final class ResourcePackFilterCreateScreen extends Screen {
    private final Screen parent;
    private final ResourcePackEditorDraft draft;
    private EditBox idBox;
    private EditBox strengthBox;
    private Button typeButton;
    private String type = "post_chain";
    private final List<String> languages = new ArrayList<>();
    private final Map<String, EditBox> nameBoxes = new LinkedHashMap<>();
    private Map<String, String> importedFiles = Map.of();
    private String status;
    private int actionTop;

    public ResourcePackFilterCreateScreen(Screen parent, ResourcePackEditorDraft draft) {
        this(parent, draft, "post_chain");
    }

    public ResourcePackFilterCreateScreen(Screen parent, ResourcePackEditorDraft draft, String initialType) {
        super(Component.translatable("screen.prismod.filter_create_title"));
        this.parent = parent;
        this.draft = draft;
        if ("lut3d".equalsIgnoreCase(initialType)) type = "lut3d";
        loadLanguages();
    }

    @Override
    protected void init() {
        clearWidgets();
        nameBoxes.clear();
        int left = 24;
        int column = Math.max(180, Math.min(300, (width - 72) / 2));
        int right = left + column + 24;
        int top = 26;
        idBox = field(left, top + 20, column, "screen.prismod.filter_id", suggestedId());
        strengthBox = field(left, top + 62, column, "screen.prismod.filter_default_strength", "1.0");
        typeButton = addRenderableWidget(Button.builder(typeLabel(), button -> {
            type = "lut3d".equals(type) ? "post_chain" : "lut3d";
            button.setMessage(typeLabel());
        }).bounds(left, top + 104, column, 20).build());

        int index = 0;
        for (String language : languages) {
            EditBox box = field(right, top + 20 + index * 36, column, "screen.prismod.filter_language_name", "");
            box.setHint(Component.literal(language));
            nameBoxes.put(language, box);
            index++;
        }
        int importTop = top + 140;
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.filter_import"), ignored ->
                status = Component.translatable("screen.prismod.filter_import_hint").getString())
                .bounds(left, importTop, column, 20).build());
        actionTop = Math.max(importTop + 24, height - 48);
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.filter_add_to_draft"), ignored -> addToDraft())
                .bounds(left, actionTop, column, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
                .bounds(right, actionTop, column, 20).build());
    }

    private EditBox field(int x, int y, int width, String label, String value) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, width, 20, Component.translatable(label)));
        box.setValue(value);
        return box;
    }

    private void loadLanguages() {
        languages.clear();
        String prefix = "assets/" + draft.namespace() + "/lang/";
        languages.addAll(draft.files().keySet().stream()
                .filter(path -> path.startsWith(prefix) && path.endsWith(".json"))
                .map(path -> path.substring(prefix.length(), path.length() - 5))
                .sorted().toList());
        if (languages.isEmpty()) {
            String selected = "en_us";
            try {
                if (Minecraft.getInstance() != null && Minecraft.getInstance().getLanguageManager() != null) {
                    selected = Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(java.util.Locale.ROOT);
                }
            } catch (RuntimeException ignored) { }
            languages.add(selected);
            if (!languages.contains("en_us")) languages.add("en_us");
        }
    }

    private String suggestedId() {
        String id = "new_filter";
        int suffix = 2;
        while (containsFilter(id)) id = "new_filter_" + suffix++;
        return id;
    }

    private boolean containsFilter(String id) {
        return draft.filters().stream().anyMatch(filter -> filter.id().equals(id));
    }

    private Component typeLabel() {
        return Component.translatable("screen.prismod.filter_type", type);
    }

    private void addToDraft() {
        String id = idBox.getValue().trim();
        if (!net.minecraft.resources.ResourceLocation.isValidPath(id)) {
            status = Component.translatable("screen.prismod.filter_create_invalid_id").getString();
            return;
        }
        float strength;
        try { strength = Float.parseFloat(strengthBox.getValue().trim()); }
        catch (NumberFormatException exception) {
            status = Component.translatable("screen.prismod.filter_create_invalid_strength").getString();
            return;
        }
        Map<String, String> names = new LinkedHashMap<>();
        boolean hasName = false;
        for (Map.Entry<String, EditBox> entry : nameBoxes.entrySet()) {
            String value = entry.getValue().getValue().trim();
            names.put(entry.getKey(), value);
            hasName |= !value.isBlank();
        }
        if (!hasName) {
            status = Component.translatable("screen.prismod.filter_create_name_required").getString();
            return;
        }
        try {
            ResourcePackEditorService.FilterCreateRequest request =
                    new ResourcePackEditorService.FilterCreateRequest(id, type, strength, names, importedFiles);
            ResourcePackEditorService.FilterCreation creation = draft.addFilter(request);
            if (parent instanceof ResourcePackEditorScreen editor) editor.filterCreated(creation.id());
            if (minecraft != null) minecraft.setScreen(parent);
        } catch (Exception exception) {
            status = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        try {
            importedFiles = ResourcePackEditorService.readImportedFiles(paths);
            String manifest = importedFiles.get("filter.json");
            if (manifest != null) {
                try {
                    String importedType = JsonParser.parseString(manifest).getAsJsonObject().get("type").getAsString();
                    if ("lut3d".equalsIgnoreCase(importedType) || "post_chain".equalsIgnoreCase(importedType)) type = importedType.toLowerCase(java.util.Locale.ROOT);
                    if (typeButton != null) typeButton.setMessage(typeLabel());
                } catch (RuntimeException ignored) { }
            }
            status = Component.translatable("screen.prismod.filter_imported", importedFiles.size()).getString();
        } catch (IOException exception) {
            status = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 7, 0xFFFFFF);
        int left = 24;
        int right = left + Math.max(180, Math.min(300, (width - 72) / 2)) + 24;
        graphics.drawString(font, Component.translatable("screen.prismod.filter_id"), left, 36, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.filter_default_strength"), left, 78, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.filter_type_label"), left, 120, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.filter_language_names"), right, 36, 0xBBBBBB);
        int index = 0;
        for (String language : languages) {
            graphics.drawString(font, Component.literal(language), right, 42 + index * 36, 0x888888);
            index++;
        }
        graphics.drawString(font, Component.translatable("screen.prismod.filter_imported_files", importedFiles.size()), right, 166, 0xAAAAAA);
        int fileIndex = 0;
        for (String file : importedFiles.keySet()) {
            if (fileIndex >= 3) break;
            graphics.drawString(font, Component.literal("- " + file), right + 8, 180 + fileIndex * 12, 0x888888);
            fileIndex++;
        }
        if (status != null) graphics.drawString(font, limitedStatus(width - 48), left, actionTop - 18, 0xFFCC66);
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
