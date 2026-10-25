/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.misc.NBTUtilBC;

public class SchematicBlockDefault implements ISchematicBlock {
    protected final Set<BlockPos> requiredBlockOffsets = new LinkedHashSet<>();
    protected BlockState blockState;
    protected final List<Property<?>> ignoredProperties = new ArrayList<>();
    protected NbtCompound tileNbt;
    protected BlockRotation tileRotation = BlockRotation.NONE;
    protected Block placeBlock;
    protected final Set<BlockPos> updateBlockOffsets = new LinkedHashSet<>();
    protected final Set<Block> canBeReplacedWithBlocks = new HashSet<>();

    private static NbtCompound tileData(SchematicBlockContext context) {
        BlockEntity tile = context.world.getBlockEntity(context.pos);
        return tile == null ? null : tile.createNbtWithId();
    }
    public static boolean predicate(SchematicBlockContext context) {
        return !context.blockState.isAir() && RulesLoader.READ_DOMAINS.contains(Registries.BLOCK.getId(context.block).getNamespace())
            && RulesLoader.getRules(context.blockState, tileData(context)).stream().noneMatch(rule -> rule.ignore);
    }
    @Override public void init(SchematicBlockContext context) {
        blockState = context.blockState;
        tileNbt = tileData(context);
        Set<JsonRule> rules = RulesLoader.getRules(blockState, tileNbt);
        requiredBlockOffsets.clear(); ignoredProperties.clear(); updateBlockOffsets.clear(); canBeReplacedWithBlocks.clear();
        tileRotation = BlockRotation.NONE;
        placeBlock = context.block;
        for (JsonRule rule : rules) {
            if (rule.requiredBlockOffsets != null) requiredBlockOffsets.addAll(rule.requiredBlockOffsets);
            if (rule.ignoredProperties != null) for (String name : rule.ignoredProperties) {
                Property<?> property = blockState.getBlock().getStateManager().getProperty(name);
                if (property != null && !ignoredProperties.contains(property)) ignoredProperties.add(property);
            }
            if (rule.updateBlockOffsets != null) updateBlockOffsets.addAll(rule.updateBlockOffsets);
            if (rule.placeBlock != null) placeBlock = registeredBlock(rule.placeBlock);
            if (rule.canBeReplacedWithBlocks != null) for (String name : rule.canBeReplacedWithBlocks) {
                Identifier id = Identifier.tryParse(name);
                // Old rules list lit/unlit block IDs that became state properties in 1.13.
                if (id != null && Registries.BLOCK.containsId(id)) canBeReplacedWithBlocks.add(Registries.BLOCK.get(id));
            }
        }
        if (context.block instanceof FallingBlock) requiredBlockOffsets.add(new BlockPos(0, -1, 0));
        if (updateBlockOffsets.isEmpty()) for (Direction side : Direction.values()) updateBlockOffsets.add(BlockPos.ORIGIN.offset(side));
        canBeReplacedWithBlocks.add(blockState.getBlock());
        canBeReplacedWithBlocks.add(placeBlock);
    }
    private static Block registeredBlock(String name) {
        Identifier id = Identifier.tryParse(name);
        if (id == null || !Registries.BLOCK.containsId(id)) throw new IllegalArgumentException("Unknown schematic block " + name);
        return Registries.BLOCK.get(id);
    }
    @Override public Set<BlockPos> getRequiredBlockOffsets() { return Set.copyOf(requiredBlockOffsets); }
    @Override public boolean isAir() { return placeBlock == Blocks.AIR; }
    private List<RequiredExtractor> extractors() {
        Set<JsonRule> rules = RulesLoader.getRules(blockState, tileNbt);
        if (rules.stream().noneMatch(rule -> rule.requiredExtractors != null)) return List.of(new RequiredExtractorItemFromBlock());
        return rules.stream().filter(rule -> rule.requiredExtractors != null)
            .flatMap(rule -> rule.requiredExtractors.stream()).toList();
    }
    @Override public List<ItemStack> computeRequiredItems() {
        List<RequiredExtractor> extractors = extractors();
        List<ItemStack> items = new ArrayList<>();
        extractors.forEach(extractor -> items.addAll(extractor.extractItemsFromBlock(blockState, tileNbt)));
        // New vanilla inventories (barrels, blast furnaces, etc.) use the same Items format.
        if (tileNbt != null && tileNbt.contains("Items", 9) && extractors.stream().noneMatch(RequiredExtractorItemsList.class::isInstance)) {
            NbtList contents = tileNbt.getList("Items", 10);
            for (int i = 0; i < contents.size(); i++) items.add(ItemStack.fromNbt(contents.getCompound(i)));
        }
        return items.stream().filter(stack -> stack != null && !stack.isEmpty()).map(ItemStack::copy).toList();
    }
    @Override public List<FluidStackBC> computeRequiredFluids() {
        List<FluidStackBC> fluids = new ArrayList<>();
        extractors().forEach(extractor -> fluids.addAll(extractor.extractFluidsFromBlock(blockState, tileNbt)));
        if (blockState.contains(Properties.WATERLOGGED) && blockState.get(Properties.WATERLOGGED))
            fluids.add(new FluidStackBC(net.minecraft.fluid.Fluids.WATER, 1000));
        return fluids.stream().filter(Objects::nonNull).map(FluidStackBC::copy).toList();
    }
    @Override public SchematicBlockDefault getRotated(BlockRotation rotation) {
        SchematicBlockDefault copy = SchematicBlockManager.createCleanCopy(this);
        requiredBlockOffsets.forEach(pos -> copy.requiredBlockOffsets.add(pos.rotate(rotation)));
        updateBlockOffsets.forEach(pos -> copy.updateBlockOffsets.add(pos.rotate(rotation)));
        copy.blockState = blockState.rotate(rotation);
        copy.ignoredProperties.addAll(ignoredProperties);
        copy.tileNbt = tileNbt == null ? null : tileNbt.copy();
        copy.tileRotation = tileRotation.rotate(rotation);
        copy.placeBlock = placeBlock;
        copy.canBeReplacedWithBlocks.addAll(canBeReplacedWithBlocks);
        return copy;
    }
    @Override public boolean canBuild(World world, BlockPos pos) {
        return world.isInBuildLimit(pos) && world.getWorldBorder().contains(pos) && world.isChunkLoaded(pos) && world.isAir(pos);
    }
    @Override public boolean isReadyToBuild(World world, BlockPos pos) {
        return placementState().canPlaceAt(world, pos);
    }
    public BlockState placementState() {
        BlockState state = placeBlock.getDefaultState();
        for (Property<?> property : blockState.getProperties()) if (state.contains(property) && !ignoredProperties.contains(property))
            state = copyProperty(state, blockState, property);
        return state;
    }
    private static <T extends Comparable<T>> BlockState copyProperty(BlockState target, BlockState source, Property<T> property) {
        return target.with(property, source.get(property));
    }
    private NbtCompound placementData(BlockPos pos, boolean applyRules) {
        if (tileNbt == null) return null;
        NbtCompound data = tileNbt.copy();
        if (applyRules) for (JsonRule rule : RulesLoader.getRules(blockState, tileNbt)) if (rule.replaceNbt != null)
            data = (NbtCompound) NBTUtilBC.merge(data, rule.replaceNbt).copy();
        data.putInt("x", pos.getX()); data.putInt("y", pos.getY()); data.putInt("z", pos.getZ());
        // A blueprint copies contents as paid items; it must not generate unopened structure loot again.
        data.remove("LootTable"); data.remove("LootTableSeed");
        return data;
    }
    @Override public boolean build(World world, BlockPos pos) {
        if (world.isClient || !canBuild(world, pos) || !isReadyToBuild(world, pos)) return false;
        return place(world, pos, placementState(), true);
    }
    @Override public boolean buildWithoutChecks(World world, BlockPos pos) {
        if (world.isClient || !world.isInBuildLimit(pos) || !world.isChunkLoaded(pos)) return false;
        return place(world, pos, blockState, false);
    }
    private boolean place(World world, BlockPos pos, BlockState state, boolean rules) {
        NbtCompound data = placementData(pos, rules);
        BlockEntity restored = data == null || !state.hasBlockEntity() ? null : BlockEntity.createFromNbt(pos, state, data);
        if (data != null && state.hasBlockEntity() && (restored == null || !restored.getType().supports(state))) return false;
        if (!world.setBlockState(pos, state, Block.NOTIFY_ALL)) return false;
        if (restored != null) {
            world.addBlockEntity(restored);
            restored.markDirty();
            world.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
        }
        for (BlockPos offset : updateBlockOffsets) {
            BlockPos neighbour = pos.add(offset);
            if (world.isChunkLoaded(neighbour)) world.updateNeighbors(neighbour, placeBlock);
        }
        return true;
    }
    @Override public boolean isBuilt(World world, BlockPos pos) {
        BlockState current = world.getBlockState(pos);
        if (!canBeReplacedWithBlocks.contains(current.getBlock())) return false;
        for (Property<?> property : blockState.getProperties()) if (!ignoredProperties.contains(property)
            && (!current.contains(property) || !current.get(property).equals(blockState.get(property)))) return false;
        return true;
    }
    @Override public NbtCompound serializeNBT() {
        NbtCompound nbt = new NbtCompound();
        nbt.put("requiredBlockOffsets", NBTUtilBC.writeCompoundList(requiredBlockOffsets.stream().map(NbtHelper::fromBlockPos)));
        nbt.put("blockState", NbtHelper.fromBlockState(blockState));
        nbt.put("ignoredProperties", NBTUtilBC.writeStringList(ignoredProperties.stream().map(Property::getName)));
        if (tileNbt != null) nbt.put("tileNbt", tileNbt.copy());
        nbt.put("tileRotation", NBTUtilBC.writeEnum(tileRotation));
        nbt.putString("placeBlock", Registries.BLOCK.getId(placeBlock).toString());
        nbt.put("updateBlockOffsets", NBTUtilBC.writeCompoundList(updateBlockOffsets.stream().map(NbtHelper::fromBlockPos)));
        nbt.put("canBeReplacedWithBlocks", NBTUtilBC.writeStringList(canBeReplacedWithBlocks.stream()
            .map(Registries.BLOCK::getId).map(Object::toString).sorted()));
        return nbt;
    }
    @Override public void deserializeNBT(NbtCompound nbt) throws InvalidInputDataException {
        try {
            NbtCompound stateTag = nbt.getCompound("blockState");
            registeredBlock(stateTag.getString("Name"));
            blockState = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), stateTag);
            placeBlock = registeredBlock(nbt.getString("placeBlock"));
            requiredBlockOffsets.clear(); ignoredProperties.clear(); updateBlockOffsets.clear(); canBeReplacedWithBlocks.clear();
            NBTUtilBC.readCompoundList(nbt.get("requiredBlockOffsets")).map(NbtHelper::toBlockPos).forEach(requiredBlockOffsets::add);
            NBTUtilBC.readStringList(nbt.get("ignoredProperties")).map(name -> blockState.getBlock().getStateManager().getProperty(name))
                .filter(Objects::nonNull).forEach(ignoredProperties::add);
            tileNbt = nbt.contains("tileNbt", 10) ? nbt.getCompound("tileNbt").copy() : null;
            tileRotation = NBTUtilBC.readEnum(nbt.get("tileRotation"), BlockRotation.class);
            if (tileRotation == null) tileRotation = BlockRotation.NONE;
            NBTUtilBC.readCompoundList(nbt.get("updateBlockOffsets")).map(NbtHelper::toBlockPos).forEach(updateBlockOffsets::add);
            NBTUtilBC.readStringList(nbt.get("canBeReplacedWithBlocks")).map(SchematicBlockDefault::registeredBlock).forEach(canBeReplacedWithBlocks::add);
            canBeReplacedWithBlocks.add(blockState.getBlock()); canBeReplacedWithBlocks.add(placeBlock);
        } catch (IllegalArgumentException failure) {
            throw new InvalidInputDataException("Invalid block schematic", failure);
        }
    }
    @Override public boolean equals(Object other) {
        return other != null && getClass() == other.getClass() && serializeNBT().equals(((SchematicBlockDefault) other).serializeNBT());
    }
    @Override public int hashCode() { return serializeNBT().hashCode(); }
}
