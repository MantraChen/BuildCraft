/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.mixin.client;

import buildcraft.transport.block.BlockPipeHolder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method="interactBlockInternal", at=@At(value="INVOKE", target="Lnet/minecraft/client/network/ClientPlayerEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"), cancellable=true)
    private void buildcraft_useSchematicFirst(net.minecraft.client.network.ClientPlayerEntity user,
            net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hit,
            CallbackInfoReturnable<net.minecraft.util.ActionResult> result) {
        var action=buildcraft.builders.item.ItemSchematicSingle.useFirst(user,client.world,hand,hit);
        if(action!=net.minecraft.util.ActionResult.PASS)result.setReturnValue(action);
    }

    // The enclosing sequenced action still reaches the server. Keep the existing block entity
    // until its authoritative attachment update arrives: predicting AIR loses the pipe and can
    // discard initialization packets while vanilla waits for the action acknowledgement.
    @Inject(method = "breakBlock", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/block/Block;onBreak(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/entity/player/PlayerEntity;)V"), cancellable = true)
    private void buildcraft_keepPipeWhileBreakingAttachment(BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        if (client.world.getBlockState(pos).getBlock() instanceof BlockPipeHolder block) {
            var trace = block.rayTrace(client.world, pos, client.player);
            if (trace != null && trace.subHit > 6 && trace.subHit < 400) result.setReturnValue(false);
        }
    }
}
