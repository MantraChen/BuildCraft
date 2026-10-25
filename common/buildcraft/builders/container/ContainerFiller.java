/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.builders.container;

import java.io.IOException;
import net.minecraft.entity.player.PlayerEntity;
import buildcraft.api.filler.IFillerPattern;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.statement.FullStatement;
import buildcraft.builders.BCBuildersScreens;
import buildcraft.builders.filler.FillerType;
import buildcraft.builders.tile.TileFiller;
import buildcraft.core.marker.volume.WorldSavedDataVolumeBoxes;

public class ContainerFiller extends ContainerBCTile<TileFiller> implements IContainerFilling {
    private final FullStatement<IFillerPattern> clientPattern = new FullStatement<>(FillerType.INSTANCE, 4,
        (statement, parameter) -> onStatementChange());
    public ContainerFiller(PlayerEntity player, int syncId, TileFiller tile) {
        super(BCBuildersScreens.FILLER, player, syncId, tile);
        addFullPlayerInventory(153);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new SlotBase(tile.invResources, col + row * 9, 8 + col * 18, 84 + row * 18));
        init();
    }
    @Override public PlayerEntity getPlayer() { return player; }
    @Override public FullStatement<IFillerPattern> getPatternStatementClient() { return clientPattern; }
    @Override public FullStatement<IFillerPattern> getPatternStatement() {
        return tile.addon != null ? tile.addon.patternStatement : tile.patternStatement;
    }
    @Override public boolean isInverted() { return tile.addon != null ? tile.addon.inverted : tile.inverted; }
    @Override public boolean isLocked() { return tile.isLocked(); }
    @Override public void setInverted(boolean value) {
        if (tile.addon != null) tile.addon.inverted = value;
        else tile.inverted = value;
    }
    @Override public void valuesChanged() {
        if (player.getWorld().isClient) return;
        if (tile.addon != null) {
            tile.addon.updateBuildingInfo();
            WorldSavedDataVolumeBoxes.get(player.getWorld()).markDirty();
        }
        tile.onStatementChange();
    }
    @Override public void readMessage(int id, PacketBufferBC buffer, boolean isClient, Object context) throws IOException {
        if (!isClient && !canUse(player)) return;
        super.readMessage(id, buffer, isClient, context);
        IContainerFilling.super.readMessage(id, buffer, isClient, context);
    }
}
