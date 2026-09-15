package dev.shadowsoffire.placebo.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;

import dev.shadowsoffire.placebo.dynreg.DynamicRegistry;

/**
 * Captures the up-to-date {@link HolderLookup.Provider} the moment it exists during server
 * bootstrap, instead of waiting on a Fabric lifecycle event or reading the live (stale-tag)
 * {@link net.minecraft.core.RegistryAccess}.
 * <p>
 * Port bug found live, first pass: {@link DynamicRegistry#apply} originally sourced registry
 * context from a {@code MinecraftServer} instance captured by
 * {@code ServerLifecycleEvents.SERVER_STARTING}/{@code START_DATA_PACK_RELOAD} — but both fire
 * <em>after</em> the very first datapack reload at boot (the one that loads every
 * {@code apotheosis:affixes}/{@code gems} file) has already completed, so that first reload always
 * decoded against plain {@link com.mojang.serialization.JsonOps}, failing every field that needs a
 * real dynamic-registry holder (enchantments, etc.) with "Can't access registry".
 * <p>
 * Second pass, found live again: injecting into {@code loadResources}'s static-method parameters
 * and calling {@code registryAccess.compositeAccess()} fixed the enchantment-holder case but not
 * tag references (e.g. {@code "#apotheosis:stoneforming_candidates"}) — they failed with
 * {@code Missing tag}, even though the tag file genuinely exists. Traced (via decompiling
 * {@code WorldLoader.load}/{@code ReloadableServerResources.loadResources}) to a real vanilla
 * two-phase design: static-registry (block/item/etc.) tags are computed into a
 * {@code List<Registry.PendingTags<?>>} early, but only <em>applied</em> into the live, global
 * {@code BuiltInRegistries} objects by {@code ReloadableServerResources.updateComponentsAndStaticRegistryTags()}
 * — called from {@code WorldLoader.load} strictly *after* {@code loadResources()}'s entire
 * reload-listener list (ours included) has already finished. Reading the live
 * {@code RegistryAccess} mid-listener therefore always sees stale (empty) tag content for static
 * registries. Vanilla's own recipes/advancements/loot-table loaders sidestep this entirely: they
 * decode against a separate, already-current {@link HolderLookup.Provider} —
 * {@code ReloadableServerRegistries.LoadResult#lookupWithUpdatedTags()} — built via
 * {@code TagLoader.buildUpdatedLookups(...)} specifically so in-flight reloads see current tags
 * without waiting for the later global apply step. That exact provider is threaded into this
 * class's own constructor as {@code loadingContext} — capturing it there, instead of the raw
 * {@code RegistryAccess} from {@code loadResources}'s parameters, gives {@code DynamicRegistry} the
 * same up-to-date tag view vanilla's own listeners use.
 */
@Mixin(ReloadableServerResources.class)
public class ReloadableServerResourcesMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void placebo$captureRegistryLookup(LayeredRegistryAccess<RegistryLayer> fullLayers, HolderLookup.Provider loadingContext,
        FeatureFlagSet enabledFeatures, Commands.CommandSelection commandSelection, List<Registry.PendingTags<?>> postponedTags,
        PermissionSet functionCompilationPermissions, List<DataComponentInitializers.PendingComponents<?>> newComponents, CallbackInfo ci) {
        DynamicRegistry.setRegistryLookup(loadingContext);
    }

}
