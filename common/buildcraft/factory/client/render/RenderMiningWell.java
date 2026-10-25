/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.factory.client.render;

import buildcraft.factory.tile.TileMiningWell;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;

public final class RenderMiningWell extends RenderMiner<TileMiningWell> {
    private static final String TEXTURE = "buildcraftfactory:blocks/mining_well/tube";
    public RenderMiningWell(BlockEntityRendererFactory.Context context) { super(TEXTURE, false); }
    public static void init() { SpriteHolderRegistry.getHolder(TEXTURE); }
    public static void textureStitchPost() { LaserRenderer_BC8.clearModels(); }
}
