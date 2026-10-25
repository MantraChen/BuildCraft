/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.client;
import buildcraft.silicon.item.ItemPluggableFacade;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.item.ItemStack;
/** Native item tint lookup for the currently displayed facade phase. */
public final class FacadeItemColours implements ItemColorProvider {
    public static final FacadeItemColours INSTANCE=new FacadeItemColours();
    private FacadeItemColours(){}
    @Override public int getColor(ItemStack stack,int tint){
        return MinecraftClient.getInstance().getBlockColors().getColor(ItemPluggableFacade.getStates(stack).getCurrentStateForStack().stateInfo.state,null,null,tint);
    }
}