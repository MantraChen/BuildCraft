/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport;

import net.minecraft.block.entity.BlockEntityType;

import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.tile.TilePipeHolder;

/** Common pipe block and entity type, assigned before client initialization. */
public class BCTransportBlocks {

    // STUB(R.Chen): filteredBuffer dropped here — BlockFilteredBuffer is not yet migrated. Restore the field
    // (typed BlockFilteredBuffer) once that block lands; nothing in the migrated leaf set references it.
    public static BlockPipeHolder pipeHolder;

    // BlockPipeHolder owns the native ticker for this registered entity type.
    public static BlockEntityType<TilePipeHolder> pipeHolderTile;

    public static void preInit() {
        var id = new net.minecraft.util.Identifier("buildcrafttransport", "pipe_holder");
        pipeHolder = net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.BLOCK, id,
            new BlockPipeHolder(net.minecraft.block.AbstractBlock.Settings.create().strength(0.25F, 3F).nonOpaque(), ""));
        pipeHolderTile = buildcraft.lib.registry.BlockEntityRegistry.register(id, TilePipeHolder.class,
            TilePipeHolder::new, pipeHolder);
        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlockEntity((tile, face) -> {
            var pipe = tile.getPipe();
            var plug = tile.getPluggable(face);
            if (tile.isRemoved() || plug != null && plug.isBlocking()) return null;
            return pipe != null && pipe.flow instanceof buildcraft.transport.pipe.flow.PipeFlowItems flow ? flow.getStorage(face) : null;
        }, pipeHolderTile);
        net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.registerForBlockEntity((tile, face) -> {
            var pipe = tile.getPipe(); var plug = tile.getPluggable(face);
            if (tile.isRemoved() || plug != null && plug.isBlocking()) return null;
            return pipe != null && pipe.flow instanceof buildcraft.transport.pipe.flow.PipeFlowFluids flow ? flow.getStorage(face) : null;
        }, pipeHolderTile);
    }
}
