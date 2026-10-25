/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.core.block;

import java.util.List;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.property.Property;

import buildcraft.api.enums.EnumDecoratedBlock;
import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.lib.block.BlockBCBase_Neptune;

public class BlockDecoration extends BlockBCBase_Neptune {
    public static final Property<EnumDecoratedBlock> DECORATED_TYPE = BuildCraftProperties.DECORATED_BLOCK;

    public BlockDecoration(String id) {
        super(AbstractBlock.Settings.create()
            .mapColor(MapColor.IRON_GRAY)
            .strength(5.0F, 10.0F)
            .sounds(BlockSoundGroup.METAL)
            .luminance(state -> state.get(DECORATED_TYPE).lightValue),
            id);
        setDefaultState(getDefaultState().with(DECORATED_TYPE, EnumDecoratedBlock.DESTROY));
    }

    // BlockState

    @Override
    public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext context) {
        return getDefaultState().with(DECORATED_TYPE,
            buildcraft.core.item.ItemBlockDecorated.getVariant(context.getStack()));
    }

    @Override
    public net.minecraft.item.ItemStack getPickStack(net.minecraft.world.BlockView world,
            net.minecraft.util.math.BlockPos pos, BlockState state) {
        var stack = new net.minecraft.item.ItemStack(this);
        stack.getOrCreateSubNbt("BlockStateTag").putString(DECORATED_TYPE.getName(), state.get(DECORATED_TYPE).asString());
        return stack;
    }

    @Override
    protected void addProperties(List<Property<?>> properties) {
        properties.add(DECORATED_TYPE);
    }

}

