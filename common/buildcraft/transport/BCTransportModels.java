/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

// Native pipe model baking. Pluggable models are restored separately.
@Environment(EnvType.CLIENT)
public class BCTransportModels {
    public static void preInit() {} // STUB Phase 5
    public static void init() {
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
            if (model == null || !(context.id() instanceof net.minecraft.client.util.ModelIdentifier id)) return model;
            var identifier = new net.minecraft.util.Identifier(id.getNamespace(), id.getPath());
            if (id.getVariant().equals("inventory") && net.minecraft.registry.Registries.ITEM.get(identifier) instanceof buildcraft.transport.item.ItemPipeHolder item)
                return new buildcraft.transport.client.model.ModelPipe(model, item.getDefinition(), context.textureGetter());
            if (identifier.equals(net.minecraft.registry.Registries.BLOCK.getId(BCTransportBlocks.pipeHolder)))
                return new buildcraft.transport.client.model.ModelPipe(model, null, context.textureGetter());
            return model;
        }));
    }
}
