package dev.shadowsoffire.placebo.mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import dev.shadowsoffire.placebo.util.CachedObject;
import dev.shadowsoffire.placebo.util.CachedObject.CachedObjectSource;

/**
 * Makes {@link ItemStack} actually implement {@link CachedObjectSource}, as required by
 * {@code CachedObjectSource.getOrCreate(ItemStack, ...)}'s blind {@code (CachedObjectSource)
 * (Object) stack} cast.
 * <p>
 * Port bug found via live runtime testing: this mixin never existed in the port at all, despite
 * {@link CachedObject}'s own javadoc explicitly stating "This interface is applied via mixin" —
 * every call into the cache (e.g. {@code AffixHelper#getAffixes}, invoked on every equipment-change
 * tick) threw {@code ClassCastException} immediately on any player carrying/wearing an item, which
 * Neruina caught by kicking the player rather than crashing the whole server.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackCachedObjectMixin implements CachedObjectSource {

    @Unique
    private final Map<Identifier, CachedObject<?>> placebo$cachedObjects = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrCreate(Identifier id, Function<ItemStack, T> deserializer, ToIntFunction<ItemStack> hasher) {
        ItemStack self = (ItemStack) (Object) this;
        CachedObject<T> obj = (CachedObject<T>) this.placebo$cachedObjects.computeIfAbsent(id, k -> new CachedObject<>(id, deserializer, hasher));
        return obj.get(self);
    }

}
