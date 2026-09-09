package dev.shadowsoffire.placebo;

import dev.shadowsoffire.placebo.network.PayloadHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Port note: upstream's {@code PlaceboClient} also wires up commands, patreon
 * wings/trails, tooltip-scroll handling, and various NeoForge client events — none of
 * that is ported yet (out of scope for the Adventure module or not yet reached). This
 * is intentionally a partial entrypoint; expand as more of Placebo gets ported. The
 * client tick counter is ported because {@code color.GradientColor} (used by
 * Apotheosis for its top rarity tier's animated name color) needs it.
 */
public class PlaceboClient implements ClientModInitializer {

    private static long ticks = 0;

    @Override
    public void onInitializeClient() {
        PayloadHelper.registerClientHandlers();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ticks++);
        Placebo.LOGGER.info("Placebo client (Fabric port, partial) initializing");
    }

    public static float getColorTicks() {
        return (ticks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 0.5F;
    }
}
