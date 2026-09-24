package com.xkmxz.prismod.server.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Owns Prismod's optional common/server network protocol. */
public final class PrismodNetwork {
    public static final ResourceLocation CHANNEL_ID =
            ResourceLocation.fromNamespaceAndPath("prismod", "main");
    public static final String PROTOCOL_VERSION = "1";
    public static final String ABSENT_VERSION = "ABSENT";
    public static final String VANILLA_VERSION = "ACCEPTVANILLA";

    private static SimpleChannel channel;

    private PrismodNetwork() {
    }

    public static synchronized void initialize() {
        if (channel != null) return;
        channel = NetworkRegistry.newSimpleChannel(
                CHANNEL_ID,
                () -> PROTOCOL_VERSION,
                PrismodNetwork::acceptsProtocolVersion,
                PrismodNetwork::acceptsProtocolVersion);
        channel.registerMessage(0, EmptyPacket.class, EmptyPacket::encode, EmptyPacket::decode,
                EmptyPacket::handle);
    }

    public static boolean acceptsProtocolVersion(String version) {
        return PROTOCOL_VERSION.equals(version)
                || ABSENT_VERSION.equals(version)
                || VANILLA_VERSION.equals(version);
    }

    public static boolean isInitialized() {
        return channel != null;
    }

    public static SimpleChannel channel() {
        initialize();
        return channel;
    }

    /** A reserved no-payload packet for future protocol expansion. */
    public static final class EmptyPacket {
        public EmptyPacket() {
        }

        static void encode(EmptyPacket packet, FriendlyByteBuf buffer) {
        }

        static EmptyPacket decode(FriendlyByteBuf buffer) {
            return new EmptyPacket();
        }

        private static void handle(EmptyPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            contextSupplier.get().setPacketHandled(true);
        }
    }
}
