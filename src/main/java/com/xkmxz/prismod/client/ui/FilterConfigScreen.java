package com.xkmxz.prismod.client.ui;

import com.xkmxz.prismod.client.config.PrismodClientConfig;
import com.xkmxz.prismod.client.filter.*;
import com.xkmxz.prismod.client.filter.registry.FilterDefinition;
import com.xkmxz.prismod.client.filter.registry.FilterRegistry;
import com.xkmxz.prismod.client.filter.state.FilterManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 配置草稿独立于控件，调整窗口大小或取消时不会意外写入配置。 */
public final class FilterConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private final Screen parent;
    private final List<FilterKey> draftOrder;
    private final Map<FilterKey, Double> draftStrengths = new HashMap<>();
    private final Map<FilterKey, RowControls> rows = new HashMap<>();
    private boolean draftEnabled;
    private FilterKey draftSelected;
    private FilterKey draggedId;
    private int panelTop;
    private int panelLeft;
    private int panelWidth;
    private int listTop;
    private int listBottom;
    private int scrollOffset;

    public FilterConfigScreen(Screen parent) {
        super(Component.translatable("screen.prismod.title"));
        this.parent = parent;
        draftEnabled = PrismodClientConfig.ENABLED.get();
        draftOrder = new ArrayList<>();
        for (FilterKey key : PrismodClientConfig.cycleOrder()) {
            if (PrismodClientConfig.isFilterVisible(key) && FilterRegistry.get().definition(key) != null) {
                draftOrder.add(key);
            }
        }
        for (FilterDefinition definition : FilterRegistry.get().definitions()) {
            if (PrismodClientConfig.isFilterVisible(definition.key()) && !draftOrder.contains(definition.key())) {
                draftOrder.add(definition.key());
            }
        }
        draftSelected = FilterManager.get().selectedSelection().key();
        for (FilterKey key : draftOrder) {
            draftStrengths.put(key, (double) PrismodClientConfig.strength(key));
        }
        for (FilterDefinition definition : FilterRegistry.get().definitions()) {
            FilterKey key = definition.key();
            draftStrengths.put(key, (double) PrismodClientConfig.strength(key));
        }
    }

    @Override
    protected void init() {
        rows.clear();
        draggedId = null;
        setDragging(false);
        panelWidth = Math.min(440, width - 16);
        panelLeft = (width - panelWidth) / 2;
        int panelHeight = Math.min(Math.max(180, height - 20), 300);
        panelTop = Math.max(6, (height - panelHeight) / 2);
        listTop = panelTop + 82;
        listBottom = panelTop + panelHeight - 42;

        Button enabled = addRenderableWidget(Button.builder(enabledLabel(), button -> {
            draftEnabled = !draftEnabled;
            button.setMessage(enabledLabel());
        }).bounds(panelLeft, panelTop + 18, panelWidth, 20).build());
        enabled.setTabOrderGroup(0);

        int managerButtonWidth = (panelWidth - 4) / 2;
        Button resourcePacks = addRenderableWidget(Button.builder(
                Component.translatable("screen.prismod.resource_packs"),
                button -> minecraft.setScreen(new ResourcePackManagerScreen(this)))
                .bounds(panelLeft, panelTop + 40, managerButtonWidth, 20).build());
        resourcePacks.setTabOrderGroup(1);
        Button filterManager = addRenderableWidget(Button.builder(
                Component.translatable("screen.prismod.filter_manager"),
                button -> minecraft.setScreen(new FilterVisibilityManagerScreen(this)))
                .bounds(panelLeft + managerButtonWidth + 4, panelTop + 40, managerButtonWidth, 20).build());
        filterManager.setTabOrderGroup(2);

        int nameWidth = Math.min(132, panelWidth / 3);
        int nameLeft = panelLeft + 20;
        int sliderLeft = nameLeft + nameWidth + 2;
        int sliderWidth = panelWidth - nameWidth - 108;
        int upLeft = panelLeft + panelWidth - 38;
        int debugLeft = upLeft - 42;

        for (FilterKey id : draftOrder) {
            Button handle = addRenderableWidget(Button.builder(Component.literal("≡"), button -> {})
                    .bounds(panelLeft, listTop, 18, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.prismod.drag", filterName(id))))
                    .createNarration(supplier -> Component.translatable("screen.prismod.drag", filterName(id)))
                    .build());
            Button select = addRenderableWidget(Button.builder(selectionLabel(id), button -> {
                draftSelected = id;
                rows.forEach((filter, row) -> row.select.setMessage(selectionLabel(filter)));
            }).bounds(nameLeft, listTop, nameWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.prismod.select", filterName(id))))
                    .build());
            StrengthSlider strength = addRenderableWidget(new StrengthSlider(id, sliderLeft, listTop, sliderWidth));
            Button debug = null;
            FilterDefinition definition = FilterRegistry.get().definition(id);
            if (definition != null && definition.debugSupported()) {
                debug = addRenderableWidget(Button.builder(Component.translatable("screen.prismod.filter_debug.open"), button -> openFilterDebug(id))
                        .bounds(debugLeft, listTop, 38, 20).tooltip(Tooltip.create(Component.translatable("screen.prismod.filter_debug.open_hint"))).build());
            }
            Button up = addRenderableWidget(Button.builder(Component.literal("↑"), button -> move(id, -1))
                    .bounds(upLeft, listTop, 18, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.prismod.move_up", filterName(id))))
                    .createNarration(supplier -> Component.translatable("screen.prismod.move_up", filterName(id)))
                    .build());
            Button down = addRenderableWidget(Button.builder(Component.literal("↓"), button -> move(id, 1))
                    .bounds(upLeft + 20, listTop, 18, 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.prismod.move_down", filterName(id))))
                    .createNarration(supplier -> Component.translatable("screen.prismod.move_down", filterName(id)))
                    .build());
            rows.put(id, new RowControls(handle, select, strength, debug, up, down));
        }
        arrangeRows();

        int buttonWidth = (panelWidth - 4) / 2;
        Button save = addRenderableWidget(Button.builder(Component.translatable("screen.prismod.save"), button -> save())
                .bounds(panelLeft, listBottom + 18, buttonWidth, 20).build());
        Button cancel = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(panelLeft + buttonWidth + 4, listBottom + 18, buttonWidth, 20).build());
        save.setTabOrderGroup(100);
        cancel.setTabOrderGroup(100);
    }

    private void openFilterDebug(FilterKey key) {
        if (minecraft != null && minecraft.level != null) {
            minecraft.setScreen(new LutDebugScreen(this, key));
        }
    }

    private Component enabledLabel() {
        return Component.translatable("screen.prismod.enabled",
                Component.translatable(draftEnabled ? "options.on" : "options.off"));
    }

    private static Component filterName(FilterKey id) {
        FilterDefinition definition = FilterRegistry.get().definition(id);
        Component name = definition == null ? Component.literal(id.serializedName()) : definition.displayName();
        String failure = FilterRegistry.get().failure(id);
        return definition != null && failure == null ? name
                : Component.translatable("screen.prismod.filter_unavailable", name);
    }

    private Component selectionLabel(FilterKey id) {
        return id.equals(draftSelected)
                ? Component.translatable("screen.prismod.selected", filterName(id)) : filterName(id);
    }

    private void move(FilterKey id, int direction) {
        int oldIndex = draftOrder.indexOf(id);
        int newIndex = Mth.clamp(oldIndex + direction, 0, draftOrder.size() - 1);
        if (oldIndex != newIndex) {
            draftOrder.remove(oldIndex);
            draftOrder.add(newIndex, id);
            arrangeRows();
        }
    }

    private void arrangeRows() {
        for (int index = 0; index < draftOrder.size(); index++) {
            RowControls row = rows.get(draftOrder.get(index));
            int y = listTop + index * ROW_HEIGHT - scrollOffset;
            boolean visible = y + ROW_HEIGHT > listTop && y < listBottom;
            for (AbstractWidget widget : row.widgets()) {
                widget.setY(y);
                widget.visible = visible;
                widget.active = visible;
                widget.setTabOrderGroup(index + 1);
            }
            row.up.active = index > 0;
            row.down.active = index < draftOrder.size() - 1;
        }
    }

    private void save() {
        PrismodClientConfig.ENABLED.set(draftEnabled);
        List<FilterKey> order = new ArrayList<>(draftOrder);
        for (FilterKey key : PrismodClientConfig.cycleOrder()) {
            if (!order.contains(key)) order.add(key);
        }
        PrismodClientConfig.setCycleOrder(order);
        draftStrengths.forEach(PrismodClientConfig::setStrength);
        PrismodClientConfig.SPEC.save();
        FilterManager.get().refreshConfig();
        // select 仅更新玩家选择，不能覆盖其他模组正在强制使用的滤镜。
        FilterManager.get().select(draftSelected);
        onClose();
    }

    Screen parentScreen() {
        return parent;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggedId = null;
            for (FilterKey id : draftOrder) {
                if (rows.get(id).handle.isMouseOver(mouseX, mouseY)) {
                    draggedId = id;
                    break;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggedId != null) {
            int target = Mth.clamp((int) Math.floor((mouseY - listTop + scrollOffset) / ROW_HEIGHT), 0, draftOrder.size() - 1);
            move(draggedId, target - draftOrder.indexOf(draggedId));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelLeft && mouseX <= panelLeft + panelWidth && mouseY >= listTop && mouseY <= listBottom) {
            int maxScroll = Math.max(0, draftOrder.size() * ROW_HEIGHT - (listBottom - listTop));
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta) * ROW_HEIGHT, 0, maxScroll);
            arrangeRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggedId != null) {
            draggedId = null;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, panelTop, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.prismod.order_hint"), width / 2, panelTop + 66, 0xBBBBBB);
        boolean forced = FilterManager.get().isForced();
        graphics.drawCenteredString(font, Component.translatable(forced ? "screen.prismod.forced_hint" : "screen.prismod.save_hint"),
                width / 2, listBottom + 2, forced ? 0xFFCC66 : 0xBBBBBB);
        if (draggedId != null) {
            int y = listTop + draftOrder.indexOf(draggedId) * ROW_HEIGHT - scrollOffset;
            graphics.fill(panelLeft - 2, y - 1, panelLeft + panelWidth + 2, y + 21, 0x8855AAFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private record RowControls(Button handle, Button select, StrengthSlider strength, Button debug, Button up, Button down) {
        private List<AbstractWidget> widgets() {
            return debug == null ? List.of(handle, select, strength, up, down) : List.of(handle, select, strength, debug, up, down);
        }
    }

    private final class StrengthSlider extends AbstractSliderButton {
        private final FilterKey id;

        private StrengthSlider(FilterKey id, int x, int y, int sliderWidth) {
            super(x, y, sliderWidth, 20, Component.empty(), draftStrengths.get(id));
            this.id = id;
            setTooltip(Tooltip.create(Component.translatable("screen.prismod.strength_hint", filterName(id))));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.prismod.strength", Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            draftStrengths.put(id, value);
        }
    }
}
