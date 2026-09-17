package com.xkmxz.prismod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** 配置草稿独立于控件，调整窗口大小或取消时不会意外写入配置。 */
public final class FilterConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private final Screen parent;
    private final List<FilterId> draftOrder;
    private final EnumMap<FilterId, Double> draftStrengths = new EnumMap<>(FilterId.class);
    private final EnumMap<FilterId, RowControls> rows = new EnumMap<>(FilterId.class);
    private boolean draftEnabled;
    private FilterId draftSelected;
    private FilterId draggedId;
    private int panelTop;
    private int panelLeft;
    private int panelWidth;
    private int listTop;

    public FilterConfigScreen(Screen parent) {
        super(Component.translatable("screen.prismod.title"));
        this.parent = parent;
        draftEnabled = PrismodClientConfig.ENABLED.get();
        draftOrder = new ArrayList<>(PrismodClientConfig.cycleOrder());
        draftSelected = FilterManager.get().selectedState().id();
        for (FilterId id : FilterId.values()) {
            draftStrengths.put(id, (double) PrismodClientConfig.strength(id));
        }
    }

    @Override
    protected void init() {
        rows.clear();
        draggedId = null;
        setDragging(false);
        panelWidth = Math.min(440, width - 16);
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(6, (height - 228) / 2);
        listTop = panelTop + 58;

        Button enabled = addRenderableWidget(Button.builder(enabledLabel(), button -> {
            draftEnabled = !draftEnabled;
            button.setMessage(enabledLabel());
        }).bounds(panelLeft, panelTop + 18, panelWidth, 20).build());
        enabled.setTabOrderGroup(0);

        int nameWidth = Math.min(132, panelWidth / 3);
        int nameLeft = panelLeft + 20;
        int sliderLeft = nameLeft + nameWidth + 2;
        int sliderWidth = panelWidth - nameWidth - 62;
        int upLeft = panelLeft + panelWidth - 38;

        for (FilterId id : draftOrder) {
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
            rows.put(id, new RowControls(handle, select, strength, up, down));
        }
        arrangeRows();

        int buttonWidth = (panelWidth - 4) / 2;
        Button save = addRenderableWidget(Button.builder(Component.translatable("screen.prismod.save"), button -> save())
                .bounds(panelLeft, panelTop + 208, buttonWidth, 20).build());
        Button cancel = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(panelLeft + buttonWidth + 4, panelTop + 208, buttonWidth, 20).build());
        save.setTabOrderGroup(100);
        cancel.setTabOrderGroup(100);
    }

    private Component enabledLabel() {
        return Component.translatable("screen.prismod.enabled",
                Component.translatable(draftEnabled ? "options.on" : "options.off"));
    }

    private static Component filterName(FilterId id) {
        return Component.translatable(id.translationKey());
    }

    private Component selectionLabel(FilterId id) {
        return id == draftSelected
                ? Component.translatable("screen.prismod.selected", filterName(id)) : filterName(id);
    }

    private void move(FilterId id, int direction) {
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
            for (AbstractWidget widget : row.widgets()) {
                widget.setY(listTop + index * ROW_HEIGHT);
                widget.setTabOrderGroup(index + 1);
            }
            row.up.active = index > 0;
            row.down.active = index < draftOrder.size() - 1;
        }
    }

    private void save() {
        PrismodClientConfig.ENABLED.set(draftEnabled);
        PrismodClientConfig.setCycleOrder(draftOrder);
        draftStrengths.forEach(PrismodClientConfig::setStrength);
        PrismodClientConfig.SPEC.save();
        FilterManager.get().refreshConfig();
        // select 仅更新玩家选择，不能覆盖其他模组正在强制使用的滤镜。
        FilterManager.get().select(draftSelected);
        onClose();
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
            for (FilterId id : draftOrder) {
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
            int target = Mth.clamp((int) Math.floor((mouseY - listTop) / ROW_HEIGHT), 0, draftOrder.size() - 1);
            move(draggedId, target - draftOrder.indexOf(draggedId));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
        graphics.drawCenteredString(font, Component.translatable("screen.prismod.order_hint"), width / 2, panelTop + 43, 0xBBBBBB);
        boolean forced = FilterManager.get().isForced();
        graphics.drawCenteredString(font, Component.translatable(forced ? "screen.prismod.forced_hint" : "screen.prismod.save_hint"),
                width / 2, panelTop + 194, forced ? 0xFFCC66 : 0xBBBBBB);
        if (draggedId != null) {
            int y = listTop + draftOrder.indexOf(draggedId) * ROW_HEIGHT;
            graphics.fill(panelLeft - 2, y - 1, panelLeft + panelWidth + 2, y + 21, 0x8855AAFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private record RowControls(Button handle, Button select, StrengthSlider strength, Button up, Button down) {
        private List<AbstractWidget> widgets() {
            return List.of(handle, select, strength, up, down);
        }
    }

    private final class StrengthSlider extends AbstractSliderButton {
        private final FilterId id;

        private StrengthSlider(FilterId id, int x, int y, int sliderWidth) {
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
