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

    @Inject(method="interactBlock", at=@At(value="INVOKE", target="Lnet/minecraft/server/network/ServerPlayerEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"), cancellable=true)
    private void buildcraft_useSchematicFirst(ServerPlayerEntity user, net.minecraft.world.World level,
            net.minecraft.item.ItemStack stack, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hit,
            CallbackInfoReturnable<net.minecraft.util.ActionResult> result) {
        var action=buildcraft.builders.item.ItemSchematicSingle.useFirst(user,level,hand,hit);
        if(action==net.minecraft.util.ActionResult.PASS)action=buildcraft.core.item.ItemMapLocation.useFirst(user,level,hand,hit);
        if(action!=net.minecraft.util.ActionResult.PASS)result.setReturnValue(action);
    }

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
