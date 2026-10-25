/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.factory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.registry.Registries;
import buildcraft.lib.client.model.ModelHolderVariable;
import buildcraft.factory.client.model.ModelHeatExchange;

@Environment(EnvType.CLIENT)
public final class BCFactoryModels {
    public static final ModelHolderVariable HEAT_EXCHANGE_STATIC = new ModelHolderVariable(
        "buildcraftfactory:models/tiles/heat_exchange_static.json", ModelHeatExchange.FUNCTION_CONTEXT);
    private static boolean registered;
    public static void fmlPreInit() { /* Construct the resource holder before resource reload. */ }
    public static void fmlInit() {
        if (registered) return;
        registered = true;
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
            if (BCFactoryBlocks.heatExchange != null && context.id() instanceof ModelIdentifier id) {
                var blockId = Registries.BLOCK.getId(BCFactoryBlocks.heatExchange);
                if (id.getNamespace().equals(blockId.getNamespace()) && id.getPath().equals(blockId.getPath()))
                    return new ModelHeatExchange(model);
            }
            return model;
        }));
    }
}
