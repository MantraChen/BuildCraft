/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.mixin;

import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.explosion.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExplosionBehavior.class)
public abstract class FacadeExplosionMixin {
    @Inject(method="getBlastResistance",at=@At("RETURN"),cancellable=true)
    private void buildcraft$facadeResistance(Explosion explosion,BlockView world,BlockPos pos,BlockState state,FluidState fluid,CallbackInfoReturnable<Optional<Float>> callback){
        if(world.getBlockEntity(pos) instanceof buildcraft.transport.tile.TilePipeHolder tile){
            float resistance=callback.getReturnValue().orElse(0f);
            for(var face:Direction.values())if(tile.getPluggable(face) instanceof buildcraft.silicon.plug.PluggableFacade facade)
                resistance=Math.max(resistance,facade.active().state.getBlock().getBlastResistance());
            callback.setReturnValue(Optional.of(resistance));
        }
    }
}
