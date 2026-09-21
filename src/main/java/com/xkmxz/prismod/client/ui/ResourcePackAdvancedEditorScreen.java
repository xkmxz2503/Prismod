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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Advanced raw-file editor. It edits the shared draft and never saves by itself. */
public final class ResourcePackAdvancedEditorScreen extends Screen {
    private final Screen parent;
    private final ResourcePackEditorDraft draft;
    private final List<Button> fileButtons = new ArrayList<>();
    private final Map<String, Boolean> expanded = new LinkedHashMap<>();
    private MultiLineEditBox textBox;
    private String selectedFile;
    private int treeLeft;
    private int treeRight;
    private int treeTop;
    private int treeBottom;
    private int treeScroll;

    public ResourcePackAdvancedEditorScreen(Screen parent, ResourcePackEditorDraft draft) {
        super(Component.translatable("screen.prismod.advanced_editor_title"));
        this.parent = parent;
        this.draft = draft;
        this.selectedFile = PrismodPackLoader.MANIFEST_FILE;
        expanded.putIfAbsent("filters", true);
        expanded.putIfAbsent("languages", true);
        expanded.putIfAbsent("documentation", true);
    }

    @Override
    protected void init() {
        clearWidgets();
        fileButtons.clear();
        treeLeft = 20;
        int top = 16;
        int contentWidth = Math.max(320, width - 40);
        int leftWidth = Math.min(300, Math.max(220, contentWidth / 3));
        treeRight = treeLeft + leftWidth;
        int rightLeft = treeRight + 10;
        int rightWidth = contentWidth - leftWidth - 10;
        treeTop = top + 28;
        treeBottom = height - 44;
        textBox = addRenderableWidget(new MultiLineEditBox(font, rightLeft, top + 28, rightWidth, height - 72,
                Component.translatable("screen.prismod.file_editor"), Component.empty()));
        textBox.setValue(draft.files().getOrDefault(selectedFile, ""));
        textBox.setValueListener(value -> { if (selectedFile != null) draft.files().put(selectedFile, value); });
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - 112, height - 28, 100, 20).build());
        buildTreeButtons(treeLeft, treeTop, leftWidth);
    }

    private void buildTreeButtons(int x, int y, int width) {
        List<TreeEntry> entries = buildTree();
        for (int index = 0; index < entries.size(); index++) {
            TreeEntry entry = entries.get(index);
            int buttonY = y + index * 22 - treeScroll;
            int indent = entry.depth() * 12;
            int buttonX = x + indent;
            int buttonWidth = Math.max(40, width - indent);
            Component label = Component.literal(entry.group() ? (isExpanded(entry.key()) ? "▼ " : "▶ ") : "  ")
                    .append(entry.label());
            Button button = addRenderableWidget(Button.builder(label, ignored -> selectTreeEntry(entry))
                    .bounds(buttonX, buttonY, buttonWidth, 20).build());
            button.visible = buttonY + 20 > treeTop && buttonY < treeBottom;
            fileButtons.add(button);
        }
    }

    private List<TreeEntry> buildTree() {
        List<TreeEntry> result = new ArrayList<>();
        result.add(new TreeEntry("filters", Component.translatable("screen.prismod.file_tree.filters"), true, 0));
        if (isExpanded("filters")) {
            for (PrismodPackLoader.PackFilterEntry filter : draft.filters().stream()
                    .sorted(Comparator.comparing(PrismodPackLoader.PackFilterEntry::id)).toList()) {
                String key = "filter:" + filter.id();
                expanded.putIfAbsent(key, true);
                result.add(new TreeEntry(key, Component.literal(filter.id()), true, 1));
                if (isExpanded(key)) addPathTree(result, key, filter.path(), 2);
            }
        }
        result.add(new TreeEntry("languages", Component.translatable("screen.prismod.file_tree.languages"), true, 0));
        if (isExpanded("languages")) {
            String prefix = "assets/" + draft.namespace() + "/lang/";
            draft.files().keySet().stream().filter(path -> path.startsWith(prefix) && path.endsWith(".json"))
                    .sorted().forEach(path -> result.add(new TreeEntry(path, Component.literal(fileName(path)), false, 1)));
        }
        result.add(new TreeEntry("documentation", Component.translatable("screen.prismod.file_tree.documentation"), true, 0));
        if (isExpanded("documentation")) {
            List<String> documentation = draft.files().keySet().stream()
                    .filter(path -> path.equals("README.md") || path.equals(PrismodPackLoader.MANIFEST_FILE))
                    .sorted().toList();
            for (String path : documentation) result.add(new TreeEntry(path, Component.literal(fileName(path)), false, 1));
        }
        return result;
    }

    private void addPathTree(List<TreeEntry> result, String rootKey, String filterPath, int depth) {
        String prefix = filterPath.endsWith("/") ? filterPath : filterPath + "/";
        Set<String> paths = new TreeSet<>();
        for (String path : draft.files().keySet()) {
            if (!path.startsWith(prefix)) continue;
            String relative = path.substring(prefix.length());
            if (!relative.isBlank()) paths.add(relative);
        }
        addFolderTree(result, paths, prefix, rootKey, "", depth);
    }

    private void addFolderTree(List<TreeEntry> result, Set<String> paths, String prefix,
                               String rootKey, String folder, int depth) {
        String folderPrefix = folder.isEmpty() ? "" : folder + "/";
        Set<String> files = new TreeSet<>();
        Set<String> folders = new TreeSet<>();
        for (String relative : paths) {
            if (!relative.startsWith(folderPrefix)) continue;
            String child = relative.substring(folderPrefix.length());
            int slash = child.indexOf('/');
            if (slash < 0) files.add(child);
            else folders.add(child.substring(0, slash));
        }
        for (String file : files) {
            String relative = folderPrefix + file;
            result.add(new TreeEntry(prefix + relative, Component.literal(file), false, depth));
        }
        for (String childFolder : folders) {
            String child = folderPrefix + childFolder;
            String key = rootKey + ":" + child;
            result.add(new TreeEntry(key, Component.literal(childFolder), true, depth));
            if (isExpanded(key)) addFolderTree(result, paths, prefix, rootKey, child, depth + 1);
        }
    }

    private boolean isExpanded(String key) {
        return expanded.getOrDefault(key, false);
    }

    private void selectTreeEntry(TreeEntry entry) {
        if (entry.group()) {
            expanded.put(entry.key(), !isExpanded(entry.key()));
            init();
            return;
        }
        selectFile(entry.key());
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
        if (mouseX >= treeLeft && mouseX <= treeRight && mouseY >= treeTop && mouseY <= treeBottom) {
            int max = Math.max(0, buildTree().size() * 22 - (treeBottom - treeTop));
            treeScroll = Mth.clamp(treeScroll - (int) Math.signum(delta) * 22, 0, max);
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.prismod.files"), treeLeft, treeTop - 14, 0xBBBBBB);
        graphics.drawString(font, Component.literal(selectedFile == null ? "" : fileName(selectedFile)), treeRight + 10, 21, 0xBBBBBB);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private record TreeEntry(String key, Component label, boolean group, int depth) { }
}
