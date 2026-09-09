package dev.shadowsoffire.placebo.dynreg;

import java.util.Optional;

import org.jetbrains.annotations.ApiStatus;

import com.mojang.datafixers.util.Either;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.network.PayloadProvider;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Port note (NeoForge -> Fabric): {@code ConnectionType.NEOFORGE} tagging on the
 * deferred-decode {@link RegistryFriendlyByteBuf} in {@code Content.Provider} was
 * dropped — Fabric's {@link RegistryFriendlyByteBuf} constructor doesn't take a
 * connection-type marker.
 */
@ApiStatus.Internal
public class DynRegPayloads {

    public static record Start(Identifier id) implements CustomPacketPayload {

        public static final Type<Start> TYPE = new Type<>(Placebo.loc("reload_sync_start"));

        public static final StreamCodec<FriendlyByteBuf, Start> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, Start::id,
            Start::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static class Provider implements PayloadProvider<Start> {

            @Override
            public Type<Start> getType() {
                return TYPE;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, Start> getCodec() {
                return CODEC;
            }

            @Override
            public void handleClient(Start msg, ClientPlayNetworking.Context ctx) {
                SyncManagement.initSync(msg.id);
            }

            @Override
            public Optional<PacketFlow> getFlow() {
                return Optional.of(PacketFlow.CLIENTBOUND);
            }
        }
    }

    public static record Content<V>(Identifier id, Identifier key, Either<V, ByteBuf> item) implements CustomPacketPayload {

        public static final Type<Content<?>> TYPE = new Type<>(Placebo.loc("reload_sync_content"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Content<?>> CODEC = StreamCodec.of(Content::write, Content::read);

        public Content(Identifier id, Identifier key, V item) {
            this(id, key, Either.left(item));
        }

        public Content(Identifier id, Identifier key, ByteBuf buf) {
            this(id, key, Either.right(buf));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static <V> void write(RegistryFriendlyByteBuf buf, Content<V> payload) {
            buf.writeIdentifier(payload.id);
            buf.writeIdentifier(payload.key);
            SyncManagement.writeItem(payload.id, payload.item.orThrow(), buf);
        }

        /**
         * Reads a content payload. We defer deserialization of the underlying object, since it may depend on the state of
         * other registries that are being setup on the main thread.
         */
        public static <V> Content<V> read(RegistryFriendlyByteBuf buf) {
            Identifier id = buf.readIdentifier();
            Identifier key = buf.readIdentifier();

            int size = buf.writerIndex() - buf.readerIndex();
            ByteBuf itemBuf = Unpooled.buffer(size, size);
            buf.readBytes(itemBuf);
            return new Content<>(id, key, itemBuf);
        }

        public static class Provider<V> implements PayloadProvider<Content<?>> {

            @Override
            public Type<Content<?>> getType() {
                return TYPE;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, Content<?>> getCodec() {
                return CODEC;
            }

            @Override
            public void handleClient(Content<?> msg, ClientPlayNetworking.Context ctx) {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(msg.item.right().get(), ctx.player().registryAccess());

                try {
                    V value = SyncManagement.readItem(msg.id, buf);
                    SyncManagement.acceptItem(msg.id, msg.key, value);
                }
                catch (Exception ex) {
                    Placebo.LOGGER.error("Failure when deserializing a dynamic registry object via network: Registry: {}, Object ID: {}", msg.id, msg.key);
                    throw ex;
                }
            }

            @Override
            public Optional<PacketFlow> getFlow() {
                return Optional.of(PacketFlow.CLIENTBOUND);
            }
        }
    }

    public static record End(Identifier id) implements CustomPacketPayload {

        public static final Type<End> TYPE = new Type<>(Placebo.loc("reload_sync_end"));

        public static final StreamCodec<FriendlyByteBuf, End> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, End::id,
            End::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static class Provider implements PayloadProvider<End> {

            @Override
            public Type<End> getType() {
                return TYPE;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, End> getCodec() {
                return CODEC;
            }

            @Override
            public void handleClient(End msg, ClientPlayNetworking.Context ctx) {
                SyncManagement.endSync(msg.id);
            }

            @Override
            public Optional<PacketFlow> getFlow() {
                return Optional.of(PacketFlow.CLIENTBOUND);
            }
        }
    }
}
