/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.mixin;

import buildcraft.builders.item.ItemSchematicSingle;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve the original 16 blank / one recorded schematic stack limits. */
@Mixin(ItemStack.class)
public abstract class SchematicItemStackMixin {
    @Inject(method="getMaxCount",at=@At("HEAD"),cancellable=true)
    private void buildcraft_recordedSchematicLimit(CallbackInfoReturnable<Integer> result){
        if(ItemSchematicSingle.isUsed((ItemStack)(Object)this))result.setReturnValue(1);
    }
}
