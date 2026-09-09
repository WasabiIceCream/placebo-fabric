package dev.shadowsoffire.placebo.tabs;

import java.util.function.Supplier;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.Output;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/**
 * An ITabFiller is an item that has been delegated the task of filling creative tabs.
 *
 * Port note (NeoForge -> Fabric): the original's {@code fillItemCategory} takes NeoForge's
 * {@code BuildCreativeModeTabContentsEvent}, whose job here is just accepting items into
 * the tab being built — takes the vanilla {@link Output} directly instead, which is that
 * same accept-items capability without the event wrapper.
 * <p>
 * Note: {@link TabFillingRegistry}, which cross-cuttingly injects an {@code ITabFiller}
 * into arbitrary (including vanilla/other mods') tabs, is not yet ported — it needs a
 * mixin into {@code CreativeModeTab.buildContents} (no Fabric API event exists for this
 * at all, unlike NeoForge). This interface itself has no such dependency, so it's ported
 * standalone; items that implement it only actually get pulled into other tabs once
 * {@code TabFillingRegistry} exists.
 */
public interface ITabFiller {

    /**
     * Fills a creative tabs will all relevant subitems of this item.<br>
     * This method will only be called for tabs that this tab filler is registered for.
     *
     * @param tab    The current creative tab being filled.
     * @param output The output, used to add items to the tab.
     */
    void fillItemCategory(CreativeModeTab tab, Output output);

    /**
     * Creates a simple {@link ITabFiller} that populates a tab with the default stack of the item.
     *
     * @param i Any {@link ItemLike}
     * @return A new ITabFiller which will provide the item to all tabs it is invoked for.
     */
    static ITabFiller simple(ItemLike i) {
        return (tab, output) -> output.accept(i.asItem().getDefaultInstance());
    }

    /**
     * Creates a delegating {@link ITabFiller} that will perform a different task depending on the supplied object.<br>
     * If the supplied object implements {@link ITabFiller}, then {@link #fillItemCategory(CreativeModeTab, Output)} will be called.<br>
     * If not, then the item's default instance will be added, similar to {@link #simple(ItemLike)}.
     *
     * @param i An itemlike supplier.
     * @return A new ITabFiller which will delegate to the item or provide the item to all tabs it is invoked for.
     */
    static ITabFiller delegating(Supplier<? extends ItemLike> i) {
        return (tab, output) -> {
            Item item = i.get().asItem();
            if (item instanceof ITabFiller filler) {
                filler.fillItemCategory(tab, output);
            }
            else {
                output.accept(item.getDefaultInstance());
            }
        };
    }

}
