package dev.shadowsoffire.placebo.json;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

import net.minecraft.resources.Identifier;

public class JsonUtil {

    /**
     * Checks if an item is empty, and if it is, returns false and logs the key.
     */
    public static boolean checkAndLogEmpty(JsonElement e, Identifier id, Identifier regId, Logger logger) {
        String s = e.toString();
        if (s.isEmpty() || "{}".equals(s)) {
            logger.error("Ignoring {} item with id {} as it is empty.  Please switch to a condition-false json instead of an empty one.", regId, id);
            return false;
        }
        return true;
    }

    /**
     * Checks the conditions on a Json, and returns true if they are met.
     * <p>
     * Port note: upstream reads NeoForge's {@code "neoforge:conditions"}. Here the entry may carry Fabric's
     * {@code "fabric:load_conditions"} (a single condition object, or an array that must all pass), decoded with
     * Fabric's Resource Conditions API, so datapacks can disable or gate individual entries without the "empty file"
     * workaround (which logs an error).
     *
     * @param e       The Json being checked.
     * @param id      The ID of that json.
     * @param regId   The type of the json, for logging.
     * @param logger  The logger to log to.
     * @param ops     Unused, kept for call-site compatibility with the original signature.
     * @return True if the item's conditions are met, false otherwise.
     */
    public static boolean checkConditions(JsonElement e, Identifier id, Identifier regId, Logger logger, DynamicOps<JsonElement> ops) {
        return checkConditions(e, id, regId, logger, (HolderLookup.Provider) null);
    }

    /**
     * @param lookup The current registries, needed by conditions such as {@code fabric:registry_contains} and
     *               {@code fabric:tags_populated}; may be null, in which case those conditions fail to evaluate and the
     *               entry is skipped with an error.
     */
    public static boolean checkConditions(JsonElement e, Identifier id, Identifier regId, Logger logger, @Nullable HolderLookup.Provider lookup) {
        if (!e.isJsonObject() || !e.getAsJsonObject().has(ResourceConditions.CONDITIONS_KEY)) return true;
        JsonElement conds = e.getAsJsonObject().get(ResourceConditions.CONDITIONS_KEY);
        RegistryOps.RegistryInfoLookup info = lookup == null ? null : new RegistryOps.RegistryInfoLookup() {
            @Override
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return lookup.lookup(key).map(l -> RegistryOps.RegistryInfo.fromRegistryLookup((HolderLookup.RegistryLookup<T>) l));
            }
        };
        try {
            List<ResourceCondition> list = conds.isJsonArray()
                ? ResourceCondition.LIST_CODEC.parse(JsonOps.INSTANCE, conds).getOrThrow()
                : List.of(ResourceCondition.CODEC.parse(JsonOps.INSTANCE, conds).getOrThrow());
            for (ResourceCondition c : list) {
                if (!c.test(info)) {
                    logger.debug("Skipping {} item with id {} as its load conditions are not met.", regId, id);
                    return false;
                }
            }
            return true;
        }
        catch (Exception ex) {
            logger.error("Skipping {} item with id {}: its load conditions could not be read: {}", regId, id, ex.getMessage());
            return false;
        }
    }

}
