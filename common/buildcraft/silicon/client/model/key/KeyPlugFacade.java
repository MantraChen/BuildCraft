/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.client.model.key;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
public final class KeyPlugFacade {
    public final BlockState state;public final Direction side;public final boolean isHollow;
    public KeyPlugFacade(BlockState state,Direction side,boolean hollow){this.state=state;this.side=side;isHollow=hollow;}
}