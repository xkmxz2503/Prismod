package com.xkmxz.prismod.client.ui;

import com.xkmxz.prismod.client.pack.ResourcePackEditorDraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Editor for the resource-pack root README.md. */
public final class ResourcePackReadmeScreen extends Screen {
    private final Screen parent;
    private final ResourcePackEditorDraft draft;
    private MultiLineEditBox textBox;

    public ResourcePackReadmeScreen(Screen parent, ResourcePackEditorDraft draft) {
        super(Component.translatable("screen.prismod.readme_editor_title"));
        this.parent = parent;
        this.draft = draft;
    }

    @Override
    protected void init() {
        clearWidgets();
        textBox = addRenderableWidget(new MultiLineEditBox(font, 24, 32, width - 48, height - 84,
                Component.translatable("screen.prismod.readme_editor"), Component.empty()));
        textBox.setValue(draft.files().getOrDefault("README.md", ""));
        textBox.setValueListener(value -> draft.files().put("README.md", value));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - 124, height - 40, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.prismod.readme_hint"), 24, 20, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
