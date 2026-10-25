/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

public enum BCBuildersGuis {
    ARCHITECT,
    BUILDER,
    FILLER,
    LIBRARY,
    REPLACER,
    FILLER_PLANNER;

    public void openGUI(PlayerEntity player) {
        if(this==FILLER_PLANNER&&!player.getWorld().isClient){
            var selection=buildcraft.core.marker.volume.EnumAddonSlot.getSelectingVolumeBoxAndSlot(player,
                buildcraft.core.marker.volume.WorldSavedDataVolumeBoxes.get(player.getWorld()).volumeBoxes);
            if(selection.getLeft()!=null&&selection.getLeft().addons.get(selection.getRight()) instanceof buildcraft.builders.addon.AddonFillerPlanner addon)
                BCBuildersScreens.openPlanner(player,addon);
        }
    }

    public void openGUI(PlayerEntity player, BlockPos pos) {
        if(this==LIBRARY){BCBuildersScreens.openLibrary(player,pos);return;}
        if(this==REPLACER){BCBuildersScreens.openReplacer(player,pos);return;}
        if (this == ARCHITECT) { BCBuildersScreens.openArchitect(player, pos); return; }
        if (this == BUILDER) {
            BCBuildersScreens.openBuilder(player, pos);
            return;
        }
        if (this == FILLER) {
            BCBuildersScreens.openFiller(player, pos);
            return;
        }
        // STUB(R.Chen): Forge player.openGui → Fabric ScreenHandler, deferred — Phase 10
    }
}
