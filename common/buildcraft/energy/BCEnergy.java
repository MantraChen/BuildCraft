/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy;

import java.util.function.Consumer;

import buildcraft.lib.compat.FluidRegistryBC;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import buildcraft.lib.BCLib;
import buildcraft.lib.registry.MigrationManager;
import buildcraft.lib.registry.RegistryConfig;
import buildcraft.lib.registry.TagManager;
import buildcraft.lib.registry.TagManager.EnumTagType;
import buildcraft.lib.registry.TagManager.TagEntry;

import buildcraft.core.BCCore;

//@formatter:off
@Mod(
    modid = BCEnergy.MODID,
    name = "BuildCraft Energy",
    version = BCLib.VERSION,
    dependencies = "required-after:buildcraftcore@[" + BCLib.VERSION + "]"
)
//@formatter:on
public class BCEnergy {
    public static final String MODID = "buildcraftenergy";

    static {
        FluidRegistryBC.enableUniversalBucket();
    }

    @Mod.Instance(MODID)
    public static BCEnergy INSTANCE;

    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent evt) {
        RegistryConfig.useOtherModConfigFor(MODID, BCCore.MODID);
        BCEnergyConfig.preInit();
        BCEnergyEntities.preInit();
        BCEnergyFluids.fmlPreInit();
        BCEnergyBlocks.preInit();
        BCEnergyItems.preInit();

        NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE, BCEnergyProxy.getProxy());

        BCEnergyProxy.getProxy().fmlPreInit();
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {
        BCEnergyRecipes.init();
        BCEnergyWorldGen.fmlInit();
        BCEnergyProxy.getProxy().fmlInit();
    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {
        BCEnergyProxy.getProxy().fmlPostInit();
        // Biome validation runs on SERVER_STARTING, after native data-pack registry loading.
        registerMigrations();
    }

    private static void registerMigrations() {
        /** 7.99.0 */
        // STUB(R.Chen): fluid block migrations deferred until BCEnergyFluids is ported — Phase 10
        // Fluid registration changed from "fluid_block_[FLUID]" to "fluid_block_heat_[HEAT]_[FLUID]"
    }

    static { BCEnergyTags.register(); }

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
