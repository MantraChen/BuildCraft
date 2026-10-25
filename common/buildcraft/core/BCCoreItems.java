/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core;

import buildcraft.api.items.FluidItemDrops;

import buildcraft.lib.BCLib;
import buildcraft.lib.item.ItemBC_Neptune;
import buildcraft.lib.registry.RegistrationHelper;

import buildcraft.core.item.ItemFragileFluidContainer;
import buildcraft.core.item.ItemGoggles;
import buildcraft.core.item.ItemList_BC8;
import buildcraft.core.item.ItemMapLocation;
import buildcraft.core.item.ItemMarkerConnector;
import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.core.item.ItemVolumeBox;
import buildcraft.core.item.ItemWrench_Neptune;

public class BCCoreItems {

    private static final RegistrationHelper HELPER = new RegistrationHelper();

    public static ItemWrench_Neptune wrench;
    public static ItemBC_Neptune gearWood;
    public static ItemBC_Neptune gearStone;
    public static ItemBC_Neptune gearIron;
    public static ItemBC_Neptune gearGold;
    public static ItemBC_Neptune gearDiamond;
    public static ItemPaintbrush_BC8 paintbrush;
    public static ItemList_BC8 list;
    public static ItemMapLocation mapLocation;
    public static ItemMarkerConnector markerConnector;
    public static ItemVolumeBox volumeBox;
    public static ItemFragileFluidContainer fragileFluidShard;
    public static ItemGoggles goggles;

    public static void preInit() {
        wrench = HELPER.addItem("item.wrench", () -> new ItemWrench_Neptune("item.wrench"));
        gearWood = HELPER.addItem("item.gear.wood", () -> new ItemBC_Neptune("item.gear.wood"));
        gearStone = HELPER.addItem("item.gear.stone", () -> new ItemBC_Neptune("item.gear.stone"));
        gearIron = HELPER.addItem("item.gear.iron", () -> new ItemBC_Neptune("item.gear.iron"));
        gearGold = HELPER.addItem("item.gear.gold", () -> new ItemBC_Neptune("item.gear.gold"));
        gearDiamond = HELPER.addItem("item.gear.diamond", () -> new ItemBC_Neptune("item.gear.diamond"));
        paintbrush = HELPER.addItem("item.paintbrush", () -> new ItemPaintbrush_BC8(new net.minecraft.item.Item.Settings().maxCount(1), "item.paintbrush"));
        list = HELPER.addItem("item.list", () -> new ItemList_BC8("item.list"));
        mapLocation = HELPER.addItem("item.map_location", () -> new ItemMapLocation("item.map_location"));
        markerConnector = HELPER.addItem("item.marker_connector", () -> new ItemMarkerConnector("item.marker_connector"));
        volumeBox = HELPER.addItem("item.volume_box", () -> new ItemVolumeBox("item.volume_box"));
        fragileFluidShard = HELPER.addItem("item.fragile_fluid_shard", () -> new ItemFragileFluidContainer(new net.minecraft.item.Item.Settings(), "item.fragile_fluid_shard"));
        if (BCLib.DEV) {
            goggles = HELPER.addItem("item.goggles", () -> new ItemGoggles("item.goggles"));
        }
        FluidItemDrops.item = fragileFluidShard;
        if (fragileFluidShard != null) net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.ITEM.registerForItems(
            (stack, context) -> fragileFluidShard.storage(context), fragileFluidShard);
    }
}
