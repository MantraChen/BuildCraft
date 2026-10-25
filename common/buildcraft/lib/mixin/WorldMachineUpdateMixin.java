/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.mixin;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(World.class)
public abstract class WorldMachineUpdateMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",at=@At("RETURN"))
    private void buildcraft_notifyQuarries(BlockPos pos,BlockState state,int flags,int depth,CallbackInfoReturnable<Boolean> result) {
        if(result.getReturnValueZ()) {
            var world=(World)(Object)this;
            buildcraft.builders.BCBuildersEventDist.INSTANCE.blockChanged(world,pos);
            buildcraft.factory.MiningWellUpdates.changed(world,pos);
        }
    }
}
