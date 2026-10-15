package com.xkmxz.prismod.client.filter.registry;

import com.xkmxz.prismod.client.pack.PrismodPackLoader;
import com.xkmxz.prismod.api.client.submit.model.CustomFilterMetadata;
import com.xkmxz.prismod.client.application.handle.FilterRegistration;
import com.xkmxz.prismod.api.common.query.state.filter.FilterFailureReason;
import com.xkmxz.prismod.api.common.query.state.lifecycle.FilterRegistrationState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterRegistryTest {
    @Test
    @SuppressWarnings("unchecked")
    void replacedHandleIsClosedEvenWhenReplacementHasResourceFailure() throws Exception {
        // 直接搭建注册表的版本记录，避免测试依赖 Minecraft 客户端和资源加载。
        var registryConstructor = FilterRegistry.class.getDeclaredConstructor();
        registryConstructor.setAccessible(true);
        FilterRegistry registry = registryConstructor.newInstance();
        var key = new com.xkmxz.prismod.client.filter.FilterKey(
                ResourceLocation.fromNamespaceAndPath("example", "replacement"));
        var identity = new FilterRegistry.RegistrationId("owner", key);
        var recordClass = Class.forName(FilterRegistry.class.getName() + "$RegistrationRecord");
        var recordConstructor = recordClass.getDeclaredConstructor(FilterRegistry.RegistrationId.class,
                ResourceLocation.class, CustomFilterMetadata.class, long.class);
        recordConstructor.setAccessible(true);
        Object replacement = recordConstructor.newInstance(identity,
                ResourceLocation.fromNamespaceAndPath("example", "invalid.json"), CustomFilterMetadata.defaults(), 2L);
        var registrationsField = FilterRegistry.class.getDeclaredField("registrations");
        registrationsField.setAccessible(true);
        var registrations = (java.util.Map<Object, Object>) registrationsField.get(registry);
        registrations.put(identity, replacement);
        var failuresField = FilterRegistry.class.getDeclaredField("failures");
        failuresField.setAccessible(true);
        ((java.util.Map<Object, String>) failuresField.get(registry)).put(key, "replacement invalid");
        var handleClass = Class.forName(FilterRegistry.class.getName() + "$Handle");
        var handleConstructor = handleClass.getDeclaredConstructor(FilterRegistry.class,
                FilterRegistry.RegistrationId.class, long.class);
        handleConstructor.setAccessible(true);
        FilterRegistration old = (FilterRegistration) handleConstructor.newInstance(registry, identity, 1L);
        FilterRegistration current = (FilterRegistration) handleConstructor.newInstance(registry, identity, 2L);

        assertEquals(FilterRegistrationState.CLOSED, old.state());
        assertEquals(FilterRegistrationState.FAILED, current.state());
        old.close();
        assertEquals(replacement, registrations.get(identity));
    }

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
