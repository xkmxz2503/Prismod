package com.xkmxz.prismod.client.ui;

import com.google.gson.JsonObject;
import com.xkmxz.prismod.client.bootstrap.PrismodClient;
import com.xkmxz.prismod.client.pack.PrismodPackLoader;
import com.xkmxz.prismod.client.pack.ResourcePackEditorDraft;
import com.xkmxz.prismod.client.pack.ResourcePackEditorService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Normal resource-pack editor. Raw files are intentionally kept in a separate advanced page. */
public final class ResourcePackEditorScreen extends Screen {
    private final Screen parent;
    private final ResourcePackEditorDraft draft;
    private EditBox nameBox;
    private EditBox namespaceBox;
    private final List<Button> filterButtons = new ArrayList<>();
    private String status;
    private int filterListTop;
    private int filterListBottom;
    private int filterListLeft;
    private int filterListRight;
    private int filterScroll;

    public ResourcePackEditorScreen(Screen parent, PrismodPackLoader.PackCandidate candidate) throws IOException {
        this(parent, ResourcePackEditorDraft.open(candidate));
    }

    public ResourcePackEditorScreen(Screen parent, ResourcePackEditorDraft draft) {
        super(Component.translatable("screen.prismod.resource_pack_editor"));
        this.parent = parent;
        this.draft = draft;
    }

    @Override
    protected void init() {
        clearWidgets();
        filterButtons.clear();
        int panelLeft = 24;
        int panelTop = 18;
        int panelWidth = Math.min(760, width - 48);
        int gap = 16;
        int leftWidth = Math.min(340, Math.max(210, (panelWidth - gap) * 2 / 5));
        int right = panelLeft + leftWidth + gap;
        int rightWidth = Math.max(160, panelWidth - leftWidth - gap);

        nameBox = addRenderableWidget(new EditBox(font, panelLeft, panelTop + 24, leftWidth, 20,
                Component.translatable("screen.prismod.pack_name")));
        nameBox.setValue(draft.name());
        namespaceBox = addRenderableWidget(new EditBox(font, panelLeft, panelTop + 62, leftWidth, 20,
                Component.translatable("screen.prismod.namespace")));
        namespaceBox.setValue(draft.namespace());

        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.new_post_filter"), button -> openCreateFilter("post_chain"))
                .bounds(panelLeft, panelTop + 98, (leftWidth - 4) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.new_lut_filter"), button -> openCreateFilter("lut3d"))
                .bounds(panelLeft + (leftWidth + 4) / 2, panelTop + 98, (leftWidth - 4) / 2, 20).build());

        filterListLeft = right;
        filterListRight = right + rightWidth;
        filterListTop = panelTop + 30;
        filterListBottom = Math.max(filterListTop, height - 32);
        int index = 0;
        for (PrismodPackLoader.PackFilterEntry filter : draft.filters()) {
            JsonObject manifest = draft.filterManifest(filter.id());
            String type = manifest.has("type") ? manifest.get("type").getAsString() : "?";
            String source = manifest.has("source") ? manifest.get("source").getAsString() : "?";
            Button row = addRenderableWidget(Button.builder(Component.literal(filter.id() + "  [" + type + "]  " + source),
                            button -> openFilter(filter.id()))
                    .bounds(filterListLeft, filterListTop, rightWidth, 20).build());
            filterButtons.add(row);
            index++;
        }
        arrangeFilterRows();

        int actionTop = panelTop + 134;
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.readme_editor"), button -> openReadme())
                .bounds(panelLeft, actionTop, (leftWidth - 4) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.language_editor"), button -> openLanguages())
                .bounds(panelLeft + (leftWidth + 4) / 2, actionTop, (leftWidth - 4) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.advanced_editor"), button -> openAdvanced())
                .bounds(panelLeft, actionTop + 24, (leftWidth - 4) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.save"), button -> save())
                .bounds(panelLeft + (leftWidth + 4) / 2, actionTop + 24, (leftWidth - 4) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(panelLeft, actionTop + 48, leftWidth, 20).build());
    }

    private void openFilter(String id) {
        syncMetadata();
        if (minecraft != null) minecraft.setScreen(new ResourcePackFilterScreen(this, draft, id));
    }

    private void openCreateFilter(String type) {
        syncMetadata();
        if (minecraft != null) minecraft.setScreen(new ResourcePackFilterCreateScreen(this, draft, type));
    }

    public void filterCreated(String id) {
        status = Component.translatable("screen.prismod.filter_added", id).getString();
        init();
    }

    private void arrangeFilterRows() {
        for (int index = 0; index < filterButtons.size(); index++) {
            Button row = filterButtons.get(index);
            int y = filterListTop + index * 24 - filterScroll;
            row.setX(filterListLeft);
            row.setWidth(filterListRight - filterListLeft);
            row.setY(y);
            row.visible = y + 20 > filterListTop && y < filterListBottom;
        }
    }

    private void openReadme() {
        syncMetadata();
        if (minecraft != null) minecraft.setScreen(new ResourcePackReadmeScreen(this, draft));
    }

    private void openLanguages() {
        syncMetadata();
        if (minecraft != null) minecraft.setScreen(new ResourcePackLanguageScreen(this, draft));
    }

    private void openAdvanced() {
        syncMetadata();
        if (minecraft != null) minecraft.setScreen(new ResourcePackAdvancedEditorScreen(this, draft));
    }

    private void save() {
        syncMetadata();
        ResourcePackEditorService.SaveResult result = ResourcePackEditorService.save(
                draft.session(), nameBox.getValue(), namespaceBox.getValue(), draft.pendingFiles());
        status = result.message();
        if (result.success()) {
            PrismodClient.reloadPrismodResources();
            if (minecraft != null) minecraft.setScreen(parent);
        }
    }

    private void syncMetadata() {
        if (nameBox != null) draft.setName(nameBox.getValue());
        if (namespaceBox != null && net.minecraft.resources.ResourceLocation.isValidNamespace(namespaceBox.getValue().trim())) {
            draft.setNamespace(namespaceBox.getValue());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= filterListLeft && mouseX <= filterListRight
                && mouseY >= filterListTop && mouseY <= filterListBottom) {
            int maxScroll = Math.max(0, filterButtons.size() * 24 - (filterListBottom - filterListTop));
            filterScroll = Mth.clamp(filterScroll - (int) Math.signum(delta) * 24, 0, maxScroll);
            arrangeFilterRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);
        int left = 24;
        int top = 18;
        graphics.drawString(font, Component.translatable("screen.prismod.pack_name"), left, top + 10, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.namespace"), left, top + 48, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.filters_abstract"), filterListLeft, top + 10, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.editor_hint"), left, height - 22, 0xAAAAAA);
        if (status != null) graphics.drawString(font, limitedStatus(width - left * 2), left, height - 38, 0xFFCC66);
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
