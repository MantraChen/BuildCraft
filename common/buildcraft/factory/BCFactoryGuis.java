/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.factory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

public enum BCFactoryGuis {
    AUTO_WORKBENCH_ITEMS,
    AUTO_WORKBENCH_FLUIDS,
    CHUTE,
    TANK,
    DISTILLER;

    public void openGUI(PlayerEntity player) {
        // STUB(R.Chen): Forge player.openGui → Fabric ScreenHandler, deferred — Phase 10
    }

    public void openGUI(PlayerEntity player, BlockPos pos) {
        if (this == AUTO_WORKBENCH_ITEMS && player.getWorld().getBlockEntity(pos) instanceof buildcraft.factory.tile.TileAutoWorkbenchItems tile) {
            BCFactoryScreens.openAutoWorkbench(player, tile);
            return;
        }
        if (this == CHUTE && player.getWorld().getBlockEntity(pos) instanceof buildcraft.factory.tile.TileChute chute) {
            BCFactoryScreens.openChute(player, chute);
            return;
        }
        if (this == TANK && player.getWorld().getBlockEntity(pos) instanceof buildcraft.factory.tile.TileTank tank) {
            BCFactoryScreens.openTank(player, tank);
            return;
        }
        // STUB(R.Chen): Forge player.openGui → Fabric ScreenHandler, deferred — Phase 10
    }
}
