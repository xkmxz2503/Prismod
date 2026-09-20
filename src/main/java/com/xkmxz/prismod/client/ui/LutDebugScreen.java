package com.xkmxz.prismod.client.ui;

import com.xkmxz.prismod.client.config.FilterDebugPresetStore;
import com.xkmxz.prismod.client.filter.FilterDefinition;
import com.xkmxz.prismod.client.filter.FilterKey;
import com.xkmxz.prismod.client.filter.FilterRegistry;
import com.xkmxz.prismod.client.filter.Lut3dData;
import com.xkmxz.prismod.client.filter.FilterDebugSettings;
import com.xkmxz.prismod.client.filter.FilterType;
import com.xkmxz.prismod.client.render.WorldFilterRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 通用滤镜运行时预览与基础调色页面。普通滤镜选择和强度不会被此页修改。 */
public final class LutDebugScreen extends Screen {
    private final Screen parent;
    private final FilterKey key;
    private final FilterDefinition definition;
    private final FilterDebugPresetStore presets = FilterDebugPresetStore.createDefault();
    private FilterDebugSettings settings;
    private final List<SettingSlider> sliders = new ArrayList<>();
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int settingsTop;
    private int settingsBottom;
    private int settingsScroll;
    private int previewLeft;
    private int previewTop;
    private int previewRight;
    private int previewBottom;
    private float divider = 0.5F;
    private boolean draggingDivider;

    public LutDebugScreen(Screen parent, FilterKey key) {
        super(Component.literal("滤镜调试"));
        this.parent = parent;
        this.key = key;
        this.definition = FilterRegistry.get().definition(key);
        String namespace = definition == null || definition.packNamespace() == null ? key.id().getNamespace() : definition.packNamespace();
        this.settings = presets.get(namespace, key.id().getPath());
    }

