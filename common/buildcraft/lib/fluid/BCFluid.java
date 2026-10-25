/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.fluid;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.block.*;
import net.minecraft.fluid.*;
import net.minecraft.item.Item;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.*;
import buildcraft.lib.compat.FluidStackBC;

/** Native finite-source fluid with the legacy 1–16 quanta and reversible gravity. */
public class BCFluid extends FlowableFluid {
    public static final IntProperty AMOUNT = IntProperty.of("amount", 1, 16);
    private final BCFluid source;
    private final BCFluid flowing;
    private final Identifier id, stillTexture, flowTexture;
    private int colour = -1, light = -1, dark = -1, heat, opacity = 3;
    private int density = 1000, viscosity = 1000, temperature = 300, quanta = 8;
    private boolean flammable, heatable;
    private MapColor mapColour = MapColor.BLACK;
    private String blockName, translationName;
    private BCFluidBlock block;
    private Item bucket;

    public BCFluid(String name, Identifier still, Identifier flow) {
        id = name.contains(":") ? new Identifier(name) : new Identifier("buildcraftenergy", name);
        source = this;
        stillTexture = still;
        flowTexture = flow;
        blockName = id.getPath();
        translationName = id.getPath();
        flowing = new BCFluid(this);
    }

    private BCFluid(BCFluid source) {
        this.source = source;
        flowing = this;
        id = source.id;
        stillTexture = source.stillTexture;
        flowTexture = source.flowTexture;
    }

    public Identifier getId() { return source.id; }
    public Identifier getStillTexture() { return stillTexture; }
    public Identifier getFlowTexture() { return flowTexture; }
    public Text getName() {
        Text bare = Text.translatable("fluid." + getId().getNamespace() + "." + source.translationName);
        return source.heatable ? Text.translatable("buildcraft.fluid.heat_" + source.heat, bare) : bare;
    }
    public String getBareLocalizedName(FluidStackBC stack) {
        return Text.translatable("fluid." + getId().getNamespace() + "." + source.translationName).getString();
    }
    public String getLocalizedName(FluidStackBC stack) { return getName().getString(); }
    public void setUnlocalizedName(String name) { source.translationName = name; }
    public void setMapColour(MapColor color) { source.mapColour = color; }
    public MapColor getMapColour() { return source.mapColour; }
    public void setFlammable(boolean value) { source.flammable = value; }
    public boolean isFlammable() { return source.flammable; }
    public void setLightOpacity(int value) { source.opacity = MathHelper.clamp(value, 0, 15); }
    public int getLightOpacity() { return source.opacity; }
    public void setBlockName(String name) { source.blockName = name; }
    public String getBlockName() { return source.blockName; }
    public int getColor() { return source.colour; }
    public int getLightColour() { return source.light; }
    public int getDarkColour() { return source.dark; }
    public BCFluid setColour(int color) { source.colour = color; return this; }
    public BCFluid setColour(int light, int dark) {
        source.light = light; source.dark = dark; source.colour = -1; return this;
    }
    public BCFluid setHeat(int value) { source.heat = value; return this; }
    public int getHeatValue() { return source.heat; }
    public BCFluid setHeatable(boolean value) { source.heatable = value; return this; }
    public boolean isHeatable() { return source.heatable; }
    public void setDensity(int value) { source.density = value; }
    public int getDensity() { return source.density; }
    public boolean isGaseous() { return source.density < 0; }
    public void setViscosity(int value) {
        if (value <= 0) throw new IllegalArgumentException("Viscosity must be positive");
        source.viscosity = value;
    }
    public int getViscosity() { return source.viscosity; }
    public void setTemperature(int value) {
        if (value < 0) throw new IllegalArgumentException("Temperature must be nonnegative");
        source.temperature = value;
    }
    public int getTemperature() { return source.temperature; }
    public void setQuantaPerBlock(int value) {
        if (value < 2 || value > 16) throw new IllegalArgumentException("Quanta must be between 2 and 16");
        source.quanta = value;
    }
    public int getQuantaPerBlock() { return source.quanta; }
    public BCFluidBlock getBlock() { return source.block; }
    void attach(BCFluidBlock block, Item bucket) { source.block = block; source.bucket = bucket; }
    @Override public Item getBucketItem() { return source.bucket; }
    @Override public BCFluid getStill() { return source; }
    @Override public BCFluid getFlowing() { return source.flowing; }
    @Override public boolean matchesType(Fluid fluid) { return fluid instanceof BCFluid bc && bc.source == source; }
    @Override public boolean isStill(FluidState state) { return this == source; }
    @Override public int getLevel(FluidState state) { return isStill(state) ? getQuantaPerBlock() : state.get(AMOUNT); }
    @Override protected void appendProperties(StateManager.Builder<Fluid, FluidState> builder) {
        super.appendProperties(builder);
        builder.add(AMOUNT);
    }
    @Override public FluidState getFlowing(int amount, boolean falling) {
        return getFlowing().getDefaultState().with(AMOUNT, MathHelper.clamp(amount, 1, getQuantaPerBlock() - 1))
            .with(FALLING, falling);
    }
    @Override protected boolean isInfinite(World world) { return false; }
    @Override protected int getFlowSpeed(WorldView world) { return getQuantaPerBlock(); }
    @Override protected int getLevelDecreasePerBlock(WorldView world) { return 1; }
    @Override public int getTickRate(WorldView world) { return Math.max(1, getViscosity() / 200); }
    @Override protected float getBlastResistance() { return 100; }
    @Override public Optional<SoundEvent> getBucketFillSound() { return Optional.of(SoundEvents.ITEM_BUCKET_FILL); }
    @Override public BlockState toBlockState(FluidState state) {
        return getBlock().getDefaultState()
            .with(FluidBlock.LEVEL, state.isStill() ? 0 : Math.max(1, getQuantaPerBlock() - state.getLevel()))
            .with(BCFluidBlock.VERTICAL_FLOW, state.get(FALLING));
    }
    @Override public float getHeight(FluidState state) {
        return Math.min(1, state.getLevel() / (getQuantaPerBlock() + 1f));
    }
    @Override public float getHeight(FluidState state, BlockView world, BlockPos pos) {
        return matchesType(world.getFluidState(pos.offset(gravity().getOpposite())).getFluid()) ? 1 : getHeight(state);
    }
    @Override public VoxelShape getShape(FluidState state, BlockView world, BlockPos pos) {
        double height = getHeight(state, world, pos);
        return isGaseous() ? VoxelShapes.cuboid(0, 1 - height, 0, 1, 1, 1)
            : VoxelShapes.cuboid(0, 0, 0, 1, height, 1);
    }
    private Direction gravity() { return isGaseous() ? Direction.UP : Direction.DOWN; }

