package dev.shadowsoffire.placebo.systems.mixes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.PlaceboClient;
import dev.shadowsoffire.placebo.dynreg.DynamicRegistry;
import dev.shadowsoffire.placebo.dynreg.RegistrySerializer;
import dev.shadowsoffire.placebo.systems.mixes.JsonMix.Type;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.alchemy.PotionBrewing;

/**
 * Data-driven brewing mixes ({@code data/<ns>/placebo/brewing_mixes/}), added to vanilla's {@link PotionBrewing}.
 * <p>
 * Port note (NeoForge -> Fabric): upstream reads the running server through NeoForge's {@code ServerLifecycleHooks}
 * and applies the mixes in {@code ServerAboutToStartEvent}; here the server is tracked with Fabric's
 * {@link ServerLifecycleEvents} and the mixes are applied in {@code SERVER_STARTING}. Adding is idempotent (a mix
 * already present is not added twice), since the client and server of an integrated game share this registry.
 */
public class MixRegistry extends DynamicRegistry<JsonMix<?>> {

    public static final MixRegistry INSTANCE = new MixRegistry();

    @Nullable
    private static MinecraftServer currentServer;

    public MixRegistry() {
        super(Placebo.LOGGER, Placebo.loc("brewing_mixes"), RegistrySerializer.synced(JsonMix.CODEC));
    }

    @Override
    public void registerToBus() {
        super.registerToBus();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            currentServer = server;
            applyMixes();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
    }

    @Override
    protected void beginReload(ReloadType type) {
        for (PotionBrewing brewing : resolveBrewing()) {
            this.removeAll(brewing);
        }
        super.beginReload(type);
    }

    @Override
    protected void onReload(ReloadType type) {
        super.onReload(type);
        for (PotionBrewing brewing : resolveBrewing()) {
            this.addAll(brewing);
        }
    }

    /**
     * Called when the server starts, since the first reload on a dedicated server happens before the server (and its
     * {@link PotionBrewing}) exists.
     */
    public static void applyMixes() {
        for (PotionBrewing brewing : resolveBrewing()) {
            INSTANCE.addAll(brewing);
        }
    }

    /**
     * The {@link PotionBrewing} instances currently in use: the client's (on the client, once in a world) and the
     * running server's.
     */
    private static List<PotionBrewing> resolveBrewing() {
        List<PotionBrewing> registries = new ArrayList<>();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            PotionBrewing client = PlaceboClient.getBrewingRegistry();
            if (client != null) registries.add(client);
        }
        if (currentServer != null) {
            registries.add(currentServer.potionBrewing());
        }
        return registries;
    }

    @SuppressWarnings("unchecked")
    private static List<PotionBrewing.Mix<?>> getMixList(PotionBrewing brewing, Type type) {
        return (List<PotionBrewing.Mix<?>>) (Object) switch (type) {
            case POTION -> brewing.potionMixes;
            case CONTAINER -> brewing.containerMixes;
        };
    }

    private static void makeMutable(PotionBrewing brewing) {
        if (!(brewing.containerMixes instanceof ArrayList)) brewing.containerMixes = new ArrayList<>(brewing.containerMixes);
        if (!(brewing.potionMixes instanceof ArrayList)) brewing.potionMixes = new ArrayList<>(brewing.potionMixes);
    }

    private Set<PotionBrewing.Mix<?>> ownMixes() {
        Set<PotionBrewing.Mix<?>> set = Collections.newSetFromMap(new IdentityHashMap<>());
        this.getValues().forEach(mix -> set.add(mix.mix()));
        return set;
    }

    private void removeAll(PotionBrewing brewing) {
        makeMutable(brewing);
        Set<PotionBrewing.Mix<?>> own = this.ownMixes();
        brewing.potionMixes.removeIf(own::contains);
        brewing.containerMixes.removeIf(own::contains);
    }

    private void addAll(PotionBrewing brewing) {
        this.removeAll(brewing);
        this.getValues().forEach(mix -> getMixList(brewing, mix.type()).add(mix.mix()));
    }

}
