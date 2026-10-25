/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.silicon;

import buildcraft.silicon.item.ItemGateCopier;
import buildcraft.silicon.item.ItemPluggableFacade;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.item.ItemPluggableLens;
import buildcraft.silicon.item.ItemPluggablePulsar;
import buildcraft.silicon.item.ItemRedstoneChipset;

/** Native gates and five NBT chipset variants; remaining pluggables await restoration. */
public class BCSiliconItems {

    // Other silicon items remain unregistered until their behavior is migrated.
    public static ItemRedstoneChipset redstoneChipset;
    public static ItemGateCopier gateCopier;
    public static ItemPluggableGate plugGate;
    public static ItemPluggableLens plugLens;
    public static ItemPluggablePulsar plugPulsar;
    // STUB(R.Chen): plugLightSensor and plugTimer items not yet stubbed as ItemPluggableSimple equivalent.
    public static ItemPluggableFacade plugFacade;

    public static void preInit() {
        redstoneChipset=net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new net.minecraft.util.Identifier("buildcraftsilicon","redstone_chipset"),new ItemRedstoneChipset(new net.minecraft.item.Item.Settings()));
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.REDSTONE)
            .register(entries->{for(var chipset:buildcraft.api.enums.EnumRedstoneChipset.values())entries.add(chipset.getStack());});
        plugGate = net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new net.minecraft.util.Identifier("buildcraftsilicon", "plug_gate"), new ItemPluggableGate(new net.minecraft.item.Item.Settings()));
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.REDSTONE)
            .register(entries -> ItemPluggableGate.variants().forEach(variant -> entries.add(plugGate.getStack(variant))));
    }
}
