/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.engine;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import buildcraft.lib.compat.MaterialBC;
import net.minecraft.block.AbstractBlock;
import net.minecraft.state.property.Property;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.state.StateManager;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.block.BlockRenderType;
import net.minecraft.util.math.Direction;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import buildcraft.api.blocks.ICustomRotationHandler;
import buildcraft.api.core.IEngineType;

import buildcraft.lib.block.BlockBCTile_Neptune;
import buildcraft.lib.registry.RegistryConfig;
import buildcraft.lib.tile.TileBC_Neptune;

public abstract class BlockEngineBase_BC8<E extends Enum<E> & IEngineType> extends BlockBCTile_Neptune
    implements ICustomRotationHandler {
    private final Map<E, Class<? extends TileEngineBase_BC8>> engineTileClasses =
        new EnumMap<>(getEngineProperty().getType());

    public BlockEngineBase_BC8(AbstractBlock.Settings material, String id) {
        super(material, id);
        setDefaultState(getDefaultState().with(getEngineProperty(), getEngineType(0)));
    }

    // Engine directly related methods

    public void registerEngine(E type, Class<? extends TileEngineBase_BC8> tileClass) {
        if (RegistryConfig.isEnabled("engines", RegistryConfig.getId(this) + "/" + type.name().toLowerCase(Locale.ROOT),
            getUnlocalizedName(type))) {
            engineTileClasses.put(type, tileClass);
        }
    }

    public boolean isRegistered(E type) {
        return engineTileClasses.containsKey(type);
    }

    @Nonnull
    public ItemStack getStack(E type) {
        ItemStack stack = new ItemStack(this);
        stack.getOrCreateSubNbt("BlockStateTag").putString(getEngineProperty().getName(), getEngineProperty().name(type));
        return stack;
    }

    public E getEngineType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return getEngineType(0);
        NbtCompound stateTag = stack.getSubNbt("BlockStateTag");
        if (stateTag != null) {
            E type = getEngineProperty().parse(stateTag.getString(getEngineProperty().getName())).orElse(null);
            if (type != null) return type;
        }
        // Preserve the variant when importing an older stack that still uses metadata.
        return getEngineType(stack.getDamage());
    }

    public abstract Property<E> getEngineProperty();

    public abstract E getEngineType(int meta);

    public abstract String getUnlocalizedName(E engine);

    // BlockState

    @Override
    protected void addProperties(List<Property<?>> properties) {
        super.addProperties(properties);
        properties.add(getEngineProperty());
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        E type = getEngineType(context.getStack());
        if (!isRegistered(type)) return null;
        // Choose the variant before the world constructs its block entity. Applying
        // BlockStateTag after placement would leave the default engine's entity behind.
        return super.getPlacementState(context).with(getEngineProperty(), type);
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        return getStack(state.get(getEngineProperty()));
    }

// @Override removed (R.Chen): no longer overrides — Phase 10
    public int getMetaFromState(BlockState state) {
        E type = state.get(getEngineProperty());
        return type.ordinal();
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public BlockState getStateFromMeta(int meta) {
        E engineType = getEngineType(meta);
        return getDefaultState().with(getEngineProperty(), engineType);
    }

    // Misc Block Overrides

    @Override
    public int getOpacity(BlockState state, BlockView world, BlockPos pos) {
        // The animated piston occupies only part of the block; sample light inside it.
        return 0;
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public boolean isOpaqueCube(BlockState state) {
        return false;
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public boolean isFullBlock(BlockState state) {
        return false;
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public boolean isFullCube(BlockState state) {
        return false;
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public BlockFaceShape getBlockFaceShape(BlockView world, BlockState state, BlockPos pos, Direction side) {
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof TileEngineBase_BC8) {
            TileEngineBase_BC8 engine = (TileEngineBase_BC8) tile;
            if (side == engine.currentDirection.getOpposite()) {
                return BlockFaceShape.SOLID;
            } else {
                return BlockFaceShape.UNDEFINED;
            }
        }
        return BlockFaceShape.UNDEFINED;
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public boolean isSideSolid(BlockState base_state, BlockView world, BlockPos pos, Direction side) {
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof TileEngineBase_BC8) {
            TileEngineBase_BC8 engine = (TileEngineBase_BC8) tile;
            return side == engine.currentDirection.getOpposite();
        }
        return false;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public TileBC_Neptune createBlockEntity(BlockPos pos, BlockState state) {
        E engineType = state.get(getEngineProperty());
        Class<? extends TileEngineBase_BC8> tileClass = engineTileClasses.get(engineType);
        if (tileClass == null) {
            return null;
        }
        return buildcraft.lib.registry.BlockEntityRegistry.create(tileClass, pos, state);
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public void getSubBlocks(ItemGroup tab, DefaultedList<ItemStack> list) {
        for (E engine : getEngineProperty().getValues()) {
            if (engineTileClasses.containsKey(engine)) {
                list.add(getStack(engine));
            }
        }
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public int damageDropped(BlockState state) {
        return state.get(getEngineProperty()).ordinal();
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean notify) {
        super.neighborUpdate(state, world, pos, block, fromPos, notify);
        if (world.isClient) return;
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof TileEngineBase_BC8) {
            TileEngineBase_BC8 engine = (TileEngineBase_BC8) tile;
            engine.rotateIfInvalid();
        }
    }

    // ICustomRotationHandler

    @Override
    public ActionResult attemptRotation(World world, BlockPos pos, BlockState state, Direction sideWrenched) {
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof TileEngineBase_BC8) {
            TileEngineBase_BC8 engine = (TileEngineBase_BC8) tile;
            return engine.attemptRotation();
        }
        return ActionResult.FAIL;
    }
}
