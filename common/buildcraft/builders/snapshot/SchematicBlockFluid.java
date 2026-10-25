/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.snapshot;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.lib.compat.FluidStackBC;

public class SchematicBlockFluid implements ISchematicBlock {
    private BlockState blockState;
    private boolean isFlowing;
    public static boolean predicate(SchematicBlockContext context) { return context.block instanceof FluidBlock; }
    @Override public void init(SchematicBlockContext context) {
        blockState = context.blockState;
        isFlowing = !blockState.getFluidState().isStill();
    }
    private Fluid fluid() {
        Fluid fluid = blockState.getFluidState().getFluid();
        return fluid instanceof FlowableFluid flowing ? flowing.getStill() : fluid;
    }
    @Override public Set<BlockPos> getRequiredBlockOffsets() {
        return Stream.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN)
            .map(BlockPos.ORIGIN::offset).collect(Collectors.toUnmodifiableSet());
    }
    @Override public List<FluidStackBC> computeRequiredFluids() {
        return isFlowing ? List.of() : List.of(new FluidStackBC(fluid(), 1000));
    }
    @Override public SchematicBlockFluid getRotated(BlockRotation rotation) {
        SchematicBlockFluid copy = new SchematicBlockFluid();
        copy.blockState = blockState; copy.isFlowing = isFlowing;
        return copy;
    }
    @Override public boolean canBuild(World world, BlockPos pos) {
        if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos) || !world.isChunkLoaded(pos)) return false;
        if (world.getDimension().ultrawarm() && blockState.getFluidState().isIn(FluidTags.WATER)) return false;
        var existing = world.getFluidState(pos);
        return world.isAir(pos) || (world.getBlockState(pos).getBlock() instanceof FluidBlock
            && !existing.isStill() && existing.getFluid().matchesType(fluid()));
    }
    @Override public boolean build(World world, BlockPos pos) {
        return !world.isClient && canBuild(world, pos) && (isFlowing || world.setBlockState(pos, blockState, Block.NOTIFY_ALL));
    }
    @Override public boolean buildWithoutChecks(World world, BlockPos pos) {
        return !world.isClient && world.isInBuildLimit(pos) && world.isChunkLoaded(pos)
            && world.setBlockState(pos, blockState, Block.NOTIFY_ALL);
    }
    @Override public boolean isBuilt(World world, BlockPos pos) {
        return isFlowing || (world.getFluidState(pos).isStill() && world.getFluidState(pos).getFluid().matchesType(fluid()));
    }
    @Override public NbtCompound serializeNBT() {
        NbtCompound tag = new NbtCompound();
        tag.put("blockState", NbtHelper.fromBlockState(blockState));
        tag.putBoolean("isFlowing", isFlowing);
        return tag;
    }
    @Override public void deserializeNBT(NbtCompound tag) throws InvalidInputDataException {
        NbtCompound stateTag = tag.getCompound("blockState");
        Identifier id = Identifier.tryParse(stateTag.getString("Name"));
        if (id == null || !Registries.BLOCK.containsId(id) || !(Registries.BLOCK.get(id) instanceof FluidBlock))
            throw new InvalidInputDataException("Not a fluid block schematic: " + id);
        blockState = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), stateTag);
        isFlowing = !blockState.getFluidState().isStill();
    }
    @Override public boolean equals(Object other) {
        return other instanceof SchematicBlockFluid fluid && blockState == fluid.blockState && isFlowing == fluid.isFlowing;
    }
    @Override public int hashCode() { return Objects.hash(blockState, isFlowing); }
}
