/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport;

import net.minecraft.item.Item;

import buildcraft.transport.item.ItemPipeHolder;
import buildcraft.transport.item.ItemWire;

/** Native transport items. */
public class BCTransportItems {

    public static ItemPipeHolder pipeStructure;
    public static ItemPipeHolder pipeItemWood, pipeFluidWood, pipePowerWood, pipeRfWood;
    public static ItemPipeHolder pipeItemStone, pipeFluidStone, pipePowerStone, pipeRfStone;
    public static ItemPipeHolder pipeItemCobble, pipeFluidCobble, pipePowerCobble, pipeRfCobble;
    public static ItemPipeHolder pipeItemQuartz, pipeFluidQuartz, pipePowerQuartz, pipeRfQuartz;
    public static ItemPipeHolder pipeItemGold, pipeFluidGold, pipePowerGold, pipeRfGold;
    public static ItemPipeHolder pipeItemSandstone, pipeFluidSandstone, pipePowerSandstone, pipeRfSandstone;
    public static ItemPipeHolder pipeItemIron, pipeFluidIron, pipePowerIron, pipeRfIron;
    public static ItemPipeHolder pipeItemDiamond, pipeFluidDiamond, pipePowerDiamond, pipeRfDiamond;
    public static ItemPipeHolder pipeItemDiaWood, pipeFluidDiaWood, pipePowerDiaWood, pipeRfDiaWood;
    public static ItemPipeHolder pipeItemClay, pipeFluidClay;
    public static ItemPipeHolder pipeItemVoid, pipeFluidVoid;
    public static ItemPipeHolder pipeItemObsidian, pipeFluidObsidian;
    public static ItemPipeHolder pipeItemLapis;
    public static ItemPipeHolder pipeItemDaizuli;
    public static ItemPipeHolder pipeItemEmzuli;
    public static ItemPipeHolder pipeItemStripes;

    public static Item waterproof;
    public static Item plugBlocker;
    public static Item plugPowerAdaptor;
    public static ItemWire wire;

    public static void preInit() {
        waterproof = net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new net.minecraft.util.Identifier("buildcrafttransport", "waterproof"), new Item(new Item.Settings()) {
                @Override public String getTranslationKey() { return "item.pipeWaterproof.name"; }
            });
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.INGREDIENTS)
            .register(entries -> entries.add(waterproof));
        wire = net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new net.minecraft.util.Identifier("buildcrafttransport", "wire"), new ItemWire("wire"));
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.REDSTONE).register(entries -> {
            for (var colour : net.minecraft.util.DyeColor.values()) entries.add(wire.stack(colour));
        });
        pipeStructure = makePipeItem(BCTransportPipes.structure);
        pipeItemWood = makePipeItem(BCTransportPipes.woodItem);
        pipeFluidWood = makePipeItem(BCTransportPipes.woodFluid);
        pipePowerWood = makePipeItem(BCTransportPipes.woodPower);
        pipeRfWood = makePipeItem(BCTransportPipes.woodRf);
        pipeItemStone = makePipeItem(BCTransportPipes.stoneItem);
        pipeFluidStone = makePipeItem(BCTransportPipes.stoneFluid);
        pipePowerStone = makePipeItem(BCTransportPipes.stonePower);
        pipeRfStone = makePipeItem(BCTransportPipes.stoneRf);
        pipeItemCobble = makePipeItem(BCTransportPipes.cobbleItem);
        pipeFluidCobble = makePipeItem(BCTransportPipes.cobbleFluid);
        pipePowerCobble = makePipeItem(BCTransportPipes.cobblePower);
        pipeRfCobble = makePipeItem(BCTransportPipes.cobbleRf);
        pipeItemQuartz = makePipeItem(BCTransportPipes.quartzItem);
        pipeFluidQuartz = makePipeItem(BCTransportPipes.quartzFluid);
        pipePowerQuartz = makePipeItem(BCTransportPipes.quartzPower);
        pipeRfQuartz = makePipeItem(BCTransportPipes.quartzRf);
        pipeItemGold = makePipeItem(BCTransportPipes.goldItem);
        pipeFluidGold = makePipeItem(BCTransportPipes.goldFluid);
        pipePowerGold = makePipeItem(BCTransportPipes.goldPower);
        pipeRfGold = makePipeItem(BCTransportPipes.goldRf);
        pipeItemSandstone = makePipeItem(BCTransportPipes.sandstoneItem);
        pipeFluidSandstone = makePipeItem(BCTransportPipes.sandstoneFluid);
        pipePowerSandstone = makePipeItem(BCTransportPipes.sandstonePower);
        pipeRfSandstone = makePipeItem(BCTransportPipes.sandstoneRf);
        pipeItemIron = makePipeItem(BCTransportPipes.ironItem);
        pipeFluidIron = makePipeItem(BCTransportPipes.ironFluid);
        pipePowerIron = makePipeItem(BCTransportPipes.ironPower);
        pipeRfIron = makePipeItem(BCTransportPipes.ironRf);
        pipeItemDiamond = makePipeItem(BCTransportPipes.diamondItem);
        pipeFluidDiamond = makePipeItem(BCTransportPipes.diamondFluid);
        pipePowerDiamond = makePipeItem(BCTransportPipes.diamondPower);
        pipeRfDiamond = makePipeItem(BCTransportPipes.diamondRf);
        pipeItemDiaWood = makePipeItem(BCTransportPipes.diaWoodItem);
        pipeFluidDiaWood = makePipeItem(BCTransportPipes.diaWoodFluid);
        pipePowerDiaWood = makePipeItem(BCTransportPipes.diaWoodPower);
        pipeRfDiaWood = makePipeItem(BCTransportPipes.diaWoodRf);
        pipeItemClay = makePipeItem(BCTransportPipes.clayItem);
        pipeFluidClay = makePipeItem(BCTransportPipes.clayFluid);
        pipeItemVoid = makePipeItem(BCTransportPipes.voidItem);
        pipeFluidVoid = makePipeItem(BCTransportPipes.voidFluid);
        pipeItemObsidian = makePipeItem(BCTransportPipes.obsidianItem);
        pipeItemLapis = makePipeItem(BCTransportPipes.lapisItem);
        pipeItemDaizuli = makePipeItem(BCTransportPipes.daizuliItem);
        pipeItemEmzuli = makePipeItem(BCTransportPipes.emzuliItem);
        pipeItemStripes = makePipeItem(BCTransportPipes.stripesItem);
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.FUNCTIONAL).register(entries -> ItemPipeHolder.items.forEach(entries::add));
    }

    public static ItemPipeHolder makePipeItem(buildcraft.api.transport.pipe.PipeDefinition definition) {
        return definition == null ? null : buildcraft.transport.pipe.PipeRegistry.INSTANCE.createItemForPipe(definition);
    }
}
