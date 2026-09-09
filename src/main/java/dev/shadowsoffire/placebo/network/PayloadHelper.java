package dev.shadowsoffire.placebo.network;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Preconditions;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Port note (NeoForge -> Fabric): the original registers everything from a single
 * common-sided {@code RegisterPayloadHandlersEvent} subscriber. Fabric has no
 * equivalent deferred event — codec registration ({@link PayloadTypeRegistry}) and
 * server-side receiver registration are both safe to do eagerly from common code and
 * happen in {@link #registerPayload}, but client-side receiver registration
 * ({@link ClientPlayNetworking}) references client-only classes and must be deferred
 * to a client entrypoint — see {@link #registerClientHandlers()}.
 */
public class PayloadHelper {

    private static final Map<CustomPacketPayload.Type<?>, PayloadProvider<?>> ALL_PROVIDERS = new HashMap<>();

    /**
     * Registers a payload using {@link PayloadProvider}.
     * <p>
     * Safe to call from common code (mod {@code onInitialize}). Registers the payload's
     * stream codec on both directions, and wires up the server-side receiver
     * immediately if the payload's flow allows SERVERBOUND traffic. Client-side
     * receiver wiring is deferred — see {@link #registerClientHandlers()}.
     *
     * @param prov An instance of the payload provider.
     */
    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> void registerPayload(PayloadProvider<T> prov) {
        Preconditions.checkNotNull(prov);
        synchronized (ALL_PROVIDERS) {
            if (ALL_PROVIDERS.containsKey(prov.getType())) {
                throw new UnsupportedOperationException("Attempted to register payload provider with duplicate ID: " + prov.getType().id());
            }
            ALL_PROVIDERS.put(prov.getType(), prov);
        }

        var codec = prov.getCodec();
        if (prov.getFlow().isEmpty() || prov.getFlow().get() == PacketFlow.CLIENTBOUND) {
            PayloadTypeRegistry.clientboundPlay().register(prov.getType(), (net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T>) codec);
        }
        if (prov.getFlow().isEmpty() || prov.getFlow().get() == PacketFlow.SERVERBOUND) {
            PayloadTypeRegistry.serverboundPlay().register(prov.getType(), (net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T>) codec);
            ServerPlayNetworking.registerGlobalReceiver(prov.getType(), (payload, ctx) -> prov.handleServer(payload, ctx));
        }
    }

    /**
     * Registers Fabric's client-side receivers for every payload provider registered so far whose
     * flow allows CLIENTBOUND traffic. Must be called from a client entrypoint (after all
     * {@link #registerPayload} calls have run), never from common code.
     */
    @SuppressWarnings("unchecked")
    public static void registerClientHandlers() {
        synchronized (ALL_PROVIDERS) {
            for (PayloadProvider<?> prov : ALL_PROVIDERS.values()) {
                if (prov.getFlow().isEmpty() || prov.getFlow().get() == PacketFlow.CLIENTBOUND) {
                    ClientPlayNetworking.registerGlobalReceiver((CustomPacketPayload.Type<CustomPacketPayload>) prov.getType(), (payload, ctx) -> ((PayloadProvider<CustomPacketPayload>) prov).handleClient(payload, ctx));
                }
            }
        }
    }

}
