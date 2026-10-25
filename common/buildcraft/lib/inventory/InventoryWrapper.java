/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.inventory;

import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.minecraft.inventory.Inventory;

/** Unsided adapter for inventories directly owned by the caller. */
public final class InventoryWrapper extends StorageItemTransactor {
    public InventoryWrapper(Inventory inventory) {
        super(InventoryStorage.of(inventory, null));
    }
}
