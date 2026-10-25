/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.plug;

import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.*;
import net.minecraft.util.math.*;

/** The original six-facing control plate, including stale-reference checks. */
public abstract class PluggableSidedPlate extends PipePluggable {
    protected PluggableSidedPlate(PluggableDefinition definition,IPipeHolder holder,Direction side){super(definition,holder,side);}
    @Override public final Box getBoundingBox() {
        return switch(side) {
            case DOWN->new Box(5/16.0,2/16.0,5/16.0,11/16.0,4/16.0,11/16.0);
            case UP->new Box(5/16.0,12/16.0,5/16.0,11/16.0,14/16.0,11/16.0);
            case NORTH->new Box(5/16.0,5/16.0,2/16.0,11/16.0,11/16.0,4/16.0);
            case SOUTH->new Box(5/16.0,5/16.0,12/16.0,11/16.0,11/16.0,14/16.0);
            case WEST->new Box(2/16.0,5/16.0,5/16.0,4/16.0,11/16.0,11/16.0);
            case EAST->new Box(12/16.0,5/16.0,5/16.0,14/16.0,11/16.0,11/16.0);
        };
    }
    @Override public final boolean isBlocking(){return true;}
    public final boolean isAttached() {
        var world=holder.getPipeWorld();var tile=holder.getPipeTile();
        return world!=null && tile!=null && !tile.isRemoved() && world.isChunkLoaded(holder.getPipePos())
            && world.getBlockEntity(holder.getPipePos())==tile && holder.getPluggable(side)==this;
    }
    protected final boolean liveServer(){return isAttached() && !holder.getPipeWorld().isClient;}
}
