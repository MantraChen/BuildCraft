/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.gui;

import java.util.*;
import net.fabricmc.api.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.*;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.silicon.container.ContainerAdvancedCraftingTable;

/** Native laser crafting, protected template and searchable unlocked recipes. Remake by FelixMantra. */
@Environment(EnvType.CLIENT)
public class GuiAdvancedCraftingTable extends GuiBC8<ContainerAdvancedCraftingTable> {
    private static final Identifier TEXTURE=new Identifier("buildcraftsilicon:textures/gui/advanced_crafting_table.png");
    private boolean bookOpen;
    private int page;
    private int refreshTicks;
    private String query="";
    private TextFieldWidget search;
    private List<CraftingRecipe> recipes=List.of();
    private final buildcraft.lib.gui.ledger.LedgerOwnership ownership;

    public GuiAdvancedCraftingTable(ContainerAdvancedCraftingTable container,PlayerInventory inventory,Text title) {
        super(container,inventory,title);xSize=176;ySize=241;
        ownership=(buildcraft.lib.gui.ledger.LedgerOwnership)shownElements.stream()
            .filter(buildcraft.lib.gui.ledger.LedgerOwnership.class::isInstance).findFirst().orElseThrow();
    }
    @Override protected boolean shouldAddHelpLedger(){return false;}
    @Override protected void init() {
        super.init();
        shownElements.remove(ownership);
        if(!bookOpen)shownElements.add(ownership);
        if(bookOpen)x=(width-318)/2;
        addDrawableChild(ButtonWidget.builder(Text.literal("R"),button->{bookOpen=!bookOpen;clearAndInit();})
            .dimensions(x+5,y+17,20,18).tooltip(Tooltip.of(Text.translatable("gui.recipebook.toggleRecipes"))).build());
        if(bookOpen) {
            search=new TextFieldWidget(textRenderer,x+185,y+7,122,18,Text.translatable("gui.recipebook.search_hint"));
            search.setMaxLength(80);search.setText(query);search.setChangedListener(value->{query=value;page=0;filterRecipes();});
            addDrawableChild(search);
            addDrawableChild(ButtonWidget.builder(Text.literal("<"),button->{if(page>0)page--;})
                .dimensions(x+185,y+219,30,16).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(">"),button->{if((page+1)*20<recipes.size())page++;})
                .dimensions(x+277,y+219,30,16).build());
            filterRecipes();
        } else search=null;
    }
    private void filterRecipes() {
        String needle=query.toLowerCase(Locale.ROOT);
        recipes=client.world.getRecipeManager().listAllOfType(RecipeType.CRAFTING).stream()
            .filter(recipe->recipe.fits(3,3) && !recipe.isIgnoredInRecipeBook() && !recipe.getIngredients().isEmpty()
                && (client.player.isCreative() || client.player.getRecipeBook().contains(recipe)))
            .filter(recipe->recipe.getOutput(client.world.getRegistryManager()).getName().getString().toLowerCase(Locale.ROOT).contains(needle)
                || recipe.getId().toString().contains(needle))
            .sorted(Comparator.comparing(recipe->recipe.getId().toString())).toList();
        page=Math.min(page,Math.max(0,(recipes.size()-1)/20));
    }
    @Override protected void handledScreenTick() {
        super.handledScreenTick();
        if(search!=null)search.tick();
        if(bookOpen && ++refreshTicks>=20){refreshTicks=0;filterRecipes();}
    }
    private int recipeAt(double mouseX,double mouseY) {
        int dx=(int)mouseX-(x+194),dy=(int)mouseY-(y+35);
        if(dx<0 || dy<0 || dx>=104 || dy>=130)return -1;
        int index=page*20+(dy/26)*4+dx/26;return index<recipes.size()?index:-1;
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        if(bookOpen && button==0) {
            int index=recipeAt(mouseX,mouseY);
            if(index>=0) {
                Identifier id=recipes.get(index).getId();
                container.sendMessage(ContainerAdvancedCraftingTable.NET_SELECT_RECIPE,buffer->buffer.writeIdentifier(id));
                return true;
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(search!=null && search.isFocused() && key!=256) {
            search.keyPressed(key,scan,modifiers);return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override protected boolean isClickOutsideBounds(double mouseX,double mouseY,int left,int top,int button) {
        if(bookOpen && mouseX>=x+180 && mouseX<x+312 && mouseY>=y && mouseY<y+241)return false;
        return super.isClickOutsideBounds(mouseX,mouseY,left,top,button);
    }
    @Override public boolean charTyped(char chr,int modifiers) {
        return search!=null && search.isFocused()?search.charTyped(chr,modifiers):super.charTyped(chr,modifiers);
    }
    @Override public void drawElementBackgrounds(DrawContext context,float delta) {
        context.drawTexture(TEXTURE,x,y,0,0,176,241);
        int filled=(int)(70*container.getProgress());
        context.fill(x+164,y+77-filled,x+168,y+77,0xFFFF4040);
        if(bookOpen) {
            context.fill(x+180,y,x+312,y+241,0xFF777777);
            context.fill(x+182,y+2,x+310,y+239,0xFFC6C6C6);
            for(int cell=0;cell<20 && page*20+cell<recipes.size();cell++) {
                int sx=x+194+(cell%4)*26,sy=y+35+(cell/4)*26;
                context.fill(sx,sy,sx+24,sy+24,0xFF999999);
                context.drawItem(recipes.get(page*20+cell).getOutput(client.world.getRegistryManager()),sx+4,sy+4);
            }
        }
        super.drawElementBackgrounds(context,delta);
    }
    @Override public void drawElementForegrounds(DrawContext context,float delta) {
        context.drawText(textRenderer,title,x+(176-textRenderer.getWidth(title))/2,y+6,0x404040,false);
        context.drawText(textRenderer,(container.tile.power/buildcraft.api.mj.MjAPI.MJ)+"/500 MJ",x+94,y+17,0x404040,false);
        String credit="Remake by FelixMantra";
        context.drawText(textRenderer,credit,x+(176-textRenderer.getWidth(credit))/2,y+141,0x404040,false);
        if(bookOpen)context.drawText(textRenderer,(page+1)+"/"+Math.max(1,(recipes.size()+19)/20),x+234,y+223,0x404040,false);
        super.drawElementForegrounds(context,delta);
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        if(bookOpen) {
            int index=recipeAt(mouseX,mouseY);
            if(index>=0)context.drawItemTooltip(textRenderer,recipes.get(index).getOutput(client.world.getRegistryManager()),mouseX,mouseY);
        }
    }
}
