/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import java.util.function.Consumer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.fabricmc.api.EnvType;

import buildcraft.lib.BCLib;
import buildcraft.lib.registry.RegistryConfig;
import buildcraft.lib.registry.TagManager;
import buildcraft.lib.registry.TagManager.EnumTagType;
import buildcraft.lib.registry.TagManager.TagEntry;

import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.RulesLoader;
import buildcraft.core.BCCore;

//@formatter:off
@Mod(
    modid = BCBuilders.MODID,
    name = "BuildCraft Builders",
    version = BCLib.VERSION,
    dependencies = "required-after:buildcraftcore@[" + BCLib.VERSION + "]"
)
//@formatter:on
public class BCBuilders {
    public static final String MODID = "buildcraftbuilders";

    @Mod.Instance(MODID)
    public static BCBuilders INSTANCE = null;

    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent evt) {
        RegistryConfig.useOtherModConfigFor(MODID, BCCore.MODID);

        BCBuildersConfig.preInit();
        BCBuildersRegistries.preInit();
        BCBuildersItems.fmlPreInit();
        BCBuildersBlocks.fmlPreInit();
        BCBuildersStatements.preInit();
        BCBuildersSchematics.preInit();

        NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE, BCBuildersProxy.getProxy());

        BCBuildersProxy.getProxy().fmlPreInit();

        // STUB(R.Chen): MinecraftForge.EVENT_BUS.register(BCBuildersEventDist.INSTANCE);
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {
        BCBuildersProxy.getProxy().fmlInit();
        BCBuildersRegistries.init();
        BCBuildersRecipes.init();
        BCBuildersBlocks.fmlInit();
    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {
        BCBuildersProxy.getProxy().fmlPostInit();
        RulesLoader.loadAll();
    }

    @Mod.EventHandler
    public static void onServerStarting(FMLServerStartingEvent event) {
        GlobalSavedDataSnapshots.reInit(buildcraft.lib.tile.TileBC_Neptune.NetSide.SERVER);
    }

    static { BCBuildersTags.register(); }

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
