package dev.shadowsoffire.placebo.json;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;

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
     *
     * Port note: the original NeoForge version checks a {@code "neoforge:conditions"}
     * array embedded in the JSON via NeoForge's {@code ICondition}/{@code ConditionalOps}
     * system, letting datapack authors mark individual entries as conditionally active
     * (loaded-mod checks, tag-populated checks, etc.). Fabric's Resource Conditions API
     * (`fabric-resource-conditions-api-v1`) covers the same *purpose* but applies to
     * vanilla resource types (recipes, loot tables) automatically — it has no equivalent
     * for a custom-scanned directory like this one, and implementing per-entry condition
     * parsing here would be a project of its own.
     *
     * TODO(port): always-true for now — no in-scope Adventure-module JSON (rarities,
     * affixes, gems) currently relies on per-entry conditional loading. Revisit if that
     * changes; a real implementation would parse a `"fabric:load_conditions"`-style key
     * here using {@code ResourceConditions}' registered condition types.
     *
     * @param e       The Json being checked.
     * @param id      The ID of that json.
     * @param regId   The type of the json, for logging.
     * @param logger  The logger to log to.
     * @param ops     The ops used to decode this Json (unused by the stub, kept for
     *                call-site compatibility with the original signature).
     * @return True if the item's conditions are met, false otherwise.
     */
    public static boolean checkConditions(JsonElement e, Identifier id, Identifier regId, Logger logger, DynamicOps<JsonElement> ops) {
        return true;
    }

}
