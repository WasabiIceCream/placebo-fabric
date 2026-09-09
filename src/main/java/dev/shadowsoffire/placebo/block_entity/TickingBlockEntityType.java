package dev.shadowsoffire.placebo.block_entity;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Companion object holding the {@link TickSide} for a {@link BlockEntityType}, providing
 * {@link BlockEntityTicker}s for {@linkplain TickingBlockEntity ticking block entities}.
 *
 * Port note (NeoForge -> Fabric): the original subclasses {@link BlockEntityType} directly.
 * Vanilla's {@code BlockEntityType} constructor is private in this MC version (constructing one
 * from outside the class requires Fabric's {@code FabricBlockEntityTypeBuilder}), so subclassing
 * it is no longer possible at all. This is now a standalone companion keyed by identity against
 * the real, builder-constructed {@link BlockEntityType}, registered via
 * {@link dev.shadowsoffire.placebo.registry.DeferredHelper#tickingBlockEntity}.
 *
 * @param <T> The type of the ticking block entity.
 * @see TickingEntityBlock
 */
public final class TickingBlockEntityType<T extends BlockEntity & TickingBlockEntity> {

    private static final Map<BlockEntityType<?>, TickingBlockEntityType<?>> LOOKUP = new IdentityHashMap<>();

    protected final TickSide side;

    private TickingBlockEntityType(TickSide side) {
        this.side = side;
    }

    public static <T extends BlockEntity & TickingBlockEntity> void register(BlockEntityType<T> type, TickSide side) {
        LOOKUP.put(type, new TickingBlockEntityType<T>(side));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity & TickingBlockEntity> TickingBlockEntityType<T> get(BlockEntityType<?> type) {
        return (TickingBlockEntityType<T>) LOOKUP.get(type);
    }

    /**
     * Returns the ticker for the given side, or null if the block entity does not tick on the specified side.
     *
     * @param client True if the ticker for the client side is being requested
     */
    @Nullable
    public BlockEntityTicker<T> getTicker(boolean client) {
        if (client && this.side.ticksOnClient()) {
            return (level, pos, state, entity) -> entity.clientTick(level, pos, state);
        }
        else if (!client && this.side.ticksOnServer()) {
            return (level, pos, state, entity) -> entity.serverTick(level, pos, state);
        }
        return null;
    }

    public static enum TickSide {
        CLIENT,
        SERVER,
        CLIENT_AND_SERVER;

        /**
         * {@return true if this mode should tick on the client}
         */
        public boolean ticksOnClient() {
            return this == CLIENT || this == CLIENT_AND_SERVER;
        }

        /**
         * {@return true if this mode should tick on the server}
         */
        public boolean ticksOnServer() {
            return this == SERVER || this == CLIENT_AND_SERVER;
        }
    }

}
