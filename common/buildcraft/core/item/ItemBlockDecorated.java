/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.item;

import java.util.HashMap;

import net.minecraft.client.util.ModelIdentifier;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import buildcraft.api.enums.EnumDecoratedBlock;

import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.item.ItemBlockBCMulti;

public class ItemBlockDecorated extends ItemBlockBCMulti {

    public ItemBlockDecorated(BlockBCBase_Neptune block) {
        super(block, stack -> getVariant(stack).asString());
    }

    public static EnumDecoratedBlock getVariant(net.minecraft.item.ItemStack stack) {
        var tag = stack.getSubNbt("BlockStateTag");
        if (tag != null) {
            String variant = tag.getString(buildcraft.core.block.BlockDecoration.DECORATED_TYPE.getName());
            for (var type : EnumDecoratedBlock.VALUES) if (type.asString().equals(variant)) return type;
        }
        return EnumDecoratedBlock.fromMeta(stack.getDamage());
    }

    // @Override -- removed: method does not exist in Fabric 1.20.1
    @Environment(EnvType.CLIENT)
    public void addModelVariants(HashMap<Integer, ModelIdentifier> variants) {
        for (EnumDecoratedBlock type : EnumDecoratedBlock.VALUES) {
            addVariant(variants, type.ordinal(), type.asString());
        }
    }
}
