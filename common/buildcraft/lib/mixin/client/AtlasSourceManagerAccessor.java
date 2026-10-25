package buildcraft.lib.mixin.client;

import com.mojang.serialization.Codec;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.client.texture.atlas.AtlasSourceManager;
import net.minecraft.client.texture.atlas.AtlasSourceType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla has no public registration API for atlas source codecs in 1.20.1. */
@Mixin(AtlasSourceManager.class)
public interface AtlasSourceManagerAccessor {
    @Invoker("register")
    static AtlasSourceType buildcraft_register(String name, Codec<? extends AtlasSource> codec) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
