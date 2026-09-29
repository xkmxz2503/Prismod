package com.xkmxz.prismod.network.contract;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Frozen policy payload shared by the server sender and client receiver. */
public record PolicyMessage(
        Operation operation,
        UUID requestId,
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
        operation = operation == null ? Operation.CLEAR_SELECTION : operation;
        requestId = requestId == null ? new UUID(0L, 0L) : requestId;
        owner = owner == null ? "" : owner.trim();
        strength = Float.isFinite(strength) ? Math.max(0.0F, Math.min(1.0F, strength)) : 0.0F;
    }

    public static PolicyMessage selection(UUID id, ResourceLocation filter, float strength) {
        return new PolicyMessage(Operation.SELECT, id, "", filter, strength, 0);
    }

    public static PolicyMessage override(UUID id, String owner, ResourceLocation filter, float strength, int priority) {
        return new PolicyMessage(Operation.OVERRIDE, id, owner, filter, strength, priority);
    }

    public static PolicyMessage clearSelection(UUID id) {
        return new PolicyMessage(Operation.CLEAR_SELECTION, id, "", null, 0.0F, 0);
    }

    public static PolicyMessage clearOverride(UUID id, String owner) {
        return new PolicyMessage(Operation.CLEAR_OVERRIDE, id, owner, null, 0.0F, 0);
    }

    public static PolicyMessage clearAllOverrides(UUID id) {
        return new PolicyMessage(Operation.CLEAR_ALL_OVERRIDES, id, "", null, 0.0F, 0);
    }

    public static void encode(PolicyMessage message, FriendlyByteBuf buffer) {
        buffer.writeEnum(message.operation());
        buffer.writeUUID(message.requestId());
        buffer.writeUtf(message.owner(), 256);
        buffer.writeBoolean(message.filter() != null);
        if (message.filter() != null) buffer.writeResourceLocation(message.filter());
        buffer.writeFloat(message.strength());
        buffer.writeInt(message.priority());
    }

    public static PolicyMessage decode(FriendlyByteBuf buffer) {
        Operation operation = buffer.readEnum(Operation.class);
        UUID requestId = buffer.readUUID();
        String owner = buffer.readUtf(256);
        ResourceLocation filter = buffer.readBoolean() ? buffer.readResourceLocation() : null;
        return new PolicyMessage(operation, requestId, owner, filter, buffer.readFloat(), buffer.readInt());
    }
}
