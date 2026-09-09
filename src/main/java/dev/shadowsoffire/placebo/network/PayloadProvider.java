package dev.shadowsoffire.placebo.network;

import java.util.Optional;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A Payload Provider encapsulates the default components that make up a custom payload packet registration.
 *
 * Port note (NeoForge -> Fabric): the original wraps NeoForge's unified
 * {@code IPayloadContext} (same type for client and server handling) plus
 * protocol/version/optional/thread configuration that Fabric's networking API doesn't
 * have equivalents for (Fabric's {@code registerGlobalReceiver} always runs on PLAY,
 * always on the main client/server thread, has no version handshake, and payloads are
 * either present or the receiver simply never fires). This version uses Fabric's
 * separate {@link ClientPlayNetworking.Context}/{@link ServerPlayNetworking.Context}
 * types directly instead of inventing a unified context type.
 *
 * @param <T> The type of the payload.
 */
public interface PayloadProvider<T extends CustomPacketPayload> {

    /**
     * @return The type of the payload being registered. Must match {@link CustomPacketPayload#type()}.
     */
    CustomPacketPayload.Type<T> getType();

    /**
     * @return The {@link StreamCodec} responsible for encoding/decoding the payload.
     */
    StreamCodec<? super RegistryFriendlyByteBuf, T> getCodec();

    /**
     * Handle the payload when received on the client. Only called if {@link #getFlow()} allows
     * CLIENTBOUND traffic; registration of the actual Fabric receiver happens in
     * {@link PayloadHelper#registerClientHandlers()}, which must be called from a client entrypoint.
     *
     * @param msg The message to handle.
     * @param ctx Relevant network context information.
     */
    default void handleClient(T msg, ClientPlayNetworking.Context ctx) {}

    /**
     * Handle the payload when received on the server. Only called if {@link #getFlow()} allows
     * SERVERBOUND traffic.
     *
     * @param msg The message to handle.
     * @param ctx Relevant network context information.
     */
    default void handleServer(T msg, ServerPlayNetworking.Context ctx) {}

    /**
     * Gets the network direction in which this payload may be sent.<br>
     * {@link Optional#empty()} means both directions are supported.
     *
     * @return The optional containing the valid network direction, or empty if both directions are supported.
     */
    Optional<PacketFlow> getFlow();

}
