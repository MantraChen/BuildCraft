/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.core.marker.volume;
import net.fabricmc.api.*;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.player.PlayerEntity;
import buildcraft.core.client.render.VolumeRender;
@Environment(EnvType.CLIENT)
public class AddonDefaultRenderer<T extends Addon> implements IFastAddonRenderer<T>{
    private final Sprite sprite;
    public AddonDefaultRenderer(){sprite=null;}
    public AddonDefaultRenderer(Object sprite){this.sprite=sprite instanceof Sprite nativeSprite?nativeSprite:null;}
    @Override public void renderAddonFast(T addon,PlayerEntity player,float delta,BufferBuilder ignored){
        VolumeRender.cube(addon.getBoundingBox(),sprite==null?VolumeRender.white():sprite,1);
    }
}
