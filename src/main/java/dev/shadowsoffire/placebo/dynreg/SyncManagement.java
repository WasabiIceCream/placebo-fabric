package dev.shadowsoffire.placebo.dynreg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.jetbrains.annotations.ApiStatus;

import dev.shadowsoffire.placebo.Placebo;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Internal class for sync management.
 *
 * Port note (NeoForge -> Fabric): the original fires all sync on a single
 * {@code OnDatapackSyncEvent} (covers both player-join and post-{@code /reload}). Fabric
 * has no equivalent single event, so this hooks {@link ServerPlayConnectionEvents#JOIN}
 * (sync to the joining player only) and {@link ServerLifecycleEvents#END_DATA_PACK_RELOAD}
 * (sync to every connected player) separately, both funnelled into
 * {@link DynamicRegistry#sync}.
 */
@ApiStatus.Internal
class SyncManagement {

    private static final Map<Identifier, DynamicRegistry<?>> SYNC_REGISTRY = new LinkedHashMap<>();

    /**
     * Registers a {@link DynamicRegistry} for syncing.
     *
     * @param listener The listener to register.
     * @throws UnsupportedOperationException if the listener is not a synced listener.
     * @throws UnsupportedOperationException if the listener is already registered to the sync registry.
     */
    static void registerForSync(DynamicRegistry<?> listener) {
        if (!listener.serializer.isSynced()) {
            throw new UnsupportedOperationException("Attempted to register the non-synced JSON Reload Listener " + listener.id + " as a synced listener!");
        }
        synchronized (SYNC_REGISTRY) {
            if (SYNC_REGISTRY.containsKey(listener.id)) {
                throw new UnsupportedOperationException("Attempted to register the JSON Reload Listener for syncing " + listener.id + " but one already exists!");
            }
            if (SYNC_REGISTRY.isEmpty()) {
                ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncAll(server, handler.getPlayer()));
                ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
                    if (success) syncAll(server, null);
                });
            }
            SYNC_REGISTRY.put(listener.id, listener);
        }
    }

    /**
     * Begins the sync for a specific listener.
     *
     * @param id The id of the listener being synced.
     */
    public static void initSync(Identifier id) {
        ifPresent(id, registry -> {
            registry.staged.clear();
            registry.stagedTags.clear();
        });
        Placebo.LOGGER.info("Starting sync for {}", id);
    }

    /**
     * Write an item (with the same type as the listener) to the network.
     *
     * @param <V>   The type of item being written.
     * @param id    The id of the listener.
     * @param value The value being written.
     * @param buf   The buffer being written to.
     */
    @SuppressWarnings("unchecked")
    public static <V> void writeItem(Identifier id, V value, RegistryFriendlyByteBuf buf) {
        ifPresent(id, registry -> {
            StreamCodec<RegistryFriendlyByteBuf, V> codec = (StreamCodec<RegistryFriendlyByteBuf, V>) registry.serializer.streamCodec();
            codec.encode(buf, value);
        });
    }

    /**
     * Reads an item from the network, via the listener's stream codec.
     *
     * @param <V> The type of item being read.
     * @param id  The id of the listener.
     * @param buf The buffer being read from.
     * @return An object of type V as deserialized from the network.
     */
    @SuppressWarnings("unchecked")
    public static <V> V readItem(Identifier id, RegistryFriendlyByteBuf buf) {
        var registry = SYNC_REGISTRY.get(id);
        if (registry == null) {
            throw new RuntimeException("Received sync packet for unknown registry: " + id);
        }
        return ((StreamCodec<RegistryFriendlyByteBuf, V>) registry.serializer.streamCodec()).decode(buf);
    }

    /**
     * Stages an item to a listener.
     *
     * @param <V>   The type of the item being staged.
     * @param id    The id of the listener.
     * @param key   The id of the entry being staged.
     * @param value The object being staged.
     */
    @SuppressWarnings("unchecked")
    public static <V> void acceptItem(Identifier id, Identifier key, V value) {
        ifPresent(id, registry -> ((Map<Identifier, V>) registry.staged).put(key, value));
    }

    /**
     * Stages the resolved tag map for a listener. Applied during {@link #endSync(Identifier)} via
     * {@link DynamicRegistry#bindTags}.
     *
     * @param id   The id of the listener.
     * @param tags The resolved tag map (tag id → list of entry ids).
     */
    public static void acceptTags(Identifier id, Map<Identifier, List<Identifier>> tags) {
        ifPresent(id, registry -> {
            registry.stagedTags.clear();
            registry.stagedTags.putAll(tags);
        });
    }

    /**
     * Ends the sync for a specific listener.
     * This will delete current data, push staged data to live, and call the appropriate methods for reloading.
     *
     * @param id The id of the listener.
     * @implNote Only called on the logical client.
     */
    public static void endSync(Identifier id) {
        boolean singleplayer = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT && Minecraft.getInstance().getSingleplayerServer() != null;
        if (singleplayer) {
            // On a singleplayer host, we have to re-register a copy of the original data instead of the synced data
            // since the synced data may not contain the "full" information from the server.
            ifPresent(id, DynamicRegistry::processIntegratedClientReload);
        }
        else {
            ifPresent(id, DynamicRegistry::processDedicatedClientReload);
        }
        Placebo.LOGGER.info("Completed sync for {}", id);
    }

    /**
     * Executes an action if the specified id is present in the sync registry.
     */
    private static void ifPresent(Identifier id, Consumer<DynamicRegistry<?>> consumer) {
        DynamicRegistry<?> value = SYNC_REGISTRY.get(id);
        if (value != null) {
            consumer.accept(value);
        }
    }

    private static void syncAll(net.minecraft.server.MinecraftServer server, @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player) {
        dependencyOrdered().forEach(r -> r.sync(server, player));
    }

    /**
     * Orders the synced registries so that a registry is synced only after every other synced registry
     * it depends on (per {@link DynamicRegistry#getFabricDependencies()}) has already been synced.
     * <p>
     * Port note: {@link DynamicRegistry#getFabricDependencies()} was originally added only to order the
     * server-side datapack reload (via {@code IdentifiableResourceReloadListener}), e.g. so
     * {@code AffixRegistry}/{@code RarityOverrideRegistry} apply after {@code RarityRegistry}. That
     * ordering has no bearing on network sync, which previously just iterated this map in whatever order
     * the registries' classes happened to get loaded in — found live when a client got disconnected with
     * "Network Protocol Error" because affix sync packets referencing {@code DynamicHolder<LootRarity>}
     * arrived and were decoded before the rarity registry's sync had bound its holders, throwing
     * "Trying to access unbound value" NullPointerExceptions. Reusing the same dependency declarations to
     * order the sync fixes this for every current and future dependent registry pair, not just this one.
     */
    private static List<DynamicRegistry<?>> dependencyOrdered() {
        List<DynamicRegistry<?>> ordered = new ArrayList<>(SYNC_REGISTRY.size());
        Set<Identifier> visited = new HashSet<>();
        Set<Identifier> visiting = new HashSet<>();
        for (DynamicRegistry<?> registry : SYNC_REGISTRY.values()) {
            visit(registry, visited, visiting, ordered);
        }
        return ordered;
    }

    private static void visit(DynamicRegistry<?> registry, Set<Identifier> visited, Set<Identifier> visiting, List<DynamicRegistry<?>> ordered) {
        Identifier id = registry.getFabricId();
        if (visited.contains(id)) return;
        if (!visiting.add(id)) {
            throw new IllegalStateException("Cyclic dependency detected among synced dynamic registries involving " + id);
        }
        for (Identifier dep : registry.getFabricDependencies()) {
            DynamicRegistry<?> depRegistry = SYNC_REGISTRY.get(dep);
            if (depRegistry != null) {
                visit(depRegistry, visited, visiting, ordered);
            }
        }
        visiting.remove(id);
        visited.add(id);
        ordered.add(registry);
    }
}
