package dev.shadowsoffire.placebo.menu;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MenuType.MenuSupplier;

/**
 * Port note (NeoForge -> Fabric): the original builds {@code MenuType}s with NeoForge's
 * {@code IContainerFactory} (extra opening data read off the same buffer as the menu id).
 * Fabric API's replacement is {@link ExtendedMenuType}, which carries the extra data
 * through its own {@code StreamCodec} instead — here, {@code BlockPos.STREAM_CODEC} for
 * the common "menu opened at a block position" case.
 */
public class MenuUtil {

    /**
     * Creates a {@link MenuType} with the target menu supplier and the vanilla feature flags.
     */
    public static <T extends AbstractContainerMenu> MenuType<T> type(MenuSupplier<T> factory) {
        return new MenuType<>(factory, FeatureFlags.DEFAULT_FLAGS);
    }

    /**
     * Util method for the most common type of extended menu — one supplying a {@link BlockPos}.
     */
    public static <T extends AbstractContainerMenu> MenuType<T> posType(PosFactory<T> factory) {
        return new ExtendedMenuType<>(factory, BlockPos.STREAM_CODEC);
    }

    /**
     * Helper method wrapping {@link Player#openMenu} that returns {@link InteractionResult}.
     * Designed for use with {@link BlockEntityMenu}.
     */
    public static <M extends AbstractContainerMenu> InteractionResult openGui(Player player, BlockPos pos, PosFactory<M> factory) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new SimplerMenuProvider<>(player.level(), pos, factory));
        return InteractionResult.CONSUME;
    }

    @FunctionalInterface
    public static interface PosFactory<T extends AbstractContainerMenu> extends ExtendedMenuType.ExtendedFactory<T, BlockPos> {
        @Override
        T create(int id, net.minecraft.world.entity.player.Inventory inv, BlockPos pos);
    }

}
