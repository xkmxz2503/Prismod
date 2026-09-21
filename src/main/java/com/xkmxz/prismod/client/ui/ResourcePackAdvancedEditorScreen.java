package com.xkmxz.prismod.client.ui;

import com.xkmxz.prismod.client.pack.PrismodPackLoader;
import com.xkmxz.prismod.client.pack.ResourcePackEditorDraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Advanced raw-file editor. It edits the shared draft and never saves by itself. */
public final class ResourcePackAdvancedEditorScreen extends Screen {
    private final Screen parent;
    private final ResourcePackEditorDraft draft;
    private final List<Button> fileButtons = new ArrayList<>();
    private MultiLineEditBox textBox;
    private String selectedFile;
    private int left;
    private int listTop;
    private int listBottom;
    private int scroll;

    public ResourcePackAdvancedEditorScreen(Screen parent, ResourcePackEditorDraft draft) {
        super(Component.translatable("screen.prismod.advanced_editor_title"));
        this.parent = parent;
        this.draft = draft;
        this.selectedFile = PrismodPackLoader.MANIFEST_FILE;
    }

    @Override
    protected void init() {
        clearWidgets();
        fileButtons.clear();
        left = 20;
        int top = 16;
        int contentWidth = Math.max(320, width - 40);
        int leftWidth = Math.min(300, Math.max(220, contentWidth / 3));
        int rightLeft = left + leftWidth + 10;
        int rightWidth = contentWidth - leftWidth - 10;
        listTop = top + 28;
        listBottom = height - 44;
        textBox = addRenderableWidget(new MultiLineEditBox(font, rightLeft, top + 28, rightWidth, height - 72,
                Component.translatable("screen.prismod.file_editor"), Component.empty()));
        textBox.setValue(draft.files().getOrDefault(selectedFile, ""));
        textBox.setValueListener(value -> { if (selectedFile != null) draft.files().put(selectedFile, value); });
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - 112, height - 28, 100, 20).build());
        buildFileButtons(left, listTop, leftWidth);
    }

    private void buildFileButtons(int x, int y, int width) {
        List<String> names = draft.files().keySet().stream().sorted().toList();
        for (int index = 0; index < names.size(); index++) {
            String path = names.get(index);
            int buttonY = y + index * 22 - scroll;
            Button button = addRenderableWidget(Button.builder(Component.literal(fileName(path)), ignored -> selectFile(path))
                    .bounds(x, buttonY, width, 20).build());
            button.visible = buttonY + 20 > listTop && buttonY < listBottom;
            fileButtons.add(button);
        }
    }

    private void selectFile(String path) {
        if (selectedFile != null) draft.files().put(selectedFile, textBox.getValue());
        selectedFile = path;
        textBox.setValue(draft.files().getOrDefault(path, ""));
    }

    private static String fileName(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX <= left + 300 && mouseY >= listTop && mouseY <= listBottom) {
            int max = Math.max(0, draft.files().size() * 22 - (listBottom - listTop));
            scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 22, 0, max);
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.prismod.files"), left, listTop - 14, 0xBBBBBB);
        graphics.drawString(font, Component.literal(fileName(selectedFile)), left + 320, 21, 0xBBBBBB);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
