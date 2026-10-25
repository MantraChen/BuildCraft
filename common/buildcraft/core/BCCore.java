/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core;

import java.io.File;
import java.util.function.Consumer;
import buildcraft.lib.compat.forge_stubs.OreDictionaryStub;

import net.minecraft.block.Blocks;
import net.minecraft.item.Item;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
// STUB(R.Chen): OreDictionaryStub removed — TODO(R.Chen): implement via Tags

import buildcraft.lib.BCLib;
import buildcraft.lib.BCLibItems;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.lib.registry.CreativeTabManager;
import buildcraft.lib.registry.CreativeTabManager.CreativeTabBC;
import buildcraft.lib.registry.TagManager;
import buildcraft.lib.registry.TagManager.EnumTagType;
import buildcraft.lib.registry.TagManager.TagEntry;

import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.VolumeCache;

//@formatter:off
@Mod(
    modid = BCCore.MODID,
    name = "BuildCraft Core",
    version = BCLib.VERSION,
    updateJSON = "https://mod-buildcraft.com/version/versions.json",
    dependencies = "required-after:buildcraftlib@[" + BCLib.VERSION + "]",
    guiFactory = "buildcraft.core.client.ConfigGuiFactoryBC"
)
//@formatter:on
public class BCCore {
    public static final String MODID = "buildcraftcore";

    @Mod.Instance(MODID)
    public static BCCore INSTANCE = null;

    static {
        BCLibItems.enableGuide();
        BCLibItems.enableDebugger();
    }

    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent event) {
        File cfgFolder = event.getModConfigurationDirectory();
        cfgFolder = new File(cfgFolder, "buildcraft");
        BCCoreConfig.preInit(cfgFolder);

        CreativeTabBC tab = CreativeTabManager.createTab("buildcraft.main");

        BCCoreBlocks.preInit();
        BCCoreItems.preInit();
        BCCoreStatements.preInit();
        BCCoreRecipes.fmlPreInit();

        BCCoreProxy.getProxy().fmlPreInit();

        tab.setItem(BCCoreItems.wrench);

        setItemTab(BCLibItems.guide, tab);
        setItemTab(BCLibItems.guideNote, tab);
        setItemTab(BCLibItems.debugger, tab);

        NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE, BCCoreProxy.getProxy());

        // STUB(R.Chen): OreDictionaryStub.registerOre("craftingTableWood", Blocks.CRAFTING_TABLE);
        // STUB(R.Chen): MinecraftForge.EVENT_BUS.register(BCCoreEventDist.INSTANCE);
        BCCoreConfig.saveConfigs();
    }

    private static void setItemTab(Item item, CreativeTabBC tab) {
        // STUB(R.Chen): Forge Item.setCreativeTab dropped — Fabric uses ItemGroupEvents in the ModInitializer, Phase 10
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent event) {
        BCCoreConfig.saveConfigs();
        // STUB(R.Chen): Forge Item.setCreativeTab dropped — Fabric ItemGroupEvents, Phase 10
        // BCLibItems.guide creative-tab assignment deferred

        BCCoreProxy.getProxy().fmlInit();

        MarkerCache.registerCache(VolumeCache.INSTANCE);
        MarkerCache.registerCache(PathCache.INSTANCE);
    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent event) {
        BCCoreConfig.saveConfigs();
        BCCoreProxy.getProxy().fmlPostInit();
        BCCoreConfig.postInit();
    }

    static { BCCoreTags.register(); }

    private static TagEntry registerTag(String id) {
        return TagManager.registerTag(id);
    }

    private static void startBatch() {
        TagManager.startBatch();
    }

    private static void endBatch(Consumer<TagEntry> consumer) {
        TagManager.endBatch(consumer);
    }
}
