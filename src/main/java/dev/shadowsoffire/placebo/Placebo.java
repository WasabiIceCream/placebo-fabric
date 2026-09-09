package dev.shadowsoffire.placebo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shadowsoffire.placebo.dynreg.DynRegPayloads;
import dev.shadowsoffire.placebo.dynreg.TagSyncPayload;
import dev.shadowsoffire.placebo.dynreg.tag.DynamicTagManager;
import dev.shadowsoffire.placebo.network.PayloadHelper;
import dev.shadowsoffire.placebo.payloads.ButtonClickPayload;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/**
 * Unofficial Fabric port of Placebo, ported only as far as needed to support the
 * Apotheosis Adventure-module Fabric port — see mod-dev/placebo-fabric/README.md.
 *
 * Port note: upstream's {@code Placebo} constructor/setup wires up commands, datagen
 * field orderings, gear-set/mix registries, tab-filling, and a custom-color system —
 * none of that is ported yet (out of scope for the Adventure module or not yet reached
 * in the port order). This is intentionally a partial entrypoint; expand as more of
 * Placebo gets ported rather than treating this as feature-complete.
 */
public class Placebo implements ModInitializer {

    public static final String MODID = "placebo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        DynamicTagManager.register();

        PayloadHelper.registerPayload(new DynRegPayloads.Start.Provider());
        PayloadHelper.registerPayload(new DynRegPayloads.Content.Provider<>());
        PayloadHelper.registerPayload(new DynRegPayloads.End.Provider());
        PayloadHelper.registerPayload(new TagSyncPayload.Provider());
        PayloadHelper.registerPayload(new ButtonClickPayload.Provider());

        LOGGER.info("Placebo (Fabric port, partial) initializing");
    }

    public static Identifier loc(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

}