    @Override protected boolean canBeReplacedWith(FluidState state, BlockView world, BlockPos pos,
            Fluid incoming, Direction direction) {
        if (matchesType(incoming)) return !state.isStill();
        int incomingDensity = incoming instanceof BCFluid bc ? bc.getDensity()
            : incoming.matchesType(Fluids.WATER) ? 1000 : incoming.matchesType(Fluids.LAVA) ? 9000 : 0;
        return direction == Direction.DOWN && incomingDensity > getDensity();
    }

    private boolean connected(BlockView world, BlockPos from, BlockPos to, Direction direction) {
        return !VoxelShapes.adjacentSidesCoverSquare(world.getBlockState(from).getCollisionShape(world, from),
            world.getBlockState(to).getCollisionShape(world, to), direction);
    }

    private boolean canEnter(World world, BlockPos from, BlockPos to, Direction direction) {
        if (world.isOutOfHeightLimit(to) || !world.isChunkLoaded(to)) return false;
        FluidState target = world.getFluidState(to);
        if (!target.isEmpty() && !matchesType(target.getFluid())) {
            int density = target.getFluid() instanceof BCFluid bc ? bc.getDensity()
                : target.getFluid().matchesType(Fluids.WATER) ? 1000
                : target.getFluid().matchesType(Fluids.LAVA) ? 9000 : Integer.MAX_VALUE;
            if (getDensity() <= density) return false;
            // Only replace a free fluid block; never destroy a waterlogged solid block.
            if (world.getBlockState(to).getBlock() instanceof FluidBlock) return connected(world, from, to, direction);
        }
        return super.canFlow(world, from, world.getBlockState(from), direction, to, world.getBlockState(to),
            target, getFlowing());
    }

