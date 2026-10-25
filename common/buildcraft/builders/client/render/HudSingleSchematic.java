/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Remake by FelixMantra */
package buildcraft.builders.client.render;

import java.util.List;
import buildcraft.builders.item.ItemSchematicSingle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Native HUD for the reusable single-block sample's material requirements. */
public final class HudSingleSchematic {
    private static ItemStack previous=ItemStack.EMPTY;
    private static List<ItemStack> materials=List.of();
    public static void render(DrawContext context,float delta){
        var client=MinecraftClient.getInstance();if(client.player==null||client.options.hudHidden)return;
        var stack=client.player.getMainHandStack();if(!ItemSchematicSingle.isUsed(stack))stack=client.player.getOffHandStack();
        if(!ItemSchematicSingle.isUsed(stack)){previous=ItemStack.EMPTY;materials=List.of();return;}
        if(!ItemStack.canCombine(stack,previous)){
            previous=stack.copy();var schematic=ItemSchematicSingle.getSchematicSafe(stack);
            materials=schematic==null?List.of():schematic.computeRequiredItems().stream().limit(8).toList();
        }
        int y=client.getWindow().getScaledHeight()-64;
        context.drawTextWithShadow(client.textRenderer,Text.translatable("gui.buildcraftbuilders.materials"),8,y-12,0xffffff);
        for(int i=0;i<materials.size();i++){context.drawItem(materials.get(i),8+20*i,y);context.drawItemInSlot(client.textRenderer,materials.get(i),8+20*i,y);}
    }
}
