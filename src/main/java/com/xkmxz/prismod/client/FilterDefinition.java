package com.xkmxz.prismod.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Runtime metadata for one filter resource. */
public record FilterDefinition(
        FilterKey key,
        ResourceLocation postEffect,
        String translationKey,
        float defaultStrength,
        boolean builtIn
) {
    public Component displayName() {
        if (translationKey != null && !translationKey.isBlank() && I18n.exists(translationKey)) {
            return Component.translatable(translationKey);
        }
        return Component.literal(key.serializedName());
    }

    public float defaultStrength() {
        return FilterState.normalizeStrength(defaultStrength);
    }
}
