package com.xkmxz.prismod.client.ui;

import com.xkmxz.prismod.client.config.PrismodClientConfig;
import com.xkmxz.prismod.client.filter.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 独立的滤镜展示状态管理界面。 */
public final class FilterVisibilityManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private final Screen parent;
    private final Set<FilterKey> draftHiddenFilters = new LinkedHashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private int panelTop;
    private int panelLeft;
    private int panelWidth;
    private int listTop;
    private int listBottom;
    private int scrollOffset;

    public FilterVisibilityManagerScreen(Screen parent) {
        super(Component.translatable("screen.prismod.filter_manager_title"));
        this.parent = parent;
        draftHiddenFilters.addAll(PrismodClientConfig.hiddenFilters());
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

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(panelLeft, panelTop + 18, panelWidth, 20).build());

        buildRows();
        arrangeRows();

        int half = (panelWidth - 4) / 2;
        Button save = addRenderableWidget(Button.builder(Component.translatable("screen.prismod.save"), button -> save())
                .bounds(panelLeft, listBottom + 18, half, 20).build());
        Button cancel = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(panelLeft + half + 4, listBottom + 18, half, 20).build());
        save.setTabOrderGroup(100);
        cancel.setTabOrderGroup(100);
    }

    private void buildRows() {
        for (FilterDefinition definition : FilterRegistry.get().definitions()) {
            String namespace = definition.packNamespace();
            if (namespace != null && !PrismodClientConfig.isPackEnabled(namespace)) continue;
            boolean visible = !draftHiddenFilters.contains(definition.key());
            Component label = Component.translatable("screen.prismod.filter_row", definition.displayName(),
                    Component.translatable(visible ? "options.on" : "options.off"));
            Button row = addRenderableWidget(Button.builder(label, button -> {
                if (draftHiddenFilters.contains(definition.key())) draftHiddenFilters.remove(definition.key());
                else draftHiddenFilters.add(definition.key());
                rebuildRows();
            }).bounds(panelLeft, listTop, panelWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.prismod.filter_toggle", definition.displayName())))
                    .build());
            rows.add(new Row(row));
        }
    }

    private void rebuildRows() {
        clearWidgets();
        rows.clear();
        init();
    }

    private void save() {
        PrismodClientConfig.setHiddenFilters(draftHiddenFilters);
        PrismodClientConfig.SPEC.save();
        FilterManager.get().refreshConfig();
        if (minecraft != null && parent instanceof FilterConfigScreen filterScreen) {
            minecraft.setScreen(new FilterConfigScreen(filterScreen.parentScreen()));
        } else {
            onClose();
        }
    }

    private void arrangeRows() {
        for (int index = 0; index < rows.size(); index++) {
            AbstractWidget widget = rows.get(index).button();
            int y = listTop + index * ROW_HEIGHT - scrollOffset;
            boolean visible = y + ROW_HEIGHT > listTop && y < listBottom;
            widget.setY(y);
            widget.visible = visible;
            widget.active = visible;
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
        graphics.drawString(font, Component.translatable("screen.prismod.filter_manager_hint"),
                panelLeft, panelTop + 48, 0xBBBBBB);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private record Row(Button button) {
    }
}
