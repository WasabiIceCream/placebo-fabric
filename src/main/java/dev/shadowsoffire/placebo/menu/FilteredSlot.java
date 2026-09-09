package dev.shadowsoffire.placebo.menu;

import java.util.function.Predicate;

import com.google.common.base.Predicates;

import dev.shadowsoffire.placebo.cap.InternalItemHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Extension of {@link Slot} which takes a filter on what may enter the slot.
 * <p>
 * Port note (NeoForge -> Fabric): the original extends NeoForge's {@code ResourceHandlerSlot}
 * (a {@code Slot} bound to a {@code transfer} {@code ResourceHandler}). Fabric GUI slots are
 * still plain vanilla {@link Slot}s backed by a {@link net.minecraft.world.Container} — the
 * Transfer API is for automation, not menu slots — so this extends {@link Slot} directly,
 * backed by {@link InternalItemHandler} (which now also implements {@code Container}).
 */
public class FilteredSlot extends Slot {

    protected final InternalItemHandler handler;
    protected final Predicate<ItemStack> filter;
    protected final int index;

    /**
     * Creates a new filtered slot
     *
     * @param handler The backing item handler
     * @param index   The slot index
     * @param x       The x coordinate
     * @param y       The y coordinate
     * @param filter  A filter controlling what items may be placed in the slot by a player
     */
    public FilteredSlot(InternalItemHandler handler, int index, int x, int y, Predicate<ItemStack> filter) {
        super(handler.asContainer(), index, x, y);
        this.handler = handler;
        this.filter = filter;
        this.index = index;
    }

    public FilteredSlot(InternalItemHandler handler, int index, int x, int y) {
        this(handler, index, x, y, Predicates.alwaysTrue());
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return this.filter.test(stack);
    }

    @Override
    public boolean mayPickup(Player playerIn) {
        return !this.handler.getStackInSlot(this.index).isEmpty();
    }

}