    @Override
    protected void init() {
        sliders.clear();
        if (minecraft == null || minecraft.level == null || definition == null || !definition.debugSupported()) {
            onClose();
            return;
        }
        WorldFilterRenderer.beginDebugSession(key, settings);
        panelWidth = Math.max(320, Math.min(1000, width - 20));
        panelLeft = (width - panelWidth) / 2;
        panelTop = 8;
        panelHeight = Math.max(240, height - 16);
        previewLeft = panelLeft + 12;
        previewTop = panelTop + 32;
        previewRight = panelLeft + panelWidth / 2 - 8;
        previewBottom = Math.min(panelTop + 230, panelTop + Math.max(120, height / 3));
        settingsTop = previewBottom + 28;
        int buttonY = panelTop + panelHeight - 28;
        settingsBottom = buttonY - 12;
        int sliderLeft = panelLeft + 12;
        int sliderWidth = Math.max(140, previewRight - sliderLeft);
        addSetting("预览强度", 0, 1, settings.intensity(), sliderLeft, 0, sliderWidth);
        addSetting("曝光 EV", -2, 2, settings.exposure(), sliderLeft, 0, sliderWidth);
        addSetting("对比度", -1, 1, settings.contrast(), sliderLeft, 0, sliderWidth);
        addSetting("高光", -1, 1, settings.highlights(), sliderLeft, 0, sliderWidth);
        addSetting("阴影", -1, 1, settings.shadows(), sliderLeft, 0, sliderWidth);
        addSetting("饱和度", 0, 2, settings.saturation(), sliderLeft, 0, sliderWidth);
        addSetting("色温", -1, 1, settings.temperature(), sliderLeft, 0, sliderWidth);
        addSetting("色调", -1, 1, settings.tint(), sliderLeft, 0, sliderWidth);
        addSetting("伽马", 0.1F, 3, settings.gamma(), sliderLeft, 0, sliderWidth);
        arrangeSettings();

        int buttonWidth = (panelWidth - 28) / 4;
        addRenderableWidget(Button.builder(Component.literal("保存预设"), button -> savePreset()).bounds(panelLeft + 12, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复默认"), button -> resetDefaults()).bounds(panelLeft + 16 + buttonWidth, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("复制诊断"), button -> copyDiagnostics()).bounds(panelLeft + 20 + buttonWidth * 2, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), button -> onClose()).bounds(panelLeft + 24 + buttonWidth * 3, buttonY, buttonWidth, 20).build());
    }

    private void addSetting(String name, float min, float max, float value, int x, int y, int width) {
        SettingSlider slider = new SettingSlider(name, min, max, value, x, y, width);
        sliders.add(slider);
        addRenderableWidget(slider);
    }

    private void arrangeSettings() {
        int contentHeight = sliders.size() * 23;
        int maxScroll = Math.max(0, contentHeight - Math.max(0, settingsBottom - settingsTop));
        settingsScroll = Mth.clamp(settingsScroll, 0, maxScroll);
        for (int index = 0; index < sliders.size(); index++) {
            SettingSlider slider = sliders.get(index);
            int y = settingsTop + index * 23 - settingsScroll;
            boolean visible = y + 20 > settingsTop && y < settingsBottom;
            slider.setY(y);
            slider.visible = visible;
            slider.active = visible;
        }
    }

    private void applySlider() {
        // init/resize can briefly expose an extra callback while widgets are rebuilt.
        // Keep the last valid value for any slot that is not currently represented.
        float[] values = {
                settings.intensity(), settings.exposure(), settings.contrast(),
                settings.highlights(), settings.shadows(), settings.saturation(),
                settings.temperature(), settings.tint(), settings.gamma()
        };
        for (int i = 0; i < Math.min(sliders.size(), values.length); i++) {
            values[i] = sliders.get(i).current();
        }
        settings = new FilterDebugSettings(values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8]);
        WorldFilterRenderer.updateDebugSettings(settings);
    }

    private void savePreset() {
        try {
            String namespace = definition.packNamespace() == null ? key.id().getNamespace() : definition.packNamespace();
            presets.put(namespace, key.id().getPath(), settings);
        } catch (IOException ignored) { }
    }

    private void resetDefaults() {
        settings = FilterDebugSettings.defaults();
        for (SettingSlider slider : sliders) slider.resetFromSettings(settings);
        WorldFilterRenderer.updateDebugSettings(settings);
    }

    private void copyDiagnostics() {
        if (minecraft != null) minecraft.keyboardHandler.setClipboard(diagnostics());
    }

    private String diagnostics() {
        Lut3dData lut = definition == null ? null : definition.lutData();
        String kind = definition != null && definition.type() == FilterType.LUT3D ? "LUT" : "PostChain";
        return "Prismod 滤镜调试\n滤镜: " + key.serializedName()
                + "\n类型: " + kind
                + "\n资源包: " + (definition == null ? "unknown" : definition.packNamespace())
                + "\n文件: " + (definition == null || definition.source() == null ? "unknown" : definition.source().getPath())
                + "\nLUT_3D_SIZE: " + (lut == null ? "unknown" : Lut3dData.SIZE)
                + "\n数据点: " + (lut == null ? "unknown" : Lut3dData.POINT_COUNT)
                + "\n色彩空间: sRGB\nDOMAIN_MIN/MAX: " + domain(lut)
                + "\n解析状态: " + (definition == null ? "失败" : kind.equals("LUT") ? (lut == null ? "失败" : "成功") : "成功")
                + "\n纹理上传: " + (WorldFilterRenderer.debugProcessedTexture() == 0 ? "等待" : "成功")
                + "\n最近错误: " + (WorldFilterRenderer.debugError() == null ? "无" : WorldFilterRenderer.debugError());
    }

    private static String domain(Lut3dData lut) {
        if (lut == null) return "unknown";
        float[] min = lut.domainMin(), max = lut.domainMax();
        return "(" + min[0] + ", " + min[1] + ", " + min[2] + ") / (" + max[0] + ", " + max[1] + ", " + max[2] + ")";
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            WorldFilterRenderer.endDebugSession();
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xDD101318);
        graphics.drawString(font, title, panelLeft + 12, panelTop + 10, 0xFFFFFF);
        drawPreview(graphics);
        graphics.drawString(font, Component.literal("调色参数（滚动查看更多）"), panelLeft + 12, settingsTop - 16, 0xCCCCCC);
        graphics.fill(panelLeft + 4, settingsTop - 2, previewRight + 4, settingsBottom, 0x33111111);
        int contentHeight = sliders.size() * 23;
        int maxScroll = Math.max(0, contentHeight - Math.max(0, settingsBottom - settingsTop));
        if (maxScroll > 0) {
            int trackLeft = previewRight + 6;
            int trackRight = trackLeft + 4;
            graphics.fill(trackLeft, settingsTop, trackRight, settingsBottom, 0x66444444);
            int thumbHeight = Math.max(12, (settingsBottom - settingsTop) * (settingsBottom - settingsTop) / contentHeight);
            int thumbY = settingsTop + (settingsBottom - settingsTop - thumbHeight) * settingsScroll / maxScroll;
            graphics.fill(trackLeft, thumbY, trackRight, thumbY + thumbHeight, 0xFFAAAAAA);
        }
        int diagnosticLeft = panelLeft + panelWidth / 2 + 10;
        drawDiagnostics(graphics, diagnosticLeft, panelTop + 36);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPreview(GuiGraphics graphics) {
        graphics.fill(previewLeft, previewTop, previewRight, previewBottom, 0xFF20252B);
        int split = Mth.clamp((int) (previewLeft + (previewRight - previewLeft) * divider),
                previewLeft + (previewRight - previewLeft) / 5,
                previewRight - (previewRight - previewLeft) / 5);
        int originalTexture = WorldFilterRenderer.debugOriginalTexture();
        int processedTexture = WorldFilterRenderer.debugProcessedTexture();
        if (originalTexture != 0 && split > previewLeft) {
            drawTexture(graphics, originalTexture, previewLeft, previewTop, split - previewLeft,
                    previewBottom - previewTop, 0.0F, 1.0F, divider, 0.0F);
        }
        if (processedTexture != 0 && previewRight > split) {
            drawTexture(graphics, processedTexture, split, previewTop, previewRight - split,
                    previewBottom - previewTop, divider, 1.0F, 1.0F, 0.0F);
        }
        graphics.fill(split - 1, previewTop, split + 1, previewBottom, 0xFFFFFFFF);
        graphics.drawString(font, Component.literal("原始"), previewLeft + 5, previewTop + 5, 0xFFFFFF);
        graphics.drawString(font, Component.literal(definition != null && definition.type() == FilterType.LUT3D ? "LUT 预览" : "滤镜预览"), Math.max(split + 5, previewLeft + 5), previewTop + 5, 0xFFFFFF);
        graphics.drawString(font, Component.literal("拖动白线调整分屏"), previewLeft, previewBottom + 5, 0xAAAAAA);
    }

    private void drawDiagnostics(GuiGraphics graphics, int x, int y) {
        String[] lines = diagnostics().split("\\n", -1);
        for (String line : lines) {
            graphics.drawString(font, Component.literal(line), x, y, 0xD0D0D0, false);
            y += 12;
        }
    }

    private static void drawTexture(GuiGraphics graphics, int texture, int x, int y, int width, int height,
                                    float u0, float v0, float u1, float v1) {
        if (width <= 0 || height <= 0) return;
        PoseStack pose = graphics.pose();
        Matrix4f matrix = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, x, y + height, 0).uv(u0, v1).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0).uv(u1, v1).endVertex();
        buffer.vertex(matrix, x + width, y, 0).uv(u1, v0).endVertex();
        buffer.vertex(matrix, x, y, 0).uv(u0, v0).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= previewLeft && mouseX <= previewRight
                && mouseY >= previewTop && mouseY <= previewBottom) {
            int split = (int) (previewLeft + (previewRight - previewLeft) * divider);
            if (Math.abs(mouseX - split) <= 8) {
                draggingDivider = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingDivider) {
            divider = Mth.clamp((float) ((mouseX - previewLeft) / (double) (previewRight - previewLeft)), 0.2F, 0.8F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingDivider) {
            draggingDivider = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelLeft && mouseX <= previewRight + 8
                && mouseY >= settingsTop && mouseY <= settingsBottom) {
            settingsScroll -= (int) Math.signum(delta) * 23;
            arrangeSettings();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private final class SettingSlider extends AbstractSliderButton {
        private final String name;
        private final float min;
        private final float max;
        private float current;

        SettingSlider(String name, float min, float max, float value, int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), normalize(value, min, max));
            this.name = name; this.min = min; this.max = max; this.current = value;
            updateMessage();
            setTooltip(Tooltip.create(Component.literal(name)));
        }

        float current() { return current; }
        void setCurrent(float value) { current = value; this.value = normalize(value, min, max); current = Mth.lerp((float) this.value, min, max); updateMessage(); }
        void resetFromSettings(FilterDebugSettings value) {
            float next = switch (name) {
                case "预览强度" -> value.intensity(); case "曝光 EV" -> value.exposure(); case "对比度" -> value.contrast();
                case "高光" -> value.highlights(); case "阴影" -> value.shadows(); case "饱和度" -> value.saturation();
                case "色温" -> value.temperature(); case "色调" -> value.tint(); default -> value.gamma();
            }; setCurrent(next);
        }
        @Override protected void updateMessage() { setMessage(Component.literal(name + "：" + String.format(java.util.Locale.ROOT, "%.2f", current))); }
        @Override protected void applyValue() { current = Mth.lerp((float) value, min, max); updateMessage(); applySlider(); }
    }

    private static double normalize(float value, float min, float max) {
        return (Mth.clamp(value, min, max) - min) / (max - min);
    }
}
