/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.plug;

import java.util.*;
import buildcraft.api.facades.IFacadeState;
import buildcraft.lib.world.SingleBlockAccess;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Property;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;

public final class FacadeBlockStateInfo implements IFacadeState {
    public final BlockState state;
    public final Set<Property<?>> varyingProperties;
    public final boolean isVisible,isTransparent;
    private final ItemStack requiredStack;
    private final boolean[] solid=new boolean[6];
    public FacadeBlockStateInfo(BlockState state,ItemStack required,Set<Property<?>> varying){
        this.state=Objects.requireNonNull(state);requiredStack=required.copy();varyingProperties=Set.copyOf(varying);
        isVisible=!required.isEmpty();isTransparent=!state.isOpaque();
        var view=new SingleBlockAccess(state);
        for(var face:Direction.values())solid[face.ordinal()]=state.isSideSolidFullSquare(view,SingleBlockAccess.POS,face);
    }
    public boolean isSideSolid(Direction side){return solid[side.ordinal()];}
    public FacadePhasedState createPhased(DyeColor colour){return new FacadePhasedState(this,colour);}
    @Override public boolean isTransparent(){return isTransparent;}
    @Override public BlockState getBlockState(){return state;}
    @Override public ItemStack getRequiredStack(){return requiredStack.copy();}
}