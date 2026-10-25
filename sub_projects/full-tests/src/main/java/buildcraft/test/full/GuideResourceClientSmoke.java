/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.nio.file.*;
import com.google.gson.*;
import buildcraft.api.mj.MjAPI;
import buildcraft.lib.client.guide.*;
import buildcraft.lib.client.guide.loader.XmlPageLoader;
import buildcraft.lib.client.guide.parts.*;
import buildcraft.lib.client.guide.parts.recipe.*;
import buildcraft.lib.recipe.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.texture.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.profiler.DummyProfiler;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

public final class GuideResourceClientSmoke implements ClientModInitializer {
    private static final Identifier RECIPE=new Identifier("buildcraft_full_tests:guide_recipe");
    private static final Identifier WHITE=new Identifier("buildcraftlib:blocks/white");
    private int stage,ticks,generation,whitePixel;private long deadline;private String save;private CompletableFuture<Void> work;private GuidePageBase before;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("buildcraft.guideResourceClientSmoke"))ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void tick(MinecraftClient c){
        if(stage==0){
            if(c.getOverlay()!=null||!(c.currentScreen instanceof TitleScreen))return;
            c.options.pauseOnLostFocus=false;c.options.getViewDistance().setValue(6);c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            atlas(c);whitePixel=pixel(c);deadline=System.nanoTime()+360_000_000_000L;save="guide-resources-"+System.currentTimeMillis();stage=1;
            var rules=new GameRules();rules.get(GameRules.DO_MOB_SPAWNING).set(false,null);
            c.createIntegratedServerLoader().createAndStart(save,new LevelInfo("BuildCraft Guide — Remake by FelixMantra",GameMode.CREATIVE,false,Difficulty.PEACEFUL,true,rules,DataConfiguration.SAFE_MODE),new GeneratorOptions(4754L,false,false),r->r.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());return;
        }
        if(stage==60)return;if(System.nanoTime()>deadline)throw new IllegalStateException("Guide resource client timeout stage "+stage);
        if(stage==17){if(c.getServer()!=null||c.world!=null)return;stage=18;ticks=0;c.createIntegratedServerLoader().start(new TitleScreen(),save);return;}
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen)throw new IllegalStateException("Guide client disconnected stage "+stage);
        if(c.world==null||c.player==null||c.getServer()==null||c.getOverlay()!=null)return;ticks++;if(ticks%20==0)c.getToastManager().clear();
        if(stage==1){work=c.getServer().submit(()->{var p=player(c);p.getInventory().clear();p.getInventory().setStack(0,new ItemStack(buildcraft.lib.BCLibItems.guide));p.currentScreenHandler.sendContentUpdates();c.getServer().getOverworld().setTimeOfDay(6000);});advance();}
        else if(stage==2&&ready()&&ticks>25){c.interactionManager.interactItem(c.player,Hand.MAIN_HAND);advance();}
        else if(stage==3&&ticks>15){
            require(c.currentScreen instanceof GuiGuide,"Native guide item did not open reader");
            require(xml("minecraft:iron_ingot","crafting") instanceof GuideCraftingFactory,"Explicit crafting type ignored");
            require(xml("minecraft:iron_ingot","smelting") instanceof GuideSmeltingFactory,"Explicit smelting type ignored");
            require(xml("minecraft:iron_ingot","assembling")==null,"Explicit empty type fell back to an unrelated recipe");
            require(xml("buildcraftcore:gear_stone",null) instanceof GuideCraftingFactory,"Untyped recipe failed");
            var chip=new ItemStack(Registries.ITEM.get(new Identifier("buildcraftsilicon:redstone_chipset")));chip.getOrCreateNbt().putString("chipset","red");
            var recipes=GuideAssemblyRecipes.INSTANCE.getRecipes(chip);require(recipes.size()==1,"Native chipset recipe missing or wrong NBT variants included");
            var f=recipes.get(0);require(cost(f)==10000*MjAPI.MJ&&inputs(f).length==1&&inputs(f)[0].get().baseStack.isOf(Items.REDSTONE),"Native chipset count/cost wrong");open(c,chip);advance();
        }else if(stage==4&&ticks>20){require(part(c)!=null,"Native assembly page empty");shot(c,"guide-native-chipset.png");generation=GuideManager.INSTANCE.getGeneration();reloadData(c,3,321);advance();}
        else if(stage==5&&ready()&&ticks>35){
            require(GuideManager.INSTANCE.getGeneration()>generation&&c.getNetworkHandler().getRecipeManager().get(RECIPE).isPresent(),"Server recipe sync did not refresh guide");
            var recipes=GuideAssemblyRecipes.INSTANCE.getRecipes(result());require(recipes.size()==2&&!recipes.get(0).equals(recipes.get(1)),"Distinct counted/cost recipes collapsed");
            for(var f:GuideAssemblyRecipes.INSTANCE.getUsages(new ItemStack(Items.DIAMOND)))require(!output(f).isOf(Items.TOTEM_OF_UNDYING),"Exact NBT usage accepted plain diamond");
            require(GuideAssemblyRecipes.INSTANCE.getUsages(gem()).stream().filter(f->output(f).isOf(Items.TOTEM_OF_UNDYING)).count()==2,"Exact NBT usage missing");
            open(c,result());advance();
        }else if(stage==6&&ticks>20){
            var p=part(c);require(inputs(p).length==12&&inputs(p)[0].get().baseStack.getCount()==3&&ItemStack.canCombine(inputs(p)[0].get().baseStack,gem())&&inputs(p)[11].get().baseStack.getCount()==12&&cost(p)==321*MjAPI.MJ,"Twelve ingredient page lost counts/NBT/cost");
            shot(c,"guide-twelve-counted-inputs.png");before=guide(c).getCurrentPage();generation=GuideManager.INSTANCE.getGeneration();reloadData(c,4,654);advance();
        }else if(stage==7&&ready()&&ticks>35){
            require(GuideManager.INSTANCE.getGeneration()>generation&&guide(c).getCurrentPage()!=before,"Open guide retained old recipe page");
            require(inputs(part(c))[0].get().baseStack.getCount()==4&&cost(part(c))==654*MjAPI.MJ,"Server reload left stale material/energy data");shot(c,"guide-live-datapack-update.png");clickLastInput(c);advance();
        }else if(stage==8&&ticks>20){
            require(guide(c).getCurrentPage().getTitle().equals(new ItemStack(Items.STRING).getName().getString()),"Twelfth ingredient click mapped to the wrong slot");shot(c,"guide-last-input-navigation.png");
            resourcePack(c);c.getResourcePackManager().scanPacks();require(c.getResourcePackManager().enable("file/guide-texture-fixture"),"Fixture resource pack missing");work=c.reloadResources();advance();
        }else if(stage==9&&ready()&&ticks>10){atlas(c);require(pixel(c)==0xff00ff00,"Native atlas ignored resource pack override");open(c,result());shot(c,"guide-resource-pack-reload.png");c.getResourcePackManager().disable("file/guide-texture-fixture");work=c.reloadResources();advance();}
        else if(stage==10&&ready()&&ticks>10){atlas(c);require(pixel(c)==whitePixel,"Removing resource pack did not restore original pixels");require(cost(part(c))==654*MjAPI.MJ,"Resource reload lost synchronized recipe");advance();}
        else if(stage==11){
            // Removing one native recipe must also invalidate an already open page.
            generation=GuideManager.INSTANCE.getGeneration();work=c.getServer().submit(()->{
                try{Files.delete(c.getServer().getSavePath(WorldSavePath.DATAPACKS).resolve("guide-fixture/data/buildcraft_full_tests/recipes/guide_recipe.json"));}catch(Exception e){throw new RuntimeException(e);}
                return c.getServer().reloadResources(c.getServer().getDataPackManager().getEnabledNames());
            }).thenCompose(f->f);advance();
        }else if(stage==12&&ready()&&ticks>35){
            require(GuideManager.INSTANCE.getGeneration()>generation&&GuideAssemblyRecipes.INSTANCE.getRecipes(result()).size()==1&&cost(part(c))==444*MjAPI.MJ,"Removed recipe still shown in open guide");shot(c,"guide-removed-recipe-refresh.png");advance();
        }else if(stage==13){c.player.closeHandledScreen();advance();}
        else if(stage==14&&ticks>10){advance();}
        else if(stage==15){advance();}
        else if(stage==16){stage=17;ticks=0;c.world.disconnect();c.disconnect(new TitleScreen());}
        else if(stage==18&&ticks>35){require(GuideAssemblyRecipes.INSTANCE.getRecipes(result()).size()==1,"Restart lost enabled data pack or guide recipe");c.interactionManager.interactItem(c.player,Hand.MAIN_HAND);advance();}
        else if(stage==19&&ticks>15){require(c.currentScreen instanceof GuiGuide,"Restarted native guide item failed");open(c,result());advance();}
        else if(stage==20&&ticks>15){require(cost(part(c))==444*MjAPI.MJ&&inputs(part(c)).length==12,"Restarted guide data wrong");atlas(c);shot(c,"guide-restarted-native-recipes.png");System.out.println("BUILDCRAFT GUIDE RESOURCE CLIENT: native guide item; typed/untyped recipe selection; synchronized assembly with exact NBT/counts/MJ; twelve-slot rendering and item navigation; live server data-pack replacement/removal; resource pack override/removal and mipmap level 4; original fluid playback; saved-world restart; failures=0");stage=60;c.scheduleStop();}
    }
    private static void atlas(MinecraftClient c){
        var atlas=c.getBakedModelManager().getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);require(((Integer)field(atlas,"mipLevel"))==4,"Block atlas mipmaps were reduced below level 4");
        for(var id:List.of(WHITE,new Identifier("buildcraftfactory:blocks/plain_pipe/end"),new Identifier("buildcraftfactory:blocks/tube/end"))){var s=atlas.getSprite(id).getContents();require(!s.getId().equals(MissingSprite.getMissingSpriteId())&&s.getWidth()%16==0&&s.getHeight()%16==0,"Small legacy sprite reduced mipmaps");}
        for(String name:List.of("oil_heat_0_still","fuel_still","redplasma_still")){var s=atlas.getSprite(new Identifier("buildcraftenergy:blocks/fluids/"+name)).getContents();require(s.getDistinctFrameCount().count()==20,"Fluid animation changed frame set");}
    }
    private static int pixel(MinecraftClient c){var s=c.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(WHITE).getContents();return ((NativeImage)field(s,"image")).getColor(0,0);}
    private static void resourcePack(MinecraftClient c){try{
        var root=c.runDirectory.toPath().resolve("resourcepacks/guide-texture-fixture");Files.createDirectories(root);Files.writeString(root.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"Guide sprite override test\"}}");
        var image=root.resolve("assets/buildcraftlib/textures/blocks/white.png");Files.createDirectories(image.getParent());try(var pixels=new NativeImage(8,8,false)){pixels.fillRect(0,0,8,8,0xff00ff00);pixels.writeTo(image);}
    }catch(Exception e){throw new RuntimeException(e);}}
    private void reloadData(MinecraftClient c,int count,long mj){
        work=c.getServer().submit(()->{try{
            var root=c.getServer().getSavePath(WorldSavePath.DATAPACKS).resolve("guide-fixture");Files.createDirectories(root);Files.writeString(root.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":15,\"description\":\"Native guide reload tests\"}}");
            var dir=root.resolve("data/buildcraft_full_tests/recipes");Files.createDirectories(dir);Files.writeString(dir.resolve("guide_recipe.json"),recipe(count,mj).toString());Files.writeString(dir.resolve("guide_recipe_alternative.json"),recipe(5,444).toString());
            var packs=c.getServer().getDataPackManager();packs.scanPacks();var names=new ArrayList<>(packs.getEnabledNames());if(!names.contains("file/guide-fixture"))names.add("file/guide-fixture");return c.getServer().reloadResources(names);
        }catch(Exception e){throw new RuntimeException(e);}}).thenCompose(f->f);
    }
    private static JsonObject recipe(int first,long cost){var json=new JsonObject();json.addProperty("type","buildcraftsilicon:assembly");json.addProperty("mj",cost);var inputs=new JsonArray();Item[] items={Items.DIAMOND,Items.REDSTONE,Items.IRON_INGOT,Items.GOLD_INGOT,Items.LAPIS_LAZULI,Items.QUARTZ,Items.STONE,Items.COBBLESTONE,Items.GLASS,Items.COAL,Items.PAPER,Items.STRING};
        for(int i=0;i<items.length;i++){var entry=new JsonObject();var ingredient=new JsonObject();ingredient.addProperty("item",Registries.ITEM.getId(items[i]).toString());entry.add("ingredient",ingredient);entry.addProperty("count",i==0?first:i+1);if(i==0)entry.addProperty("nbt",gem().getNbt().toString());inputs.add(entry);}json.add("ingredients",inputs);
        var output=new JsonObject();output.addProperty("item","minecraft:totem_of_undying");output.addProperty("nbt",result().getNbt().toString());json.add("result",output);return json;}
    private static ItemStack gem(){var s=new ItemStack(Items.DIAMOND);s.setCustomName(Text.literal("Felix ingredient"));return s;}
    private static ItemStack result(){var s=new ItemStack(Items.TOTEM_OF_UNDYING);s.setCustomName(Text.literal("Felix research"));return s;}
    private static GuidePartFactory xml(String item,String type){try{var tag=XmlPageLoader.parseTag("<recipe stack=\""+item+"\""+(type==null?"":" type=\""+type+"\"")+"/>");var list=XmlPageLoader.TAG_FACTORIES.get("recipe").parse(tag,DummyProfiler.INSTANCE);return list==null||list.isEmpty()?null:list.get(0);}catch(Exception e){throw new RuntimeException(e);}}
    private static Object field(Object target,String name){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new RuntimeException(e);}}
    private static ChangingItemStack[] inputs(Object p){return (ChangingItemStack[])field(p,"input");}
    private static long cost(Object p){return ((ChangingObject<Long>)field(p,"mjCost")).get();}
    private static ItemStack output(Object p){return ((ChangingItemStack)field(p,"output")).get().baseStack;}
    private static GuiGuide guide(MinecraftClient c){return (GuiGuide)c.currentScreen;}
    private static GuideAssembly part(MinecraftClient c){return ((GuidePage)guide(c).getCurrentPage()).parts.stream().filter(GuideAssembly.class::isInstance).map(GuideAssembly.class::cast).findFirst().orElseThrow();}
    private static void open(MinecraftClient c,ItemStack stack){var gui=guide(c);gui.openPage(GuideManager.INSTANCE.getPageFor(stack).createNew(gui));}
    private static void clickLastInput(MinecraftClient c){
        var gui=guide(c);var page=(GuidePage)gui.getCurrentPage();var position=new GuidePart.PagePosition(0,0);for(var p:page.parts){
            if(p instanceof GuideAssembly){if(position.pixel+60>GuiGuide.PAGE_HEIGHT)position=position.newPage();while(page.getPage()+1<position.page)page.nextPage();
                double x=gui.minX+(position.page%2==0?23:197)+(GuiGuide.PAGE_WIDTH-134)/2+1+3*18+8,y=gui.minY+25+position.pixel+1+2*18+8;float scale=(Float)field(gui,"bookScale");gui.mouseClicked(x*scale,y*scale,0);return;}
            position=p.renderIntoArea(0,0,GuiGuide.PAGE_WIDTH,GuiGuide.PAGE_HEIGHT,position,-1);
        }throw new IllegalStateException("Missing assembly input target");
    }
    private static net.minecraft.server.network.ServerPlayerEntity player(MinecraftClient c){return c.getServer().getPlayerManager().getPlayer(c.player.getUuid());}
    private static void shot(MinecraftClient c,String name){net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory,name,c.getFramebuffer(),m->{});}
    private boolean ready(){if(work==null||!work.isDone()||ticks<5)return false;work.join();return true;}
    private void advance(){stage++;ticks=0;System.out.println("BUILDCRAFT GUIDE RESOURCE CLIENT stage="+stage);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
