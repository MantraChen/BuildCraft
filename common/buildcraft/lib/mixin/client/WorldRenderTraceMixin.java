package buildcraft.lib.mixin.client;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRenderTraceMixin {
    @Inject(method = "scheduleBlockRenders(IIIIII)V", at = @At("HEAD"))
    private void buildcraft_traceRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2, CallbackInfo callback) {
        buildcraft.lib.misc.DebuggingTools.traceRenderUpdate(x1, y1, z1, x2, y2, z2);
    }
}
