package com.xkmxz.prismod.client.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xkmxz.prismod.client.pack.ResourcePackEditorDraft;
import com.xkmxz.prismod.client.pack.PrismodPackLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Language-specific filter display-name editor. */
public final class ResourcePackLanguageScreen extends Screen {
    private final Screen parent;
    private final ResourcePackEditorDraft draft;
    private final List<Button> languageButtons = new ArrayList<>();
    private final List<EditBox> nameBoxes = new ArrayList<>();
    private String language;
    private String status;

    public ResourcePackLanguageScreen(Screen parent, ResourcePackEditorDraft draft) {
        super(Component.translatable("screen.prismod.language_editor_title"));
        this.parent = parent;
        this.draft = draft;
    }

    @Override
    protected void init() {
        clearWidgets();
        languageButtons.clear();
        nameBoxes.clear();
        List<String> languages = languages();
        if (language == null || !languages.contains(language)) language = languages.isEmpty() ? null : languages.get(0);
        int left = 24;
        int top = 56;
        int languageWidth = 120;
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.create_language_zh_cn"), ignored -> createLanguage("zh_cn"))
                .bounds(left, 30, languageWidth, 20).build()).active = !hasLanguage("zh_cn");
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.create_language_en_us"), ignored -> createLanguage("en_us"))
                .bounds(left + languageWidth + 8, 30, languageWidth, 20).build()).active = !hasLanguage("en_us");
        for (int index = 0; index < languages.size(); index++) {
            String id = languages.get(index);
            Button button = addRenderableWidget(Button.builder(Component.literal(id), ignored -> {
                        language = id;
                        init();
                    }).bounds(left, top + index * 24, languageWidth, 20).build());
            button.active = !id.equals(language);
            languageButtons.add(button);
        }
        if (language != null) buildFilterNames(left + languageWidth + 16, top);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - 124, height - 40, 100, 20).build());
    }

    private boolean hasLanguage(String id) {
        return draft.files().containsKey("assets/" + draft.namespace() + "/lang/" + id + ".json");
    }

    private void createLanguage(String id) {
        if (draft.createLanguageFile(id)) {
            language = id;
            status = Component.translatable("screen.prismod.language_created", id).getString();
            init();
        } else {
            status = Component.translatable("screen.prismod.language_exists", id).getString();
        }
    }

    private List<String> languages() {
        String prefix = "assets/" + draft.namespace() + "/lang/";
        return draft.files().keySet().stream()
                .filter(path -> path.startsWith(prefix) && path.endsWith(".json"))
                .map(path -> path.substring(prefix.length(), path.length() - 5))
                .sorted().toList();
    }

    private void buildFilterNames(int left, int top) {
        String path = "assets/" + draft.namespace() + "/lang/" + language + ".json";
        JsonObject translations = parse(path);
        int index = 0;
        for (PrismodPackLoader.PackFilterEntry filter : draft.filters()) {
            JsonObject manifest = draft.filterManifest(filter.id());
            String key = manifest.has("display_name") ? manifest.get("display_name").getAsString() : "filter." + draft.namespace() + "." + filter.id();
            EditBox box = addRenderableWidget(new EditBox(font, left, top + index * 44, width - left - 24, 20,
                    Component.literal(filter.id())));
            box.setValue(translations.has(key) ? translations.get(key).getAsString() : "");
            box.setResponder(value -> {
                if (value.isBlank()) translations.remove(key);
                else translations.addProperty(key, value);
                draft.files().put(path, new GsonBuilder().setPrettyPrinting().create().toJson(translations));
            });
            nameBoxes.add(box);
            index++;
        }
    }

    private JsonObject parse(String path) {
        String content = draft.files().get(path);
        if (content == null || content.isBlank()) return new JsonObject();
        try {
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.prismod.languages"), 24, 20, 0xAAAAAA);
        if (language == null) graphics.drawString(font, Component.translatable("screen.prismod.no_language_files"), 280, 52, 0xFFCC66);
        else graphics.drawString(font, Component.translatable("screen.prismod.language_names", language), 280, 20, 0xAAAAAA);
        if (status != null) graphics.drawString(font, limitedStatus(width - 48), 24, height - 58, 0xFFCC66);
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
