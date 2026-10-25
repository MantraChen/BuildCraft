/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.mixin;
import net.minecraft.world.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(CollisionView.class)
public interface QuarryCollisionMixin {
    @Inject(method="getBlockCollisions",at=@At("RETURN"),cancellable=true)
    private void buildcraft_addQuarryCollisions(Entity entity,Box query,CallbackInfoReturnable<Iterable<VoxelShape>> result) {
        if((Object)this instanceof World world) {
            var shapes=buildcraft.builders.BCBuildersEventDist.INSTANCE.collisions(world,query);
            if(!shapes.isEmpty())result.setReturnValue(com.google.common.collect.Iterables.concat(result.getReturnValue(),shapes));
        }
    }
}
