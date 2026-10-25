/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import buildcraft.api.inventory.IItemTransactor;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

public interface ITileForBlueprintBuilder extends ITileForSnapshotBuilder {
    default boolean isBlueprintWorkValid(){return true;}
    Blueprint.BuildingInfo getBlueprintBuildingInfo();

    IItemTransactor getInvResources();

    Storage<ItemVariant> getResourceStorage();

    Storage<FluidVariant> getTankManager();
}
