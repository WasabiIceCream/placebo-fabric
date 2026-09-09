package dev.shadowsoffire.placebo.menu;

import dev.shadowsoffire.placebo.menu.MenuUtil.PosFactory;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.level.Level;

/**
 * Boilerplate for creating {@link MenuProvider}s when using {@link BlockEntityMenu}.
 * <p>
 * Port note (NeoForge -> Fabric): also implements Fabric API's {@link ExtendedMenuProvider}
 * so the {@link BlockPos} this menu was opened at reaches the client via
 * {@link MenuUtil#posType}'s {@code ExtendedMenuType} — NeoForge's equivalent read this
 * data straight off the packet buffer instead of through the provider.
 */
public class SimplerMenuProvider<M extends AbstractContainerMenu> implements MenuProvider, ExtendedMenuProvider<BlockPos> {
    private final Component title;
    private final BlockPos pos;
    private final MenuConstructor menuConstructor;

    public SimplerMenuProvider(Level level, BlockPos pos, PosFactory<M> factory) {
        this.pos = pos;
        this.menuConstructor = (id, inv, player) -> factory.create(id, inv, pos);
        this.title = Component.translatable(level.getBlockState(pos).getBlock().getDescriptionId());
    }

    @Override
    public Component getDisplayName() {
        return this.title;
    }

    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pInventory, Player pPlayer) {
        return this.menuConstructor.createMenu(pContainerId, pInventory, pPlayer);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.pos;
    }

}
