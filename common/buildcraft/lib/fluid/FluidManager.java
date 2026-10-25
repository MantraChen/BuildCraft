/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.fluid;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Registers a source/flowing pair, its world block, bucket and Transfer API attributes together. */
public final class FluidManager {
    private FluidManager() {}
    public static <F extends BCFluid> F register(F fluid) {
        Identifier id = fluid.getId();
        Identifier flowingId = new Identifier(id.getNamespace(), "flowing_" + id.getPath());
        Identifier blockId = new Identifier(id.getNamespace(), "fluid_block_" + fluid.getBlockName());
        Identifier bucketId = new Identifier(id.getNamespace(), id.getPath() + "_bucket");
        if (Registries.FLUID.containsId(id) || Registries.FLUID.containsId(flowingId)
                || Registries.BLOCK.containsId(blockId) || Registries.ITEM.containsId(bucketId)) {
            throw new IllegalArgumentException("Duplicate fluid registration: " + id);
        }
        Registry.register(Registries.FLUID, id, fluid);
        Registry.register(Registries.FLUID, flowingId, fluid.getFlowing());
        BCFluidBlock block = new BCFluidBlock(fluid, AbstractBlock.Settings.copy(Blocks.WATER)
            .mapColor(fluid.getMapColour()).strength(100).dropsNothing());
        BucketItem bucket = new BucketItem(fluid, new Item.Settings().recipeRemainder(Items.BUCKET).maxCount(1)) {
            @Override public Text getName(ItemStack stack) {
                return Text.translatable("item.buildcraftenergy.fluid_bucket", fluid.getName());
            }
        };
        fluid.attach(block, bucket);
        Registry.register(Registries.BLOCK, blockId, block);
        Registry.register(Registries.ITEM, bucketId, bucket);
        if (fluid.isFlammable()) FlammableBlockRegistry.getDefaultInstance().add(block, 200, 200);
        FluidVariantAttributeHandler attributes = new FluidVariantAttributeHandler() {
            @Override public Text getName(FluidVariant variant) { return fluid.getName(); }
            @Override public int getTemperature(FluidVariant variant) { return fluid.getTemperature(); }
            @Override public int getViscosity(FluidVariant variant, World world) {
                return fluid.getTickRate(world) * FluidConstants.VISCOSITY_RATIO;
            }
            @Override public boolean isLighterThanAir(FluidVariant variant) { return fluid.isGaseous(); }
        };
        FluidVariantAttributes.register(fluid, attributes);
        FluidVariantAttributes.register(fluid.getFlowing(), attributes);
        return fluid;
    }
}
