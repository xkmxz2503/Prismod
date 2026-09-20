package com.xkmxz.prismod.client.filter;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.xkmxz.prismod.client.pack.PrismodPackLoader;

import java.util.Optional;

/** Runtime metadata for one filter resource. */
public record FilterDefinition(
        FilterKey key,
        FilterType type,
        ResourceLocation postEffect,
        ResourceLocation source,
        String translationKey,
        float defaultStrength,
        boolean builtIn,
        String packNamespace,
        Lut3dData lutData
) {
    public FilterDefinition(FilterKey key, ResourceLocation postEffect, String translationKey,
                            float defaultStrength, boolean builtIn) {
        this(key, postEffect == null ? FilterType.POST_CHAIN : FilterType.POST_CHAIN, postEffect,
                postEffect, translationKey, defaultStrength, builtIn, null, null);
    }

    public FilterDefinition(FilterKey key, ResourceLocation postEffect, String translationKey,
                            float defaultStrength, boolean builtIn, String packNamespace) {
        this(key, FilterType.POST_CHAIN, postEffect, postEffect, translationKey, defaultStrength,
                builtIn, packNamespace, null);
    }

    public FilterDefinition(FilterKey key, FilterType type, ResourceLocation postEffect,
                            ResourceLocation source, String translationKey, float defaultStrength,
                            boolean builtIn, String packNamespace, Lut3dData lutData) {
        this.key = key;
        this.type = type;
        this.postEffect = postEffect;
        this.source = source;
        this.translationKey = translationKey;
        this.defaultStrength = defaultStrength;
        this.builtIn = builtIn;
        this.packNamespace = packNamespace;
        this.lutData = lutData;
    }

    public Component displayName() {
        if (translationKey != null && !translationKey.isBlank()) {
            Optional<String> packTranslation = PrismodPackLoader.translate(key.id(), packNamespace, translationKey);
            if (packTranslation.isPresent()) return Component.literal(packTranslation.get());
            // API 注册的滤镜没有 Prismod 资源包归属时，保留模组语言表兼容性。
            if (packNamespace == null && I18n.exists(translationKey)) return Component.translatable(translationKey);
        }
        return Component.literal(key.serializedName());
    }

    public float defaultStrength() {
        return FilterState.normalizeStrength(defaultStrength);
    }
}