    @Override protected FluidState getUpdatedState(World world, BlockPos pos, BlockState blockState) {
        if (matchesType(blockState.getFluidState().getFluid()) && blockState.getFluidState().isStill()) {
            return getStill(false);
        }
        BlockPos upstream = pos.offset(gravity().getOpposite());
        if (matchesType(world.getFluidState(upstream).getFluid()) && connected(world, upstream, pos, gravity())) {
            return getFlowing(getQuantaPerBlock() - 1, true);
        }
        int amount = 0;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = pos.offset(direction);
            if (!world.isChunkLoaded(neighbor)) continue;
            FluidState fluid = world.getFluidState(neighbor);
            if (matchesType(fluid.getFluid()) && connected(world, pos, neighbor, direction)) {
                amount = Math.max(amount, fluid.getLevel() - 1);
            }
        }
        return amount > 0 ? getFlowing(amount, false) : Fluids.EMPTY.getDefaultState();
    }

    @Override protected void tryFlow(World world, BlockPos pos, FluidState state) {
        if (state.isEmpty()) return;
        Direction gravity = gravity();
        BlockPos vertical = pos.offset(gravity);
        if (canEnter(world, pos, vertical, gravity)) {
            FluidState next = getFlowing(getQuantaPerBlock() - 1, true);
            if (!world.getFluidState(vertical).equals(next)) {
                flow(world, vertical, world.getBlockState(vertical), gravity, next);
            }
            return;
        }
        // A vertical stream already present continues through this block instead of spreading sideways.
        FluidState below = world.getFluidState(vertical);
        if (matchesType(below.getFluid()) && !below.isStill()) return;
        int amount = state.get(FALLING) ? getQuantaPerBlock() - 1 : state.getLevel() - 1;
        if (amount <= 0) return;
        int best = Integer.MAX_VALUE;
        java.util.Map<Direction, Integer> distances = new java.util.EnumMap<>(Direction.class);
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = pos.offset(direction);
            if (!canEnter(world, pos, neighbor, direction)) continue;
            FluidState target = world.getFluidState(neighbor);
            if (matchesType(target.getFluid()) && target.getLevel() >= amount) continue;
            int distance = distanceToDrop(world, neighbor, pos, Math.min(amount, 4));
            distances.put(direction, distance);
            best = Math.min(best, distance);
        }
        for (var entry : distances.entrySet()) {
            if (entry.getValue() != best) continue;
            BlockPos neighbor = pos.offset(entry.getKey());
            flow(world, neighbor, world.getBlockState(neighbor), entry.getKey(), getFlowing(amount, false));
        }
    }

    /** Search for the nearest vertical outlet, bounded like vanilla's flow search. */
    private int distanceToDrop(World world, BlockPos start, BlockPos excluded, int maxDistance) {
        record Step(BlockPos pos, int distance) {}
        ArrayDeque<Step> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(excluded); visited.add(start); queue.add(new Step(start, 0));
        while (!queue.isEmpty()) {
            Step step = queue.removeFirst();
            BlockPos vertical = step.pos.offset(gravity());
            if (canEnter(world, step.pos, vertical, gravity())) return step.distance;
            if (step.distance >= maxDistance) continue;
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos neighbor = step.pos.offset(direction);
                if (!visited.contains(neighbor) && canEnter(world, step.pos, neighbor, direction)) {
                    visited.add(neighbor); queue.addLast(new Step(neighbor, step.distance + 1));
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    @Override protected void beforeBreakingBlock(WorldAccess world, BlockPos pos, BlockState state) {
        Block.dropStacks(state, world, pos, state.hasBlockEntity() ? world.getBlockEntity(pos) : null);
    }

    @Override public Vec3d getVelocity(BlockView world, BlockPos pos, FluidState state) {
        Vec3d velocity = super.getVelocity(world, pos, state);
        return isGaseous() ? new Vec3d(velocity.x, -velocity.y, velocity.z) : velocity;
    }
}
