package buildcraft.test.lib.world;

import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.builders.BCBuildersSchematics;
import buildcraft.builders.snapshot.*;
import java.io.StringReader;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class SchematicGameTests implements FabricGameTest {
    private static final BlockPos SOURCE = new BlockPos(1, 2, 1), TARGET = new BlockPos(4, 2, 4);
    public static void initialize() { RulesLoader.loadAll(); BCBuildersSchematics.preInit(); }
    private static SchematicBlockDefault capture(TestContext context, BlockPos local) {
        var pos = context.getAbsolutePos(local);
        var state = context.getWorld().getBlockState(pos);
        SchematicBlockDefault schematic = new SchematicBlockDefault();
        schematic.init(new SchematicBlockContext(context.getWorld(), context.getAbsolutePos(BlockPos.ORIGIN), pos, state, state.getBlock()));
        return schematic;
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void orientedBlockRotatesAndRoundTrips(TestContext context) throws Exception {
        context.setBlockState(SOURCE, Blocks.OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        var original = capture(context, SOURCE);
        var rotated = original.getRotated(BlockRotation.CLOCKWISE_90);
        var restored = new SchematicBlockDefault();
        restored.deserializeNBT(rotated.serializeNBT());
        context.assertTrue(restored.equals(rotated) && !restored.equals(original), "Rotation/copy changed the source or lost state");
        context.assertTrue(restored.build(context.getWorld(), context.getAbsolutePos(TARGET)), "Stair schematic failed to build");
        context.assertTrue(context.getWorld().getBlockState(context.getAbsolutePos(TARGET)).get(Properties.HORIZONTAL_FACING) == Direction.EAST,
            "Stair facing did not rotate");
        context.assertTrue(restored.isBuilt(context.getWorld(), context.getAbsolutePos(TARGET)), "Built stair is not recognized");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chestContentsAreRequiredAndCopiedIndependently(TestContext context) {
        context.setBlockState(SOURCE, Blocks.CHEST);
        var chest = (ChestBlockEntity) context.getBlockEntity(SOURCE);
        ItemStack named = new ItemStack(Items.DIAMOND, 7);
        named.getOrCreateNbt().putString("fixture", "preserved");
        chest.setStack(0, named);
        var schematic = capture(context, SOURCE);
        var required = schematic.computeRequiredItems();
        context.assertTrue(count(required, Items.CHEST) == 1 && count(required, Items.DIAMOND) == 7, "Chest/inventory requirements incorrect");
        required.stream().filter(stack -> stack.isOf(Items.DIAMOND)).findFirst().orElseThrow().setCount(1);
        context.assertTrue(count(schematic.computeRequiredItems(), Items.DIAMOND) == 7, "Requirements leaked mutable stored contents");
        context.assertTrue(schematic.build(context.getWorld(), context.getAbsolutePos(TARGET)), "Chest failed to build");
        var copy = (ChestBlockEntity) context.getBlockEntity(TARGET);
        context.assertTrue(copy.getStack(0).getCount() == 7 && "preserved".equals(copy.getStack(0).getNbt().getString("fixture")), "Chest contents/NBT were lost");
        copy.getStack(0).decrement(1);
        context.assertTrue(chest.getStack(0).getCount() == 7 && count(schematic.computeRequiredItems(), Items.DIAMOND) == 7,
            "Placed inventory shares mutable state with source or blueprint");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void modernInventoriesPayForAllContents(TestContext context) {
        context.setBlockState(SOURCE, Blocks.BARREL);
        var barrel = (BarrelBlockEntity) context.getBlockEntity(SOURCE);
        barrel.setStack(2, new ItemStack(Items.IRON_INGOT, 17));
        var schematic = capture(context, SOURCE);
        context.assertTrue(count(schematic.computeRequiredItems(), Items.BARREL) == 1
            && count(schematic.computeRequiredItems(), Items.IRON_INGOT) == 17, "New vanilla inventory contents must be charged");
        context.assertTrue(schematic.build(context.getWorld(), context.getAbsolutePos(TARGET)), "Barrel failed to restore");
        context.assertTrue(((BarrelBlockEntity) context.getBlockEntity(TARGET)).getStack(2).getCount() == 17, "Barrel slot changed");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void furnaceDoesNotCopyConsumedFuelOrExperience(TestContext context) {
        context.setBlockState(SOURCE, Blocks.FURNACE.getDefaultState().with(Properties.LIT, true));
        var furnace = (FurnaceBlockEntity) context.getBlockEntity(SOURCE);
        NbtCompound data = furnace.createNbt();
        data.putShort("BurnTime", (short) 200);
        data.putShort("CookTime", (short) 100);
        NbtCompound recipes = new NbtCompound(); recipes.putInt("minecraft:iron_ingot_from_smelting_iron_ore", 5);
        data.put("RecipesUsed", recipes);
        furnace.readNbt(data);
        var schematic = capture(context, SOURCE);
        context.assertTrue(schematic.build(context.getWorld(), context.getAbsolutePos(TARGET)), "Furnace failed to restore");
        NbtCompound copied = context.getBlockEntity(TARGET).createNbt();
        context.assertTrue(copied.getShort("BurnTime") == 0 && copied.getShort("CookTime") == 0 && copied.getCompound("RecipesUsed").isEmpty(),
            "Blueprint duplicated consumed furnace fuel or XP");
        context.assertTrue(!context.getWorld().getBlockState(context.getAbsolutePos(TARGET)).get(Properties.LIT), "Copied furnace remains artificially lit");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativeItemCountsCoverHalvesLayersAndPottedPlants(TestContext context) {
        RequiredExtractorItemFromBlock extractor = new RequiredExtractorItemFromBlock();
        context.assertTrue(count(extractor.extractItemsFromBlock(Blocks.OAK_SLAB.getDefaultState()
            .with(Properties.SLAB_TYPE, net.minecraft.block.enums.SlabType.DOUBLE), null), Items.OAK_SLAB) == 2, "Double slab requires two items");
        context.assertTrue(count(extractor.extractItemsFromBlock(Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 5), null), Items.SNOW) == 5,
            "Snow layers require all items");
        context.assertTrue(extractor.extractItemsFromBlock(Blocks.OAK_DOOR.getDefaultState()
            .with(Properties.DOUBLE_BLOCK_HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER), null).isEmpty(), "Door upper half charged twice");
        context.setBlockState(SOURCE, Blocks.POTTED_POPPY);
        var costs = capture(context, SOURCE).computeRequiredItems();
        context.assertTrue(count(costs, Items.FLOWER_POT) == 1 && count(costs, Items.POPPY) == 1, "Potted plant needs pot and flower");
        context.setBlockState(SOURCE, Blocks.FLOWER_POT);
        context.assertTrue(count(capture(context, SOURCE).computeRequiredItems(), Items.FLOWER_POT) == 1, "Empty native pot matched missing legacy NBT");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fluidsAndWaterloggedStatesHaveRealCosts(TestContext context) throws Exception {
        context.setBlockState(SOURCE, Blocks.WATER);
        var pos = context.getAbsolutePos(SOURCE);
        SchematicBlockFluid source = new SchematicBlockFluid();
        source.init(new SchematicBlockContext(context.getWorld(), pos, pos, Blocks.WATER.getDefaultState(), Blocks.WATER));
        context.assertTrue(source.computeRequiredFluids().size() == 1 && source.computeRequiredFluids().get(0).getAmount()
            == net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET, "Source must require one native bucket");
        context.assertTrue(source.build(context.getWorld(), context.getAbsolutePos(TARGET)), "Source failed to place");
        context.assertTrue(source.isBuilt(context.getWorld(), context.getAbsolutePos(TARGET)), "Source placement is not recognized");
        SchematicBlockFluid restored = new SchematicBlockFluid(); restored.deserializeNBT(source.serializeNBT());
        context.assertTrue(source.equals(restored), "Fluid schematic round trip failed");
        context.setBlockState(SOURCE, Blocks.OAK_SLAB.getDefaultState().with(Properties.WATERLOGGED, true));
        var wet = capture(context, SOURCE);
        context.assertTrue(count(wet.computeRequiredItems(), Items.OAK_SLAB) == 1 && wet.computeRequiredFluids().size() == 1,
            "Waterlogged block lost item or fluid requirements");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void invalidTileTypeDoesNotPartiallyPlace(TestContext context) throws Exception {
        context.setBlockState(SOURCE, Blocks.CHEST);
        var schematic = capture(context, SOURCE);
        NbtCompound malformed = schematic.serializeNBT();
        malformed.getCompound("tileNbt").putString("id", "minecraft:furnace");
        schematic.deserializeNBT(malformed);
        context.assertTrue(!schematic.build(context.getWorld(), context.getAbsolutePos(TARGET)), "Mismatched block entity was accepted");
        context.expectBlock(Blocks.AIR, TARGET);
        malformed.getCompound("blockState").putString("Name", "missing:never_registered");
        boolean rejected = false;
        try { schematic.deserializeNBT(malformed); } catch (InvalidInputDataException expected) { rejected = true; }
        context.assertTrue(rejected, "Unknown block silently became air");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nestedInventoryRulesAndMissingPathsAreSafe(TestContext context) {
        var rule = RulesLoader.parse(new StringReader("[{\"selectors\":[\"minecraft:chest\"],\"requiredExtractors\":[{\"type\":\"item\",\"path\":[\"Parent\",\"Entries\",\"0\",\"Item\"]}]}]")).get(0);
        NbtCompound root = new NbtCompound(), parent = new NbtCompound(), entry = new NbtCompound();
        NbtList list = new NbtList();
        entry.put("Item", new ItemStack(Items.DIAMOND, 9).writeNbt(new NbtCompound()));
        list.add(entry); parent.put("Entries", list); root.put("Parent", parent);
        var extractor = rule.requiredExtractors.get(0);
        context.assertTrue(count(extractor.extractItemsFromBlock(Blocks.CHEST.getDefaultState(), root), Items.DIAMOND) == 9, "Nested list path was not traversed");
        context.assertTrue(extractor.extractItemsFromBlock(Blocks.CHEST.getDefaultState(), null).isEmpty(), "Missing tile NBT must be empty");
        context.assertTrue(extractor.extractItemsFromBlock(Blocks.CHEST.getDefaultState(), new NbtCompound()).isEmpty(), "Missing path must be empty");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bundledRulesHandleNativeRegistryStates(TestContext context) {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        var world = context.getWorld();
        var pos = context.getAbsolutePos(SOURCE);
        for (Block block : net.minecraft.registry.Registries.BLOCK) {
            var id = net.minecraft.registry.Registries.BLOCK.getId(block);
            if (!id.getNamespace().equals("minecraft")) continue;
            try {
                var schematic = SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(world, pos, pos, block.getDefaultState(), block));
                schematic.computeRequiredItems();
                schematic.computeRequiredFluids();
                schematic.serializeNBT();
            } catch (RuntimeException failure) { errors.add(id + ": " + failure); }
        }
        context.assertTrue(errors.isEmpty(), "Native registry rule incompatibilities: " + errors);
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pistonHeadsAreReplacedByAirWithoutMaterials(TestContext context) {
        var pos = context.getAbsolutePos(SOURCE);
        var schematic = SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(context.getWorld(), pos, pos,
            Blocks.PISTON_HEAD.getDefaultState(), Blocks.PISTON_HEAD));
        context.assertTrue(schematic.isAir() && schematic.computeRequiredItems().isEmpty(), "Piston head rule did not suppress the secondary block");
        context.complete();
    }
    private static int count(List<ItemStack> items, net.minecraft.item.Item item) {
        return items.stream().filter(stack -> stack.isOf(item)).mapToInt(ItemStack::getCount).sum();
    }
}
