/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render.tile;

import org.joml.Vector3f;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.texture.Sprite;

import net.minecraftforge.client.model.ModelLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import buildcraft.lib.client.model.MutableVertex;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/** A variable sized element (like LED) that can render somewhere in a TESR. Passing a resolver instance will let you
 * modify the location, colour, lightmap, and size of the single element. This does not allow for different textures. */
@Environment(EnvType.CLIENT)
public class RenderPartCube {
    /** The centre of this element. */
    public final MutableVertex center = new MutableVertex();
    public double sizeX = 1 / 16.0, sizeY = 1 / 16.0, sizeZ = 1 / 16.0;

    /** Constructs a simple cube configured for a LED. */
    public RenderPartCube() {
        this(1 / 16.0, 1 / 16.0, 1 / 16.0);
    }

    public RenderPartCube(double x, double y, double z) {
        center.positiond(x, y, z);
    }

    public void setWhiteTex() {
        Sprite sprite = buildcraft.lib.client.sprite.SpriteHolderRegistry.getHolder("buildcraftlib:blocks/white").getSprite();
        if (sprite == null) return;
        // Reset the vertex so that edits don't spill out to other tiles.
        center.texf(sprite.getFrameU(8), sprite.getFrameV(8));
    }

    public void render(net.minecraft.client.util.math.MatrixStack matrices,
            net.minecraft.client.render.VertexConsumerProvider consumers, int light, int overlay) {
        var vertices = consumers.getBuffer(net.minecraft.client.render.RenderLayer.getEntityCutout(
            new net.minecraft.util.Identifier("buildcraftlib", "textures/blocks/white.png")));
        for (var face : net.minecraft.util.math.Direction.values()) {
            var quad = buildcraft.lib.client.model.ModelUtil.createFace(face,
                new javax.vecmath.Point3f(center.position_x, center.position_y, center.position_z),
                new javax.vecmath.Point3f((float)sizeX/2, (float)sizeY/2, (float)sizeZ/2),
                new buildcraft.lib.client.model.ModelUtil.UvFaceData(0, 0, 1, 1));
            quad.colouri(center.colour_r, center.colour_g, center.colour_b, center.colour_a);
            quad.lighti(center.light_block, center.light_sky);
            quad.render(vertices, matrices.peek(), light, overlay);
        }
    }

    /** Renders an element, without changing the vertex. However this does ignore the "normal" and "texture" components
     * of the vertex. */
    public void render(BufferBuilder bb) {
        Vector3f pos = center.positionvf();
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;

        double rX = sizeX / 2;
        double rY = sizeY / 2;
        double rZ = sizeZ / 2;

        vertex(bb, center, x - rX, y + rY, z + rZ);
        vertex(bb, center, x + rX, y + rY, z + rZ);
        vertex(bb, center, x + rX, y + rY, z - rZ);
        vertex(bb, center, x - rX, y + rY, z - rZ);

        vertex(bb, center, x - rX, y - rY, z - rZ);
        vertex(bb, center, x + rX, y - rY, z - rZ);
        vertex(bb, center, x + rX, y - rY, z + rZ);
        vertex(bb, center, x - rX, y - rY, z + rZ);

        vertex(bb, center, x - rX, y - rY, z + rZ);
        vertex(bb, center, x - rX, y + rY, z + rZ);
        vertex(bb, center, x - rX, y + rY, z - rZ);
        vertex(bb, center, x - rX, y - rY, z - rZ);

        vertex(bb, center, x + rX, y - rY, z - rZ);
        vertex(bb, center, x + rX, y + rY, z - rZ);
        vertex(bb, center, x + rX, y + rY, z + rZ);
        vertex(bb, center, x + rX, y - rY, z + rZ);

        vertex(bb, center, x - rX, y - rY, z - rZ);
        vertex(bb, center, x - rX, y + rY, z - rZ);
        vertex(bb, center, x + rX, y + rY, z - rZ);
        vertex(bb, center, x + rX, y - rY, z - rZ);

        vertex(bb, center, x + rX, y - rY, z + rZ);
        vertex(bb, center, x + rX, y + rY, z + rZ);
        vertex(bb, center, x - rX, y + rY, z + rZ);
        vertex(bb, center, x - rX, y - rY, z + rZ);
    }

    private static void vertex(BufferBuilder vb, MutableVertex center, double x, double y, double z) {
        // Using DefaultVertexFormats.BLOCK
        // -- POSITION_3F // pos
        // -- COLOR_4UB // colour
        // -- TEX_2F // texture
        // -- TEX_2S // lightmap
        vb.vertex(x, y, z);
        center.renderColour(vb);
        center.renderTex(vb);
        center.renderLightMap(vb);
        vb.next();
    }
}
