package dev.shadowsoffire.placebo.dynreg.tag;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import dev.shadowsoffire.placebo.Placebo;
import dev.shadowsoffire.placebo.dynreg.DynamicRegistry;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Reload listener responsible for loading tag JSON files for every constructed {@link DynamicRegistry}.
 * <p>
 * Runs after every {@code DynamicRegistry} reload listener, via the {@link IdentifiableResourceReloadListener}
 * dependency edge each {@code DynamicRegistry} declares back to {@link #ID} (see
 * {@link DynamicRegistry#getFabricDependencies()}). The {@link #prepare} step scans tag JSON files
 * (off-thread, parallel to other reload listeners' prepare phases). The {@link #apply} step resolves
 * the scanned entries against the now-populated registries and binds the resolved tags — resolution must
 * happen during apply because preparation runs in parallel with content listeners' prepare and the registry
 * content isn't yet populated at that point.
 *
 * @see DynamicRegistry#bindTags(Map)
 */
public class DynamicTagManager extends SimplePreparableReloadListener<Map<DynamicRegistry<?>, ScannedTags<?>>> implements IdentifiableResourceReloadListener {

    public static final Identifier ID = Placebo.loc("dynamic_registry_tags");

    public static final DynamicTagManager INSTANCE = new DynamicTagManager();

    /**
     * Registers this listener with Fabric's resource-reload pipeline. Must be called once during common setup,
     * before any {@link DynamicRegistry#registerToBus()} calls (order doesn't actually matter for correctness —
     * dependency ordering is resolved by {@link #getFabricId()} regardless of registration order — but matching
     * upstream's registration order avoids surprises).
     */
    public static void register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(INSTANCE);
    }

    @Override
    protected Map<DynamicRegistry<?>, ScannedTags<?>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<DynamicRegistry<?>, ScannedTags<?>> result = new IdentityHashMap<>();
        for (DynamicRegistry<?> registry : DynamicRegistry.allRegistries().values()) {
            result.put(registry, scanFor(registry, manager));
        }
        return result;
    }

    private static <R> ScannedTags<R> scanFor(DynamicRegistry<R> registry, ResourceManager manager) {
        TagLoader<R> loader = new TagLoader<>(registry, registry.getLogger());
        return new ScannedTags<>(loader, loader.scan(manager, JsonOps.INSTANCE));
    }

    @Override
    protected void apply(Map<DynamicRegistry<?>, ScannedTags<?>> data, ResourceManager manager, ProfilerFiller profiler) {
        for (Map.Entry<DynamicRegistry<?>, ScannedTags<?>> entry : data.entrySet()) {
            DynamicRegistry<?> registry = entry.getKey();
            Map<Identifier, List<Identifier>> resolved = entry.getValue().resolve();
            registry.bindTags(resolved);
            if (!resolved.isEmpty()) {
                registry.getLogger().info("Loaded {} tags for {}.", resolved.size(), registry.getId());
            }
        }
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
