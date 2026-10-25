package buildcraft.test.lib.world;

import buildcraft.energy.BCEnergyFluids;
import buildcraft.lib.fluid.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.minecraft.block.*;
import net.minecraft.fluid.*;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;

/** Real scheduled fluid ticks and bucket placement, using production registrations. */
public class FluidGameTests implements FabricGameTest {
    private static final String CHANNEL = "buildcraft_port_tests:fluid_channel";
    public static void initialize() { BCEnergyFluids.register(true, true); }

    private static void channel(TestContext test) {
        // The template reserves the full area, so parallel tests cannot share these blocks.
        for (int x = 0; x < 18; x++) for (int y = 0; y < 6; y++) for (int z = 0; z < 5; z++) {
            test.setBlockState(x, y, z, Blocks.STONE);
        }
        for (int x = 1; x < 17; x++) test.setBlockState(x, 2, 2, Blocks.AIR);
    }

    private static FluidState fluid(TestContext test, int x, int y, int z) {
        return test.getWorld().getFluidState(test.getAbsolutePos(new BlockPos(x, y, z)));
    }

    @GameTest(templateName = CHANNEL)
    public void allHeatVariantsRegisterPairedFluidsBlocksBucketsAndAttributes(TestContext test) {
        test.assertTrue(BCEnergyFluids.allFluids.size() == 30, "Ten families must retain three heat variants each");
        test.assertTrue(buildcraft.lib.compat.FluidRegistryBC.getFluid("oil") == BCEnergyFluids.crudeOil[0],
            "Legacy unnamespaced save/script fluid names must resolve");
        for (BCFluid fluid : BCEnergyFluids.allFluids) {
            test.assertTrue(Registries.FLUID.get(fluid.getId()) == fluid && fluid.getFlowing() != fluid,
                "Each source must have its own registered flowing partner");
            test.assertTrue(fluid.matchesType(fluid.getFlowing()) && fluid.getFlowing().getStill() == fluid,
                "Source and flowing fluid must canonicalize to the same family and heat");
            test.assertTrue(Registries.BLOCK.getId(fluid.getBlock()).getNamespace().equals("buildcraftenergy")
                && fluid.getBucketItem() instanceof BucketItem, "Every variant needs a native block and bucket");
            FluidVariant variant = FluidVariant.of(fluid);
            test.assertTrue(FluidVariantAttributes.getTemperature(variant) == 300 + 20 * fluid.getHeatValue(),
                "Transfer API must expose actual heat");
            test.assertTrue(FluidVariantAttributes.isLighterThanAir(variant) == fluid.isGaseous(),
                "Transfer API must expose actual gas state");
            test.assertTrue(fluid.getTickRate(test.getWorld()) > 0, "Flow tick delay must be positive");
            test.assertTrue(fluid.getFlowing(Integer.MAX_VALUE, false).getLevel() == fluid.getQuantaPerBlock() - 1,
                "Flowing states must stay below source level and survive block-state conversion");
            for (int amount = 1; amount < fluid.getQuantaPerBlock(); amount++) {
                FluidState state = fluid.getFlowing(amount, true);
                FluidState roundTrip = state.getBlockState().getFluidState();
                test.assertTrue(roundTrip.equals(state), "World block must preserve levels beyond vanilla's eight");
            }
        }
        test.assertTrue(BCEnergyFluids.crudeOil[0].getBlock().isSticky(), "Sticky config must reach oil blocks");
        test.assertTrue(!BCEnergyFluids.fuelLight[0].getBlock().isSticky(), "Fuel must not inherit oil stickiness");
        test.assertTrue(FlammableBlockRegistry.getDefaultInstance().get(BCEnergyFluids.crudeOil[0].getBlock()) != null,
            "Burnable oil must be registered with native fire behavior");
        test.assertTrue(FlammableBlockRegistry.getDefaultInstance().get(BCEnergyFluids.oilResidue[0].getBlock()).getBurnChance() == 0,
            "Residue must remain nonflammable");
        test.complete();
    }

    @GameTest(templateName = CHANNEL)
    public void everyBucketPlacesAndOnlySourceCanBeCollected(TestContext test) {
        channel(test);
        BlockPos pos = test.getAbsolutePos(new BlockPos(2, 2, 2));
        for (BCFluid fluid : BCEnergyFluids.allFluids) {
            test.assertTrue(((BucketItem) fluid.getBucketItem()).placeFluid(null, test.getWorld(), pos, null),
                "Bucket must place its own registered fluid");
            test.assertTrue(test.getWorld().getFluidState(pos).getFluid() == fluid, "Bucket must place a source");
            ItemStack filled = fluid.getBlock().tryDrainFluid(test.getWorld(), pos, test.getWorld().getBlockState(pos));
            test.assertTrue(filled.isOf(fluid.getBucketItem()) && test.getWorld().getFluidState(pos).isEmpty(),
                "Collecting a source must yield exactly its bucket and remove the source");
            test.getWorld().setBlockState(pos, fluid.getFlowing(1, false).getBlockState());
            test.assertTrue(fluid.getBlock().tryDrainFluid(test.getWorld(), pos, test.getWorld().getBlockState(pos)).isEmpty(),
                "Flowing fluid must not duplicate source buckets");
            test.getWorld().removeBlock(pos, false);
        }
        test.complete();
    }

