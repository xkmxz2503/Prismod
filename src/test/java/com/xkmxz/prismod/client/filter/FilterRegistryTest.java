package com.xkmxz.prismod.client.filter;

import com.xkmxz.prismod.client.pack.PrismodPackLoader;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterRegistryTest {
    @Test
    void excludesVanillaNamespaceEvenWhenAResourcePackIsMarked() {
        assertFalse(FilterRegistry.isDiscoverableResource(
                ResourceLocation.fromNamespaceAndPath("minecraft", "shaders/post/blur.json")));
        assertTrue(FilterRegistry.isDiscoverableResource(
                ResourceLocation.fromNamespaceAndPath("example", "shaders/post/debug.json")));
    }

    @Test
    void discoversNonVanillaPostEffectsExceptTheInternalEmptyChain() {
        assertFalse(FilterRegistry.isDiscoverableResource(
                ResourceLocation.fromNamespaceAndPath("example", "shaders/post/empty.json")));
    }

    @Test
    void onlyTheDedicatedPrismodPackSourceIsAccepted() {
        assertTrue(PrismodPackLoader.isPrismodPackId(PrismodPackLoader.PACK_ID));
        assertFalse(PrismodPackLoader.isPrismodPackId("vanilla"));
    }

    @Test
    void metadataRequiresANonReservedNamespace() {
        assertTrue(PrismodPackLoader.parseMetadata(
                com.google.gson.JsonParser.parseString("{\"namespace\":\"example\"}").getAsJsonObject())
                .namespace().equals("example"));
        assertThrows(IllegalArgumentException.class, () -> PrismodPackLoader.parseMetadata(
                com.google.gson.JsonParser.parseString("{\"namespace\":\"minecraft\"}").getAsJsonObject()));
    }
}
