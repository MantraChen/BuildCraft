package buildcraft.lib.mixin.client;

import java.util.List;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import buildcraft.lib.debug.ClientDebuggables;

@Mixin(DebugHud.class)
public abstract class DebugHudMixin {
    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
    private void buildcraft_left(CallbackInfoReturnable<List<String>> result) {
        result.setReturnValue(ClientDebuggables.appendDebugText(result.getReturnValue(), false));
    }
    @Inject(method = "getRightText", at = @At("RETURN"), cancellable = true)
    private void buildcraft_right(CallbackInfoReturnable<List<String>> result) {
        result.setReturnValue(ClientDebuggables.appendDebugText(result.getReturnValue(), true));
    }
}