    @GameTest(templateName = CHANNEL, tickLimit = 100)
    public void oilFallsAndGasRises(TestContext test) {
        channel(test);
        test.setBlockState(2, 1, 2, Blocks.AIR);
        test.setBlockState(2, 2, 2, BCEnergyFluids.crudeOil[0].getBlock());
        test.setBlockState(12, 3, 2, Blocks.AIR);
        test.setBlockState(12, 2, 2, BCEnergyFluids.fuelGaseous[0].getBlock());
        test.runAtTick(25, () -> {
            test.assertTrue(BCEnergyFluids.crudeOil[0].matchesType(fluid(test, 2, 1, 2).getFluid()),
                "Oil must flow down under gravity");
            test.assertTrue(BCEnergyFluids.fuelGaseous[0].matchesType(fluid(test, 12, 3, 2).getFluid()),
                "Gaseous fuel must flow up");
            test.complete();
        });
    }

    @GameTest(templateName = CHANNEL, tickLimit = 160)
    public void horizontalFlowRetainsLegacyDistanceAndStopsAtItsLimit(TestContext test) {
        channel(test);
        BCFluid gas = BCEnergyFluids.fuelGaseous[2]; // 12 quanta, ceiling holds it in the channel.
        test.setBlockState(1, 2, 2, gas.getBlock());
        test.runAtTick(100, () -> {
            test.assertTrue(gas.getQuantaPerBlock() == 12, "Test must exercise levels beyond vanilla's eight");
            test.assertTrue(gas.matchesType(fluid(test, 12, 2, 2).getFluid()), "Gas must reach its 11th neighbor");
            test.assertTrue(fluid(test, 12, 2, 2).getLevel() == 1, "Last flowing cell must retain one quantum");
            test.assertTrue(fluid(test, 13, 2, 2).isEmpty(), "Flow must stop at the configured limit");
            test.complete();
        });
    }

    @GameTest(templateName = CHANNEL, tickLimit = 240)
    public void removingSourceDrainsFlowAndNeighborsDoNotCreateNewSources(TestContext test) {
        channel(test);
        BCFluid oil = BCEnergyFluids.crudeOil[0];
        test.setBlockState(1, 2, 2, oil.getBlock());
        test.setBlockState(3, 2, 2, oil.getBlock());
        test.runAtTick(35, () -> {
            test.assertTrue(oil.matchesType(fluid(test, 2, 2, 2).getFluid()) && !fluid(test, 2, 2, 2).isStill(),
                "Two neighboring oil sources must not create infinite oil");
            test.setBlockState(1, 2, 2, Blocks.AIR);
            test.setBlockState(3, 2, 2, Blocks.AIR);
        });
        test.runAtTick(200, () -> {
            for (int x = 1; x < 17; x++) test.assertTrue(fluid(test, x, 2, 2).isEmpty(),
                "Unsupported flowing oil must decay after source removal at " + x);
            test.complete();
        });
    }

    @GameTest(templateName = CHANNEL, tickLimit = 100)
    public void fluidsRespectSolidWallsAndRelativeDensity(TestContext test) {
        channel(test);
        test.setBlockState(4, 2, 2, Blocks.STONE);
        test.setBlockState(2, 2, 2, BCEnergyFluids.crudeOil[0].getBlock());
        test.setBlockState(3, 2, 2, Blocks.WATER);
        test.setBlockState(8, 2, 2, Blocks.STONE);
        test.setBlockState(11, 2, 2, BCEnergyFluids.oilResidue[0].getBlock());
        test.setBlockState(12, 2, 2, Blocks.WATER);
        test.runAtTick(30, () -> {
            test.assertTrue(fluid(test, 3, 2, 2).getFluid().matchesType(Fluids.WATER), "Light oil must not displace water");
            test.expectBlock(Blocks.STONE, new BlockPos(4, 2, 2));
            test.assertTrue(BCEnergyFluids.oilResidue[0].matchesType(fluid(test, 12, 2, 2).getFluid()),
                "Dense residue must displace free water");
            test.complete();
        });
    }
}
