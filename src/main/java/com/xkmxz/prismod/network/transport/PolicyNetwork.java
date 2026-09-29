package com.xkmxz.prismod.network.transport;

import com.xkmxz.prismod.network.contract.PolicyMessage;
import com.xkmxz.prismod.network.contract.PolicyTransport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Forge implementation of the frozen policy transport contract. */
public final class PolicyNetwork implements PolicyTransport {
    public static final ResourceLocation CHANNEL_ID = ResourceLocation.fromNamespaceAndPath(
            PolicyTransport.CHANNEL_NAMESPACE, PolicyTransport.CHANNEL_PATH);
    public static final String PROTOCOL_VERSION = PolicyTransport.PROTOCOL_VERSION;
    private static final PolicyNetwork INSTANCE = new PolicyNetwork();
    private volatile SimpleChannel channel;
    private volatile Consumer<PolicyMessage> clientSink;

    private PolicyNetwork() {
    }

    public static PolicyNetwork get() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (channel != null) return;
        channel = NetworkRegistry.newSimpleChannel(CHANNEL_ID, () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
        channel.registerMessage(0, PolicyPacket.class, PolicyPacket::encode, PolicyPacket::decode,
                (packet, contextSupplier) -> handle(packet, contextSupplier));
    }

    public boolean isInitialized() {
        return channel != null;
    }

    @Override
    public void registerClientSink(Consumer<PolicyMessage> sink) {
        clientSink = sink;
    }

    @Override
    public void broadcast(PolicyMessage message) {
        if (channel == null) return;
        try {
            channel.send(PacketDistributor.ALL.noArg(), new PolicyPacket(message));
        } catch (RuntimeException ignored) {
            // Unit tests and optional server environments may not have a network bus.
        }
    }

    @Override
    public void send(ServerPlayer player, PolicyMessage message) {
        if (player == null || channel == null) return;
        try {
            channel.send(PacketDistributor.PLAYER.with(() -> player), new PolicyPacket(message));
        } catch (RuntimeException ignored) {
            // Unit tests and optional server environments may not have a network bus.
        }
    }

    private void handle(PolicyPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Consumer<PolicyMessage> sink = clientSink;
            if (sink != null) sink.accept(packet.message());
        });
        context.setPacketHandled(true);
    }

    private record PolicyPacket(PolicyMessage message) {
        private static void encode(PolicyPacket packet, FriendlyByteBuf buffer) {
            PolicyMessage.encode(packet.message(), buffer);
        }

        private static PolicyPacket decode(FriendlyByteBuf buffer) {
            return new PolicyPacket(PolicyMessage.decode(buffer));
        }
    }
}
