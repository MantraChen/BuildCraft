/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.factory;

import java.util.function.Consumer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import buildcraft.lib.BCLib;
import buildcraft.lib.registry.RegistryConfig;
import buildcraft.lib.registry.TagManager;
import buildcraft.lib.registry.TagManager.EnumTagType;
import buildcraft.lib.registry.TagManager.TagEntry;

import buildcraft.core.BCCore;

//@formatter:off
@Mod(
    modid = BCFactory.MODID,
    name = "BuildCraft Factory",
    version = BCLib.VERSION,
    dependencies = "required-after:buildcraftcore@[" + BCLib.VERSION + "]"
)
//@formatter:on
public class BCFactory {
    public static final String MODID = "buildcraftfactory";

    @Mod.Instance(MODID)
    public static BCFactory INSTANCE = null;

    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent evt) {
        RegistryConfig.useOtherModConfigFor(MODID, BCCore.MODID);

        BCFactoryBlocks.fmlPreInit();
        BCFactoryItems.fmlPreInit();

        NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE, BCFactoryProxy.getProxy());
        // STUB(R.Chen): MinecraftForge.EVENT_BUS.register(BCFactoryEventDist.INSTANCE);

        BCFactoryProxy.getProxy().fmlPreInit();
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {
        BCFactoryProxy.getProxy().fmlInit();
    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {
        BCFactoryProxy.getProxy().fmlPostInit();
    }

    static { BCFactoryTags.register(); }

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
