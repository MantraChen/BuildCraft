/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import buildcraft.lib.compat.MaterialBC;
import net.minecraft.block.AbstractBlock;
import net.minecraft.state.property.Property;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tools.IToolWrench;

import buildcraft.lib.block.BlockBCTile_Neptune;
import buildcraft.lib.tile.TileBC_Neptune;

import buildcraft.factory.tile.TileFloodGate;

public class BlockFloodGate extends BlockBCTile_Neptune {
    public static final Map<Direction, Property<Boolean>> CONNECTED_MAP;

    static {
        CONNECTED_MAP = new HashMap<>(BuildCraftProperties.CONNECTED_MAP);
        CONNECTED_MAP.remove(Direction.UP);
    }

    public BlockFloodGate(AbstractBlock.Settings material, String id) {
        super(material, id);
        BlockState state=getDefaultState();
        for(var property:CONNECTED_MAP.values())state=state.with(property,true);
        setDefaultState(state);
    }

    @Override
    protected void addProperties(List<Property<?>> properties) {
        super.addProperties(properties);
        properties.addAll(CONNECTED_MAP.values());
    }

    @Override
    public TileBC_Neptune createBlockEntity(net.minecraft.util.math.BlockPos pos, BlockState state) {
        return buildcraft.lib.registry.BlockEntityRegistry.create(TileFloodGate.class, pos, state);
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    public BlockState getActualState(BlockState state, BlockView world, BlockPos pos) {
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof TileFloodGate) {
            for (Direction side : CONNECTED_MAP.keySet()) {
                state = state.with(CONNECTED_MAP.get(side), ((TileFloodGate) tile).openSides.contains(side));
            }
        }
        return state;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, BlockState state, PlayerEntity player, Hand hand,
        Direction side, float hitX, float hitY, float hitZ) {
        ItemStack heldItem = player.getStackInHand(hand);
        if (heldItem.getItem() instanceof IToolWrench wrench) {
            var hit=new net.minecraft.util.hit.BlockHitResult(new net.minecraft.util.math.Vec3d(pos.getX()+hitX,pos.getY()+hitY,pos.getZ()+hitZ),side,pos,false);
            if(!(world.getBlockEntity(pos) instanceof TileFloodGate gate) || !gate.canInteractWith(player)
                || !CONNECTED_MAP.containsKey(side) || !wrench.canWrench(player,hand,heldItem,hit))return false;
            if(!gate.toggleSide(player,side))return false;
            if(!world.isClient)wrench.wrenchUsed(player,hand,heldItem,hit);
            return true;
        }
        return super.onBlockActivated(world, pos, state, player, hand, side, hitX, hitY, hitZ);
    }

    @Override public void appendTooltip(ItemStack stack,BlockView world,List<net.minecraft.text.Text> tooltip,net.minecraft.client.item.TooltipContext context){
        super.appendTooltip(stack,world,tooltip,context);
        tooltip.add(net.minecraft.text.Text.translatable("block.buildcraftfactory.flood_gate.tip"));
        tooltip.add(net.minecraft.text.Text.literal("Remake by FelixMantra").formatted(net.minecraft.util.Formatting.GOLD));
    }
}
