/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.robotics;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.tile.TileZonePlanner;
import net.fabricmc.fabric.api.screenhandler.v1.*;
import net.minecraft.entity.player.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.*;
import net.minecraft.screen.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
public final class BCRoboticsScreens {
    public static final ScreenHandlerType<ContainerBC_Neptune> ZONE=new ExtendedScreenHandlerType<>((syncId,inv,data)->{
        var pos=data.readBlockPos();if(!(inv.player.getWorld().getBlockEntity(pos) instanceof TileZonePlanner tile))throw new IllegalStateException("Missing zone planner at "+pos);
        return new ContainerZonePlanner(inv.player,syncId,tile);
    });
    public static void register(){Registry.register(Registries.SCREEN_HANDLER,new Identifier("buildcraftrobotics","zone_planner"),ZONE);}
    public static void open(PlayerEntity player,BlockPos pos){
        if(!(player instanceof ServerPlayerEntity)||!player.getWorld().isChunkLoaded(pos)||!(player.getWorld().getBlockEntity(pos) instanceof TileZonePlanner tile)||!tile.canInteractWith(player))return;
        buildcraft.robotics.zone.ZonePlannerMapDataServer.INSTANCE.clear();
        player.openHandledScreen(new ExtendedScreenHandlerFactory(){
            public Text getDisplayName(){return Text.translatable("block.buildcraftrobotics.zone_planner");}
            public void writeScreenOpeningData(ServerPlayerEntity p,PacketByteBuf b){b.writeBlockPos(pos);}
            public ScreenHandler createMenu(int id,PlayerInventory inv,PlayerEntity user){return tile.canInteractWith(user)?new ContainerZonePlanner(user,id,tile):null;}
        });
    }
}
