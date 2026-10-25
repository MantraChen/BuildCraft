package buildcraft.builders.container;

import java.io.IOException;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.entity.player.PlayerEntity;


import net.fabricmc.api.EnvType;

import buildcraft.api.filler.IFillerPattern;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune.NetSide;
import buildcraft.lib.statement.FullStatement;

import buildcraft.builders.addon.AddonFillerPlanner;
import buildcraft.builders.filler.FillerType;
import buildcraft.core.BCCoreProxy;
import buildcraft.core.marker.volume.EnumAddonSlot;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.core.marker.volume.WorldSavedDataVolumeBoxes;

public class ContainerFillerPlanner extends ContainerBC_Neptune implements IContainerFilling {
    public final AddonFillerPlanner addon;
    private final FullStatement<IFillerPattern> patternStatementClient = new FullStatement<>(
        FillerType.INSTANCE,
        4,
        (statement, paramIndex) -> onStatementChange()
    );

    public ContainerFillerPlanner(PlayerEntity player){this(player,0,selected(player));}
    public ContainerFillerPlanner(PlayerEntity player,int syncId){this(player,syncId,selected(player));}
    public ContainerFillerPlanner(PlayerEntity player,int syncId,AddonFillerPlanner addon){
        super(buildcraft.builders.BCBuildersScreens.PLANNER,player,syncId);this.addon=addon;
        patternStatementClient.set(addon.patternStatement.get(),addon.patternStatement.getParameters());init();
    }
    private static AddonFillerPlanner selected(PlayerEntity player){
        var selected=EnumAddonSlot.getSelectingVolumeBoxAndSlot(player,player.getWorld().isClient
            ?buildcraft.core.marker.volume.ClientVolumeBoxes.INSTANCE.volumeBoxes:WorldSavedDataVolumeBoxes.get(player.getWorld()).volumeBoxes);
        if(selected.getLeft()==null||!(selected.getLeft().addons.get(selected.getRight()) instanceof AddonFillerPlanner addon))throw new IllegalArgumentException("No selected filler planner");
        return addon;
    }
    @Override public boolean isLocked(){return !addon.volumeBox.addons.containsValue(addon)||addon.volumeBox.isAddonLocked(addon.getSlot());}
    @Override
    public PlayerEntity getPlayer() {
        return player;
    }

    @Override
    public FullStatement<IFillerPattern> getPatternStatementClient() {
        return patternStatementClient;
    }

    @Override
    public FullStatement<IFillerPattern> getPatternStatement() {
        return addon.patternStatement;
    }

    @Override
    public boolean isInverted() {
        return addon.inverted;
    }

    @Override
    public void setInverted(boolean value) {
        addon.inverted = value;
    }

    @Override
    public void valuesChanged() {
        addon.updateBuildingInfo();
        if (!player.getWorld().isClient) {
            WorldSavedDataVolumeBoxes.get(getPlayer().getWorld()).markDirty();
        }
    }

    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, Object ctx) throws IOException {
        if(!isClient&&!canUse(player)){buffer.skipBytes(buffer.readableBytes());return;}
        super.readMessage(id, buffer, isClient, ctx);
        IContainerFilling.super.readMessage(id, buffer, isClient, ctx);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        VolumeBox volume = addon.volumeBox;
        var volumes=player.getWorld().isClient?buildcraft.core.marker.volume.ClientVolumeBoxes.INSTANCE.volumeBoxes:WorldSavedDataVolumeBoxes.get(player.getWorld()).volumeBoxes;
        if(volumes.stream().noneMatch(candidate->candidate==volume))return false;
        if (player.getWorld() != volume.world || player.isSpectator() || !volume.addons.containsValue(addon)
            || volume.isEditing() && !volume.isEditingBy(player)) return false;
        var bounds = addon.getBoundingBox();
        var center = bounds.getCenter();
        var pos = net.minecraft.util.math.BlockPos.ofFloored(center);
        return player.squaredDistanceTo(center) <= 64 && player.getWorld().isChunkLoaded(pos)
            && player.getWorld().canPlayerModifyAt(player, pos);
    }
}
