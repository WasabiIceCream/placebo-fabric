package dev.shadowsoffire.placebo.registry;

import java.util.Arrays;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.shadowsoffire.placebo.block_entity.TickingBlockEntity;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntityType;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntityType.TickSide;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType.EntityFactory;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MenuType.MenuSupplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Helper class that acts as a single point of entry for registration of all registry entries.
 * <p>
 * Provides methods for the most common types of objects, as well as {@link #custom(String, ResourceKey, Object)}
 * for other types.
 *
 * Port note (NeoForge -> Fabric): the original stages every registration behind NeoForge's
 * {@code RegisterEvent} (fired once per registry, in a fixed dependency order) via an internal
 * {@code Registrar} queue, because NeoForge registries are collected before they're frozen and
 * mods must not touch them outside that window. Fabric's registries don't have that restriction —
 * {@link Registry#registerForHolder} can be called directly, immediately, from mod init — so this
 * port drops the staging entirely and just registers eagerly. This is a genuine simplification, not
 * a corner cut: nothing here needs the deferred behavior on Fabric.
 * <p>
 * Not ported: {@code attachment(...)} (NeoForge {@code AttachmentType} — replaced by Cardinal
 * Components API at call sites, not something this helper can paper over), {@code ingredient(...)}
 * (NeoForge {@code ICustomIngredient}/{@code IngredientType} — no Fabric equivalent registry),
 * {@code dataMap(...)} (NeoForge Data Maps — no Fabric equivalent), {@code lootModifier(...)}
 * (NeoForge's {@code IGlobalLootModifier} codec registry — the Adventure-module loot injection uses
 * Fabric's {@code LootTableEvents}/Loot Table Modifier instead, a different shape entirely). Also not
 * ported: {@code registry(...)} (custom dynamic-registry creation via NeoForge's {@code RegistryBuilder}/
 * {@code NewRegistryEvent} — Apotheosis's actual custom registries in scope, e.g. rarities/affixes,
 * go through {@code dev.shadowsoffire.placebo.dynreg.DynamicRegistry} instead, not this method; if a
 * true *new vanilla-style Registry* is needed later, revisit with Fabric's {@code FabricRegistryBuilder}).
 */
public class DeferredHelper {

    protected final String modid;

    public static DeferredHelper create(String modid) {
        return new DeferredHelper(modid);
    }

    protected DeferredHelper(String modid) {
        this.modid = modid;
    }

    protected Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(this.modid, path);
    }

    /**
     * Registers a {@link Block} using a supplier.
     */
    public <T extends Block> Holder<Block> block(String path, Supplier<T> factory) {
        return Registry.registerForHolder(BuiltInRegistries.BLOCK, this.id(path), factory.get());
    }

    /**
     * Registers a {@link Block} with a reference to its constructor, configuring a new {@link Block.Properties} instance with the supplied operator.
     */
    public <T extends Block> Holder<Block> block(String path, Function<Block.Properties, T> ctor, UnaryOperator<Block.Properties> properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, this.id(path));
        return this.block(path, () -> ctor.apply(properties.apply(Block.Properties.of()).setId(key)));
    }

    /**
     * Registers an {@link Item} using a supplier.
     */
    public <T extends Item> Holder<Item> item(String path, Supplier<T> factory) {
        return Registry.registerForHolder(BuiltInRegistries.ITEM, this.id(path), factory.get());
    }

    /**
     * Registers an {@link Item} with a reference to its constructor, configuring a new {@link Item.Properties} instance with the supplied operator.
     */
    public <T extends Item> Holder<Item> item(String path, Function<Item.Properties, T> ctor, UnaryOperator<Item.Properties> properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, this.id(path));
        return item(path, () -> ctor.apply(properties.apply(new Item.Properties()).setId(key)));
    }

    /**
     * Registers an {@link Item} with a reference to its constructor, using a default {@link Item.Properties} instance.
     */
    public <T extends Item> Holder<Item> item(String path, Function<Item.Properties, T> ctor) {
        return item(path, ctor, UnaryOperator.identity());
    }

    /**
     * Registers a subclass of {@link BlockItem} given a target block, the constructor, and an {@link Item.Properties} factory.
     */
    public <T extends BlockItem> Holder<Item> blockItem(String path, Holder<Block> block, BiFunction<Block, Item.Properties, T> ctor, UnaryOperator<Item.Properties> properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, this.id(path));
        return item(path, () -> ctor.apply(block.value(), properties.apply(new Item.Properties().useBlockDescriptionPrefix()).setId(key)));
    }

    public Holder<Item> blockItem(String path, Holder<Block> block, UnaryOperator<Item.Properties> properties) {
        return blockItem(path, block, BlockItem::new, properties);
    }

    public Holder<Item> blockItem(String path, Holder<Block> block) {
        return blockItem(path, block, UnaryOperator.identity());
    }

    /**
     * Registers a {@link MobEffect} using a supplier.
     */
    public <T extends MobEffect> Holder<MobEffect> effect(String path, Supplier<T> factory) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, this.id(path), factory.get());
    }

    /**
     * Registers a {@link SoundEvent} using a supplier.
     */
    public Holder<SoundEvent> sound(String path, Supplier<SoundEvent> factory) {
        return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, this.id(path), factory.get());
    }

    /**
     * Immediately creates and registers a {@link SoundEvent} using the given path via {@link SoundEvent#createVariableRangeEvent}.
     */
    public SoundEvent sound(String path) {
        SoundEvent sound = SoundEvent.createVariableRangeEvent(this.id(path));
        this.sound(path, () -> sound);
        return sound;
    }

    /**
     * Registers a {@link Potion} using a supplier.
     */
    public <T extends Potion> Holder<Potion> potion(String path, Supplier<T> factory) {
        return Registry.registerForHolder(BuiltInRegistries.POTION, this.id(path), factory.get());
    }

    public Holder<Potion> singlePotion(String path, Supplier<MobEffectInstance> factory) {
        return this.potion(path, () -> {
            MobEffectInstance inst = factory.get();
            Identifier key = BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value());
            return new Potion(key.toLanguageKey(), inst);
        });
    }

    public Holder<Potion> multiPotion(String path, Supplier<java.util.List<MobEffectInstance>> factory) {
        String key = this.id(path).toLanguageKey("potion");
        return this.potion(path, () -> new Potion(key, factory.get().toArray(new MobEffectInstance[0])));
    }

    /**
     * Registers an {@link EntityType} using a supplier.
     */
    public <U extends Entity, T extends EntityType<U>> Holder<EntityType<?>> entity(String path, Supplier<T> factory) {
        return Registry.registerForHolder(BuiltInRegistries.ENTITY_TYPE, this.id(path), factory.get());
    }

    public <T extends Entity> Holder<EntityType<?>> entity(String path, EntityFactory<T> factory, MobCategory category, UnaryOperator<EntityType.Builder<T>> op) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, this.id(path));
        return this.entity(path, () -> op.apply(EntityType.Builder.of(factory, category)).build(key));
    }

    /**
     * Registers a {@link BlockEntityType} given a {@link FabricBlockEntityTypeBuilder.Factory} and its valid blocks.
     * <p>
     * Port note: vanilla's {@code BlockEntityType} constructor is private in this MC version, so it can only be
     * constructed via Fabric's {@link FabricBlockEntityTypeBuilder} (which also requires plain {@link Block}s, not
     * {@link Holder}s, hence the unwrapping below).
     */
    @SafeVarargs
    public final <T extends BlockEntity> Holder<BlockEntityType<?>> blockEntity(String path, FabricBlockEntityTypeBuilder.Factory<T> factory, Holder<Block>... validBlocks) {
        Block[] blocks = Arrays.stream(validBlocks).map(Holder::value).toArray(Block[]::new);
        BlockEntityType<T> type = FabricBlockEntityTypeBuilder.create(factory, blocks).build();
        return Registry.registerForHolder(BuiltInRegistries.BLOCK_ENTITY_TYPE, this.id(path), type);
    }

    /**
     * As {@link #blockEntity}, but also registers the type with {@link TickingBlockEntityType} so
     * {@link dev.shadowsoffire.placebo.block_entity.TickingEntityBlock} can find its ticker.
     */
    @SafeVarargs
    public final <T extends BlockEntity & TickingBlockEntity> Holder<BlockEntityType<?>> tickingBlockEntity(String path, FabricBlockEntityTypeBuilder.Factory<T> factory, TickSide side, Holder<Block>... validBlocks) {
        Block[] blocks = Arrays.stream(validBlocks).map(Holder::value).toArray(Block[]::new);
        BlockEntityType<T> type = FabricBlockEntityTypeBuilder.create(factory, blocks).build();
        Holder<BlockEntityType<?>> holder = Registry.registerForHolder(BuiltInRegistries.BLOCK_ENTITY_TYPE, this.id(path), type);
        TickingBlockEntityType.register(type, side);
        return holder;
    }

    /**
     * Registers a {@link ParticleType} using a supplier.
     */
    public <U extends ParticleOptions, T extends ParticleType<U>> Holder<ParticleType<?>> particle(String path, Supplier<T> factory) {
        return Registry.registerForHolder(BuiltInRegistries.PARTICLE_TYPE, this.id(path), factory.get());
    }

    public SimpleParticleType simpleParticle(String path, boolean overrideLimit) {
        var type = new SimpleParticleType(overrideLimit){};
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, this.id(path), type);
        return type;
    }

    public <T extends ParticleOptions> ParticleType<T> particle(String path, boolean overrideLimit, Function<ParticleType<T>, MapCodec<T>> codec,
        Function<ParticleType<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>> streamCodec) {
        var type = new ParticleType<T>(overrideLimit){

            @Override
            public MapCodec<T> codec() {
                return codec.apply(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
                return streamCodec.apply(this);
            }
        };
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, this.id(path), type);
        return type;
    }

    /**
     * Registers a {@link MenuType} using a supplier.
     */
    public <U extends AbstractContainerMenu, T extends MenuType<U>> T menuType(String path, T type) {
        Registry.register(BuiltInRegistries.MENU, this.id(path), type);
        return type;
    }

    public <T extends AbstractContainerMenu> MenuType<T> menu(String path, MenuSupplier<T> factory) {
        return this.menuType(path, new MenuType<>(factory, FeatureFlags.DEFAULT_FLAGS));
    }

    /**
     * Registers a {@link MenuType} that also carries a {@link BlockPos} of extra opening data to the client.
     * <p>
     * Port note (NeoForge -> Fabric): replaces NeoForge's {@code IContainerFactory}-based
     * {@code MenuType} constructor (which reads extra data off the same buffer used for the id/inventory)
     * with Fabric API's {@link ExtendedMenuType}, which carries the extra data through its own
     * {@link StreamCodec} instead — here, {@code BlockPos.STREAM_CODEC}.
     */
    public <T extends AbstractContainerMenu> MenuType<T> menuWithPos(String path, ExtendedMenuType.ExtendedFactory<T, BlockPos> factory) {
        return this.menuType(path, new ExtendedMenuType<>(factory, BlockPos.STREAM_CODEC));
    }

    /**
     * Registers a {@link RecipeType} using a supplier.
     */
    public <C extends RecipeInput, U extends Recipe<C>, T extends RecipeType<U>> Holder<RecipeType<?>> recipe(String path, Supplier<T> factory) {
        return Registry.registerForHolder(BuiltInRegistries.RECIPE_TYPE, this.id(path), factory.get());
    }

    public <C extends RecipeInput, U extends Recipe<C>> RecipeType<U> recipe(String path) {
        RecipeType<U> type = new RecipeType<U>() {};
        this.recipe(path, () -> type);
        return type;
    }

    /**
     * Registers a {@link RecipeSerializer} using a supplier.
     */
    public <I extends RecipeInput, R extends Recipe<I>> Holder<RecipeSerializer<?>> recipeSerializer(String path, Supplier<RecipeSerializer<R>> factory) {
        return Registry.registerForHolder(BuiltInRegistries.RECIPE_SERIALIZER, this.id(path), factory.get());
    }

    /**
     * Registers an {@link Attribute} using a supplier.
     */
    public <T extends Attribute> Holder<Attribute> attribute(String path, Supplier<T> factory) {
        return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, this.id(path), factory.get());
    }

    public Holder<Attribute> rangedAttribute(String path, double defaultValue, double min, double max) {
        String key = this.id(path).toLanguageKey("attribute");
        return this.attribute(path, () -> new RangedAttribute(key, defaultValue, min, max));
    }

    /**
     * Registers a {@link StatType} using a supplier.
     */
    public <S, U extends StatType<S>, T extends StatType<U>> Holder<StatType<?>> stat(String path, Supplier<T> factory) {
        return Registry.registerForHolder(BuiltInRegistries.STAT_TYPE, this.id(path), factory.get());
    }

    public Identifier customStat(String path, StatFormatter formatter) {
        Identifier id = this.id(path);
        Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id);
        Stats.CUSTOM.get(id, formatter);
        return id;
    }

    /**
     * Registers a {@link Feature} using a supplier.
     */
    public <U extends FeatureConfiguration, T extends Feature<U>> Holder<Feature<?>> feature(String path, Supplier<T> factory) {
        return Registry.registerForHolder(BuiltInRegistries.FEATURE, this.id(path), factory.get());
    }

    /**
     * Registers a {@link CreativeModeTab} that is configured with the supplied operator, placed at the
     * bottom row of the creative menu tab bar (the usual spot for a modded tab).
     */
    public Holder<CreativeModeTab> creativeTab(String path, UnaryOperator<CreativeModeTab.Builder> operator) {
        return this.creativeTab(path, CreativeModeTab.Row.BOTTOM, 0, operator);
    }

    public Holder<CreativeModeTab> creativeTab(String path, CreativeModeTab.Row row, int column, UnaryOperator<CreativeModeTab.Builder> operator) {
        return Registry.registerForHolder(BuiltInRegistries.CREATIVE_MODE_TAB, this.id(path), operator.apply(CreativeModeTab.builder(row, column)).build());
    }

    public <T> DataComponentType<T> enchantmentEffect(String path, UnaryOperator<DataComponentType.Builder<T>> operator) {
        DataComponentType<T> type = operator.apply(DataComponentType.builder()).build();
        Registry.register(BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, this.id(path), type);
        return type;
    }

    public <T> DataComponentType<T> component(String path, UnaryOperator<DataComponentType.Builder<T>> operator) {
        DataComponentType<T> type = operator.apply(DataComponentType.builder()).build();
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, this.id(path), type);
        return type;
    }

    public <T extends LootPoolEntryContainer> MapCodec<T> lootPoolEntry(String path, MapCodec<T> codec) {
        Registry.register(BuiltInRegistries.LOOT_POOL_ENTRY_TYPE, this.id(path), codec);
        return codec;
    }

    public <T extends LootItemCondition> MapCodec<T> lootCondition(String path, MapCodec<T> codec) {
        Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, this.id(path), codec);
        return codec;
    }

    public <T extends CriterionTrigger<?>> T criteriaTrigger(String path, T trigger) {
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, this.id(path), trigger);
        return trigger;
    }

    public <T extends DataComponentPredicate> DataComponentPredicate.Type<T> componentPredicate(String path, Codec<T> codec) {
        DataComponentPredicate.Type<T> type = new DataComponentPredicate.ConcreteType<>(codec);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE, this.id(path), type);
        return type;
    }

    public <T extends StructureProcessor> StructureProcessorType<T> structureProcessor(String path, MapCodec<T> codec) {
        StructureProcessorType<T> type = () -> codec;
        Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, this.id(path), type);
        return type;
    }

    /**
     * Registers a custom object to the target registry using a supplier.
     */
    public <R, T extends R> Holder<R> customDH(String path, Registry<R> registry, Supplier<T> factory) {
        return Registry.registerForHolder(registry, this.id(path), factory.get());
    }

    /**
     * Registers a custom object to the target registry immediately.
     */
    public <R, T extends R> T custom(String path, Registry<R> registry, T object) {
        Registry.register(registry, this.id(path), object);
        return object;
    }

    /**
     * Creates and registers a new, defaulted (falls back to {@code defaultId} for unknown
     * entries) custom vanilla-style {@link Registry} — e.g. for a mod-defined classification
     * system like Apotheosis's loot categories.
     * <p>
     * Port note (NeoForge -> Fabric): the original's {@code registry(String, UnaryOperator<RegistryBuilder>)}
     * exposes an {@code onBake(BakeCallback)} hook (NeoForge fires it whenever the registry is
     * frozen/reloaded) for rebuilding derived state after all entries are known. This port drops
     * that entirely — Fabric has no live-reloadable custom registries the way NeoForge does
     * (nothing here is datapack-driven; entries are plain Java constants registered once at mod
     * init), so callers should just do that rebuild once, directly, right after registering all
     * entries — no callback plumbing needed.
     */
    public <T> Registry<T> registry(String path, Identifier defaultId) {
        ResourceKey<Registry<T>> key = ResourceKey.createRegistryKey(this.id(path));
        return net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder.createDefaulted(key, defaultId).buildAndRegister();
    }

}
