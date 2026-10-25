/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.item.IItemBuildCraft;
import buildcraft.lib.item.ItemBlockBC_Neptune;
import buildcraft.lib.registry.TagManager.EnumTagType;
import buildcraft.lib.registry.TagManager.EnumTagTypeMulti;

/** Immediate native registration. Optional objects are checked before their factories run. */
public final class RegistrationHelper {
    private static final Map<String, Block> oredictBlocks = new HashMap<>();
    private static final Map<String, Item> oredictItems = new HashMap<>();

    public static void registerOredictEntries() {
        oredictItems.forEach(buildcraft.lib.compat.forge_stubs.OreDictionaryStub::registerOre);
        oredictBlocks.forEach(buildcraft.lib.compat.forge_stubs.OreDictionaryStub::registerOre);
    }

    private static Identifier registryId(String tag) {
        return new Identifier(TagManager.getTag(tag, EnumTagType.REGISTRY_NAME));
    }

    private static boolean enabled(String category, String tag) {
        return RegistryConfig.isEnabled(category, registryId(tag), TagManager.getTag(tag, EnumTagType.UNLOCALIZED_NAME));
    }

    public <I extends Item> I addItem(String tag, Supplier<I> factory) {
        return enabled("items", tag) ? addForcedItem(factory.get()) : null;
    }

    public <I extends Item> I addForcedItem(I item) {
        Identifier id = RegistryConfig.getId(item);
        if (Registries.ITEM.containsId(id)) throw new IllegalArgumentException("Duplicate item: " + id);
        Registry.register(Registries.ITEM, id, item);
        if (item instanceof IItemBuildCraft bc && !bc.id().isEmpty()) {
            MigrationManager.INSTANCE.addItemMigration(item, TagManager.getMultiTag(bc.id(), EnumTagTypeMulti.OLD_REGISTRY_NAME));
            if (TagManager.hasTag(bc.id(), EnumTagType.OREDICT_NAME)) {
                oredictItems.put(TagManager.getTag(bc.id(), EnumTagType.OREDICT_NAME), item);
            }
        }
        return item;
    }

    public <B extends Block> B addBlock(String tag, Supplier<B> factory) {
        return enabled("blocks", tag) ? addForcedBlock(factory.get()) : null;
    }

    public <B extends Block> B addForcedBlock(B block) {
        Identifier id = RegistryConfig.getId(block);
        if (Registries.BLOCK.containsId(id)) throw new IllegalArgumentException("Duplicate block: " + id);
        Registry.register(Registries.BLOCK, id, block);
        if (block instanceof BlockBCBase_Neptune bc && !bc.id.isEmpty()) {
            MigrationManager.INSTANCE.addBlockMigration(block, TagManager.getMultiTag(bc.id, EnumTagTypeMulti.OLD_REGISTRY_NAME));
            if (TagManager.hasTag(bc.id, EnumTagType.OREDICT_NAME)) {
                oredictBlocks.put(TagManager.getTag(bc.id, EnumTagType.OREDICT_NAME), block);
            }
        }
        return block;
    }

    public <B extends BlockBCBase_Neptune> B addBlockAndItem(String tag, Supplier<B> factory) {
        return addBlockAndItem(tag, factory, ItemBlockBC_Neptune::new);
    }

    public <B extends BlockBCBase_Neptune, I extends Item & IItemBuildCraft> B addBlockAndItem(
            String tag, Supplier<B> factory, Function<B, I> itemFactory) {
        B block = addBlock(tag, factory);
        if (block != null) addForcedItem(itemFactory.apply(block));
        else RegistryConfig.setDisabled("items", registryId(tag));
        return block;
    }

    public <T extends BlockEntity> BlockEntityType<T> registerTile(Class<T> tileClass, String tag,
            BlockEntityRegistry.Factory<T> factory, Block... blocks) {
        return BlockEntityRegistry.register(registryId(tag), tileClass, factory, blocks);
    }
}
