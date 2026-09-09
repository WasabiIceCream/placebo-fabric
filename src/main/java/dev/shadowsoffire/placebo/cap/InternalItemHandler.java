package dev.shadowsoffire.placebo.cap;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;

/**
 * A simple array-backed N-slot {@link ItemVariant} storage, exposing unrestricted
 * {@link #extractInternal}/{@link #insertInternal} methods.
 * <p>
 * Used by {@link dev.shadowsoffire.placebo.menu.FilteredSlot} so that menus may define
 * their own logic that differs from the logic used by automation.
 *
 * Port note (NeoForge -> Fabric): the original extends NeoForge's newer
 * {@code transfer} API's {@code ItemStacksResourceHandler} (a ready-made array-backed
 * multi-slot handler using {@code ItemResource}/{@code TransactionContext}). Fabric's
 * Transfer API (the older, original design NeoForge's `transfer` package converged on)
 * doesn't ship an equivalent ready-made class — this reimplements the same array-backed
 * behavior directly against {@link SlottedStorage}/{@link ItemVariant}.
 * <p>
 * Also exposes {@link #asContainer()}, a vanilla {@link Container} view over the same
 * backing array — Fabric GUI slots (unlike NeoForge's {@code ResourceHandlerSlot}) are
 * still plain vanilla {@link net.minecraft.world.inventory.Slot}s backed by a
 * {@code Container}, not the Transfer API (Transfer is for automation, not player-facing
 * menu slots), so {@link dev.shadowsoffire.placebo.menu.FilteredSlot} needs a view like
 * this. It's a separate adapter object rather than implemented directly on this class
 * because {@code Container}'s {@code getSlot(int)}/{@code iterator()} signatures collide
 * with {@link SlottedStorage}'s.
 */
public class InternalItemHandler implements SlottedStorage<ItemVariant> {

    protected final ItemStack[] stacks;
    protected final int maxCountPerSlot;

    public InternalItemHandler(int size) {
        this(size, 64);
    }

    public InternalItemHandler(int size, int maxCountPerSlot) {
        this.stacks = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            this.stacks[i] = ItemStack.EMPTY;
        }
        this.maxCountPerSlot = maxCountPerSlot;
    }

    public int size() {
        return this.stacks.length;
    }

    public ItemStack getStackInSlot(int slot) {
        return this.stacks[slot];
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        this.stacks[slot] = stack;
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        return new Slot(slot);
    }

    @Override
    public int getSlotCount() {
        return this.stacks.length;
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        return new AbstractList<StorageView<ItemVariant>>() {
            @Override
            public StorageView<ItemVariant> get(int index) {
                return getSlot(index);
            }

            @Override
            public int size() {
                return stacks.length;
            }
        }.iterator();
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        long inserted = 0;
        for (int i = 0; i < this.stacks.length && inserted < maxAmount; i++) {
            inserted += this.getSlot(i).insert(resource, maxAmount - inserted, transaction);
        }
        return inserted;
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        long extracted = 0;
        for (int i = 0; i < this.stacks.length && extracted < maxAmount; i++) {
            extracted += this.getSlot(i).extract(resource, maxAmount - extracted, transaction);
        }
        return extracted;
    }

    /**
     * A vanilla {@link Container} view over this handler's backing array — see the class javadoc.
     */
    public Container asContainer() {
        return new ContainerView();
    }

    private class ContainerView implements Container {

        @Override
        public int getContainerSize() {
            return stacks.length;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return getStackInSlot(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = stacks[slot];
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack split = stack.split(amount);
            if (stack.isEmpty()) stacks[slot] = ItemStack.EMPTY;
            return split;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = stacks[slot];
            stacks[slot] = ItemStack.EMPTY;
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            setStackInSlot(slot, stack);
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < stacks.length; i++) {
                stacks[i] = ItemStack.EMPTY;
            }
        }
    }

    public int extractInternal(int index, ItemVariant resource, int amount, TransactionContext transaction) {
        return this.doExtract(index, resource, amount, transaction);
    }

    public int insertInternal(int index, ItemVariant resource, int amount, TransactionContext transaction) {
        return this.doInsert(index, resource, amount, transaction);
    }

    private int doExtract(int index, ItemVariant resource, int amount, TransactionContext transaction) {
        ItemStack stack = this.stacks[index];
        if (stack.isEmpty() || !resource.matches(stack)) return 0;
        int extracted = Math.min(amount, stack.getCount());
        try (var nested = transaction.openNested()) {
            this.stacks[index] = stack.copyWithCount(stack.getCount() - extracted);
            nested.commit();
        }
        return extracted;
    }

    private int doInsert(int index, ItemVariant resource, int amount, TransactionContext transaction) {
        ItemStack stack = this.stacks[index];
        int existing = stack.isEmpty() ? 0 : stack.getCount();
        if (!stack.isEmpty() && !resource.matches(stack)) return 0;
        int max = Math.min(this.maxCountPerSlot, resource.getItem().getDefaultMaxStackSize());
        int inserted = Math.min(amount, max - existing);
        if (inserted <= 0) return 0;
        try (var nested = transaction.openNested()) {
            ItemStack newStack = stack.isEmpty() ? resource.toStack(inserted) : stack.copyWithCount(existing + inserted);
            this.stacks[index] = newStack;
            nested.commit();
        }
        return inserted;
    }

    private class Slot implements SingleSlotStorage<ItemVariant> {

        private final int index;

        private Slot(int index) {
            this.index = index;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return doInsert(this.index, resource, (int) maxAmount, transaction);
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return doExtract(this.index, resource, (int) maxAmount, transaction);
        }

        @Override
        public boolean isResourceBlank() {
            return stacks[this.index].isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(stacks[this.index]);
        }

        @Override
        public long getAmount() {
            return stacks[this.index].getCount();
        }

        @Override
        public long getCapacity() {
            return maxCountPerSlot;
        }
    }

}
