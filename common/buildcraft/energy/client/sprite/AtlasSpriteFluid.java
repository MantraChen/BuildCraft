/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 */
package buildcraft.energy.client.sprite;

import java.io.IOException;
import java.io.InputStream;
import com.mojang.serialization.Codec;
import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.client.texture.atlas.AtlasSourceType;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import buildcraft.api.core.BCLog;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.lib.fluid.BCFluid;
import buildcraft.lib.mixin.client.AtlasSourceManagerAccessor;

/** Adds recolored, animated heat templates to the native atlas during every resource reload. */
public final class AtlasSpriteFluid implements AtlasSource {
    public static final Codec<AtlasSpriteFluid> CODEC = Codec.unit(AtlasSpriteFluid::new);
    private static AtlasSourceType type;

    public static void register() {
        if (type == null) type = AtlasSourceManagerAccessor.buildcraft_register("buildcraftenergy:fluids", CODEC);
    }

    @Override public AtlasSourceType getType() { return java.util.Objects.requireNonNull(type); }

    @Override public void load(ResourceManager resources, SpriteRegions regions) {
        for (BCFluid fluid : BCEnergyFluids.allFluids) {
            add(resources, regions, fluid, fluid.getStillTexture(), "still");
            add(resources, regions, fluid, fluid.getFlowTexture(), "flow");
        }
    }

    private static void add(ResourceManager resources, SpriteRegions regions, BCFluid fluid, Identifier id, String kind) {
        var override = resources.getResource(RESOURCE_FINDER.toResourcePath(id));
        if (override.isPresent()) {
            regions.add(id, override.get());
            return;
        }
        Identifier template = new Identifier("buildcraftenergy", "blocks/fluids/heat_" + fluid.getHeatValue() + "_" + kind);
        Resource resource = resources.getResource(RESOURCE_FINDER.toResourcePath(template)).orElse(null);
        if (resource == null) {
            BCLog.logger.warn("Missing fluid texture template {}", template);
            return;
        }
        regions.add(id, () -> {
            try (InputStream input = resource.getInputStream()) {
                AnimationResourceMetadata animation = resource.getMetadata().decode(AnimationResourceMetadata.READER)
                    .orElse(AnimationResourceMetadata.EMPTY);
                return loadRecolored(id, input, animation, fluid.getLightColour(), fluid.getDarkColour());
            } catch (IOException exception) {
                BCLog.logger.warn("Failed to build fluid sprite " + id, exception);
                return null;
            }
        });
    }

    public static SpriteContents loadRecolored(Identifier id, InputStream input, AnimationResourceMetadata animation,
            int light, int dark) throws IOException {
        NativeImage image = NativeImage.read(input);
        try {
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                image.setColor(x, y, recolorPixel(image.getColor(x, y), light, dark));
            }
            return new SpriteContents(id, animation.getSize(image.getWidth(), image.getHeight()), image, animation);
        } catch (RuntimeException | Error exception) {
            image.close();
            throw exception;
        }
    }

    /** Minecraft's image pixels are ABGR; BuildCraft's configured endpoints are RGB. */
    public static int recolorPixel(int pixel, int light, int dark) {
        int red = interpolate(pixel & 255, (light >> 16) & 255, (dark >> 16) & 255);
        int green = interpolate((pixel >> 8) & 255, (light >> 8) & 255, (dark >> 8) & 255);
        int blue = interpolate((pixel >> 16) & 255, light & 255, dark & 255);
        return (pixel & 0xFF000000) | (blue << 16) | (green << 8) | red;
    }

    private static int interpolate(int value, int light, int dark) {
        return (dark * (255 - value) + light * value) / 255;
    }
}
