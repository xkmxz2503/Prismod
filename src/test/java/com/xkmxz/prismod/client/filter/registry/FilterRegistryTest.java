package com.xkmxz.prismod.client.filter.registry;

import com.xkmxz.prismod.client.pack.PrismodPackLoader;
import com.xkmxz.prismod.api.client.contract.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.contract.FilterRegistration;
import com.xkmxz.prismod.api.common.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.state.lifecycle.FilterRegistrationState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterRegistryTest {
    @Test
    void legacyShaderPathIsNeverAFilterSource() {
        assertFalse(FilterRegistry.isDiscoverableResource(
                ResourceLocation.fromNamespaceAndPath("minecraft", "shaders/post/blur.json")));
        assertFalse(FilterRegistry.isDiscoverableResource(
                ResourceLocation.fromNamespaceAndPath("example", "shaders/post/debug.json")));
    }

    @Test
    void legacyEmptyChainPathIsNeverAFilterSource() {
        assertFalse(FilterRegistry.isDiscoverableResource(
                ResourceLocation.fromNamespaceAndPath("example", "shaders/post/empty.json")));
    }

    @Test
    void onlyTheDedicatedPrismodPackSourceIsAccepted() {
        assertTrue(PrismodPackLoader.isPrismodPackId(PrismodPackLoader.PACK_ID));
        assertFalse(PrismodPackLoader.isPrismodPackId("vanilla"));
    }

    @Test
    void legacyMetadataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PrismodPackLoader.parseMetadata(
                com.google.gson.JsonParser.parseString("{\"namespace\":\"example\"}").getAsJsonObject()));
    }

    @Test
    void apiRegistrationCannotReplaceAnExistingBuiltInLogicalId() {
        FilterRegistration registration = FilterRegistry.get().register(
                ResourceLocation.fromNamespaceAndPath("prismod", "warm"), "example-mod",
                ResourceLocation.fromNamespaceAndPath("example", "shaders/post/test.json"),
                CustomFilterMetadata.defaults());

        assertEquals(FilterRegistrationState.FAILED, registration.state());
        assertEquals(FilterFailureReason.ID_CONFLICT, registration.failureReason());
        registration.close();
    }
}
