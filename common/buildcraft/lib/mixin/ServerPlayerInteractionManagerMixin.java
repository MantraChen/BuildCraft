/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.mixin;

import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow protected ServerWorld world;
    @Shadow @Final protected ServerPlayerEntity player;

    // AFTER is deliberate: Fabric's protection event runs BEFORE this invocation.
    @Inject(method = "tryBreakBlock", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/block/Block;onBreak(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/entity/player/PlayerEntity;)V",
        shift = At.Shift.AFTER), cancellable = true)
    private void buildcraft_breakAttachment(BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        if (buildcraft.transport.block.BlockPipeHolder.breakPart(world,pos,player)) {
            player.networkHandler.sendPacket(new BlockUpdateS2CPacket(world,pos));
            var tile = world.getBlockEntity(pos);
            if (tile != null && tile.toUpdatePacket() != null) player.networkHandler.sendPacket(tile.toUpdatePacket());
            result.setReturnValue(false);
        }
    }
}
