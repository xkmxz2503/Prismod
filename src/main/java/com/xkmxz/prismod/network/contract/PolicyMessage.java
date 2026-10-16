package com.xkmxz.prismod.network.contract;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** 协议 3 策略消息：请求身份描述一次操作，策略身份描述持续存在的策略；strength 使用 0-100 百分制。 */
public record PolicyMessage(
        Operation operation,
        UUID requestId,
        UUID policyId,
        String owner,
        ResourceLocation filter,
        float strength,
        int priority
) {
    public enum Operation {
        SELECT,
        OVERRIDE,
        CLEAR_SELECTION,
        CLEAR_OVERRIDE,
        CLEAR_ALL_OVERRIDES
    }

    public PolicyMessage {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(requestId, "requestId");
        boolean requiresPolicy = operation == Operation.SELECT || operation == Operation.OVERRIDE
                || operation == Operation.CLEAR_OVERRIDE;
        if (requiresPolicy) Objects.requireNonNull(policyId, "policyId");
        else if (policyId != null) throw new IllegalArgumentException("整体清理不接受单个策略 ID");
        owner = owner == null ? "" : owner.trim();
        strength = Float.isFinite(strength) ? Math.max(0.0F, Math.min(100.0F, strength)) : 0.0F;
    }

    public static PolicyMessage selection(UUID requestId, UUID policyId, ResourceLocation filter, float strength) {
        return new PolicyMessage(Operation.SELECT, requestId, policyId, "", filter, strength, 0);
    }

    public static PolicyMessage override(UUID requestId, UUID policyId, String owner,
                                         ResourceLocation filter, float strength, int priority) {
        return new PolicyMessage(Operation.OVERRIDE, requestId, policyId, owner, filter, strength, priority);
    }

    public static PolicyMessage clearSelection(UUID requestId) {
        return new PolicyMessage(Operation.CLEAR_SELECTION, requestId, null, "", null, 0.0F, 0);
    }

    public static PolicyMessage clearOverride(UUID requestId, UUID policyId, String owner) {
        return new PolicyMessage(Operation.CLEAR_OVERRIDE, requestId, policyId, owner, null, 0.0F, 0);
    }

    public static PolicyMessage clearAllOverrides(UUID requestId) {
        return new PolicyMessage(Operation.CLEAR_ALL_OVERRIDES, requestId, null, "", null, 0.0F, 0);
    }

    public static void encode(PolicyMessage message, FriendlyByteBuf buffer) {
        buffer.writeEnum(message.operation());
        buffer.writeUUID(message.requestId());
        buffer.writeBoolean(message.policyId() != null);
        if (message.policyId() != null) buffer.writeUUID(message.policyId());
        buffer.writeUtf(message.owner(), 256);
        buffer.writeBoolean(message.filter() != null);
        if (message.filter() != null) buffer.writeResourceLocation(message.filter());
        buffer.writeFloat(message.strength());
        buffer.writeInt(message.priority());
    }

    public static PolicyMessage decode(FriendlyByteBuf buffer) {
        Operation operation = buffer.readEnum(Operation.class);
        UUID requestId = buffer.readUUID();
        UUID policyId = buffer.readBoolean() ? buffer.readUUID() : null;
        String owner = buffer.readUtf(256);
        ResourceLocation filter = buffer.readBoolean() ? buffer.readResourceLocation() : null;
        return new PolicyMessage(operation, requestId, policyId, owner, filter, buffer.readFloat(), buffer.readInt());
    }
}
