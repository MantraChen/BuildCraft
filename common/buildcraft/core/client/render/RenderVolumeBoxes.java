/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.core.client.render;
import net.minecraft.entity.player.PlayerEntity;
import buildcraft.lib.client.render.DetachedRenderer;
import buildcraft.lib.client.render.laser.LaserBoxRenderer;
import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.core.marker.volume.*;
public enum RenderVolumeBoxes implements DetachedRenderer.IDetachedRenderer {
    INSTANCE;
    @SuppressWarnings("unchecked")
    public void render(PlayerEntity player,float delta){
        var context=DetachedRenderer.context();
        for(var volume:ClientVolumeBoxes.INSTANCE.volumeBoxes){
            if(volume.world!=player.getWorld()||context.frustum()!=null&&!context.frustum().isVisible(volume.box.getBoundingBox().expand(.2)))continue;
            var type=volume.isEditingBy(player)?BuildCraftLaserManager.MARKER_VOLUME_SIGNAL:volume.getLockTargetsStream()
                .filter(Lock.Target.TargetUsedByMachine.class::isInstance).map(Lock.Target.TargetUsedByMachine.class::cast)
                .map(target->target.type.getLaserType()).findFirst().orElse(BuildCraftLaserManager.MARKER_VOLUME_CONNECTED);
            LaserBoxRenderer.renderLaserBoxStatic(volume.box,type,false);
            for(var addon:volume.addons.values())((IFastAddonRenderer<Addon>)addon.getRenderer()).renderAddonFast(addon,player,delta,null);
        }
    }
    @Override public void render(float delta){var player=net.minecraft.client.MinecraftClient.getInstance().player;if(player!=null)render(player,delta);}
}
