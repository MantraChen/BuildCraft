/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.lib.client.guide.parts;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import buildcraft.api.core.BCLog;
import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.sprite.SpriteAtlas;
import buildcraft.lib.client.sprite.SpriteRaw;

public class GuideImageFactory implements GuidePartFactory {
    private final Identifier location;
    private final int width, height;
    public GuideImageFactory(String location) { this(location, -1, -1); }
    public GuideImageFactory(String location, int width, int height) {
        this.location = new Identifier(location);
        this.width = width;
        this.height = height;
    }
    @Override public GuideImage createNew(GuiGuide gui) {
        var client = MinecraftClient.getInstance();
        var resource = client.getResourceManager().getResource(location);
        if (resource.isPresent()) {
            try (var input = resource.get().getInputStream(); var image = NativeImage.read(input)) {
                return new GuideImage(gui, new SpriteRaw(location, 0, 0, 1, 1), image.getWidth(), image.getHeight(), width, height);
            } catch (java.io.IOException error) {
                BCLog.logger.warn("[lib.guide] Cannot read image {}", location, error);
            }
        }
        var sprite = client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(location);
        return new GuideImage(gui, new SpriteAtlas(sprite), sprite.getContents().getWidth(), sprite.getContents().getHeight(), width, height);
    }
}
