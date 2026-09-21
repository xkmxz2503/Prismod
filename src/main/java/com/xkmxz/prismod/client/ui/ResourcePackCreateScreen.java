package com.xkmxz.prismod.client.ui;

import com.xkmxz.prismod.client.bootstrap.PrismodClient;
import com.xkmxz.prismod.client.pack.ResourcePackEditorService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Creates an empty Prismod resource-pack skeleton. Filters are added later in the editor. */
public final class ResourcePackCreateScreen extends Screen {
    private final Screen parent;
    private EditBox nameBox;
    private EditBox namespaceBox;
    private Button readmeButton;
    private Button createButton;
    private boolean includeReadme = true;
    private String status;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;

    public ResourcePackCreateScreen(Screen parent) {
        super(Component.translatable("screen.prismod.create_pack_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        panelWidth = Math.min(500, Math.max(280, width - 32));
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(24, (height - 180) / 2);
        int fieldWidth = panelWidth;

        nameBox = addRenderableWidget(new EditBox(font, panelLeft, panelTop + 30, fieldWidth, 20,
                Component.translatable("screen.prismod.create_pack_name")));
        nameBox.setMaxLength(128);
        namespaceBox = addRenderableWidget(new EditBox(font, panelLeft, panelTop + 72, fieldWidth, 20,
                Component.translatable("screen.prismod.create_pack_namespace")));
        namespaceBox.setMaxLength(63);

        readmeButton = addRenderableWidget(Button.builder(readmeMessage(), button -> {
            includeReadme = !includeReadme;
            button.setMessage(readmeMessage());
        }).bounds(panelLeft, panelTop + 110, fieldWidth, 20).build());
        createButton = addRenderableWidget(Button.builder(Component.translatable("screen.prismod.create_pack_submit"),
                button -> create()).bounds(panelLeft, panelTop + 142, (fieldWidth - 4) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.prismod.create_pack_cancel"),
                button -> onClose()).bounds(panelLeft + (fieldWidth + 4) / 2, panelTop + 142,
                (fieldWidth - 4) / 2, 20).build());
        namespaceBox.setResponder(value -> updateCreateState());
        updateCreateState();
    }

    private Component readmeMessage() {
        return Component.translatable("screen.prismod.create_pack_readme",
                Component.translatable(includeReadme ? "options.on" : "options.off"));
    }

    private void updateCreateState() {
        if (createButton != null) createButton.active = validNamespaceInput();
    }

    private boolean validNamespaceInput() {
        String namespace = namespaceBox == null ? "" : namespaceBox.getValue().trim();
        return net.minecraft.resources.ResourceLocation.isValidNamespace(namespace)
                && !java.util.Set.of("minecraft", "prismod").contains(namespace);
    }

    private void create() {
        String namespace = namespaceBox.getValue().trim();
        if (!validNamespaceInput()) {
            status = Component.translatable("screen.prismod.create_pack_invalid_namespace").getString();
            return;
        }
        ResourcePackEditorService.CreatePackResult result = ResourcePackEditorService.createPack(
                new ResourcePackEditorService.CreatePackRequest(nameBox.getValue(), namespace, includeReadme));
        if (!result.success()) {
            status = result.message();
            return;
        }
        PrismodClient.reloadPrismodResources();
        if (minecraft != null) minecraft.setScreen(parent);
        if (parent instanceof ResourcePackManagerScreen manager) {
            manager.openCreatedPack(namespace);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, panelTop - 18, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.prismod.create_pack_name"),
                panelLeft, panelTop + 18, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.create_pack_namespace"),
                panelLeft, panelTop + 60, 0xBBBBBB);
        graphics.drawString(font, Component.translatable("screen.prismod.create_pack_readme_hint"),
                panelLeft, panelTop + 168, 0xAAAAAA);
        if (status != null) graphics.drawString(font, limitedStatus(panelWidth), panelLeft,
                panelTop + 188, 0xFFCC66);
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
