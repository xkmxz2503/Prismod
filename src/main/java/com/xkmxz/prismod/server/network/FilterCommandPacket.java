package com.xkmxz.prismod.server.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Versioned payload shared by the server sender and client-side receiver. */
public record FilterCommandPacket(
        Operation operation,
        UUID requestId,
        String owner,
        ResourceLocation filter,
        float strength,
        int priority
) {
    public enum Operation { SELECT, OVERRIDE, CLEAR_SELECTION, CLEAR_OVERRIDE, CLEAR_ALL_OVERRIDES }

    public FilterCommandPacket {
        operation = operation == null ? Operation.CLEAR_SELECTION : operation;
        requestId = requestId == null ? new UUID(0L, 0L) : requestId;
        owner = owner == null ? "" : owner;
        strength = Float.isFinite(strength) ? Math.max(0.0F, Math.min(1.0F, strength)) : 0.0F;
    }

    public static FilterCommandPacket selection(UUID id, ResourceLocation filter, float strength) {
        return new FilterCommandPacket(Operation.SELECT, id, "", filter, strength, 0);
    }

    public static FilterCommandPacket override(UUID id, String owner, ResourceLocation filter, float strength, int priority) {
        return new FilterCommandPacket(Operation.OVERRIDE, id, owner, filter, strength, priority);
    }

    public static FilterCommandPacket clearOverride(UUID id, String owner) {
        return new FilterCommandPacket(Operation.CLEAR_OVERRIDE, id, owner, null, 0.0F, 0);
    }

    public static FilterCommandPacket clearAllOverrides(UUID id) {
        return new FilterCommandPacket(Operation.CLEAR_ALL_OVERRIDES, id, "", null, 0.0F, 0);
    }

    public static void encode(FilterCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.operation());
        buffer.writeUUID(packet.requestId());
        buffer.writeUtf(packet.owner(), 256);
        buffer.writeBoolean(packet.filter() != null);
        if (packet.filter() != null) buffer.writeResourceLocation(packet.filter());
        buffer.writeFloat(packet.strength());
        buffer.writeInt(packet.priority());
    }

    public static FilterCommandPacket decode(FriendlyByteBuf buffer) {
        Operation operation = buffer.readEnum(Operation.class);
        UUID requestId = buffer.readUUID();
        String owner = buffer.readUtf(256);
        ResourceLocation filter = buffer.readBoolean() ? buffer.readResourceLocation() : null;
        return new FilterCommandPacket(operation, requestId, owner, filter, buffer.readFloat(), buffer.readInt());
    }

    public static void handleNoop(FilterCommandPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context networkContext = context.get();
        networkContext.enqueueWork(() -> {
            try {
                Class<?> receiver = Class.forName("com.xkmxz.prismod." + "client.network.PrismodClientNetwork");
                receiver.getMethod("handle", FilterCommandPacket.class).invoke(null, packet);
            } catch (ReflectiveOperationException ignored) {
                // Dedicated servers intentionally do not contain the client receiver.
            }
        });
        networkContext.setPacketHandled(true);
    }
}
