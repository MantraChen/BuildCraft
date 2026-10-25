/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.container;

import java.io.IOException;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.PropertyDelegate;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.silicon.tile.TileAssemblyTable;

public final class ContainerAssemblyTable extends ContainerBCTile<TileAssemblyTable> {
    private static final IdAllocator IDS=buildcraft.lib.gui.ContainerBC_Neptune.IDS.makeChild("assembly");
    public static final int NET_SELECT=IDS.allocId("SELECT");
    private int progress;
    public ContainerAssemblyTable(PlayerEntity player,int syncId,TileAssemblyTable tile) {
        super(buildcraft.silicon.BCSiliconScreens.ASSEMBLY,player,syncId,tile);
        for(int row=0;row<4;row++)for(int col=0;col<3;col++)addSlot(new SlotBase(tile.inv,col+row*3,8+col*18,36+row*18));
        addFullPlayerInventory(123);
        addProperties(new PropertyDelegate() {
            public int size(){return 1;}
            public int get(int index){return player.getWorld().isClient?progress:scaled();}
            public void set(int index,int value){progress=Math.max(0,Math.min(10000,value));}
        });
    }
    private int scaled(){long target=tile.getTarget();return target<=0?0:(int)Math.min(10000,tile.power*10000/target);}
    public int getProgress(){return player.getWorld().isClient?progress:scaled();}
    @Override public IdAllocator getIdAllocator(){return IDS;}
    @Override public void readMessage(int id,PacketBufferBC buffer,boolean client,Object context) throws IOException {
        if(client)return;
        if(!canUse(player))return;
        if(id!=NET_SELECT){super.readMessage(id,buffer,false,context);return;}
        var recipe=buffer.readIdentifier();boolean selected=buffer.readBoolean();
        if(buffer.isReadable())throw new IOException("Unexpected assembly selection payload");
        tile.selectRecipe(recipe,selected);sendContentUpdates();
    }
}
