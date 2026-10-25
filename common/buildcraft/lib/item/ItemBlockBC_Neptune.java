/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.lib.item;

import java.util.List;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import buildcraft.lib.block.BlockBCBase_Neptune;
import buildcraft.lib.misc.LocaleUtil;

public class ItemBlockBC_Neptune extends BlockItem implements IItemBuildCraft {
    public final String id;

    public ItemBlockBC_Neptune(BlockBCBase_Neptune block) {
        super(block, new Settings());
        this.id = "item." + block.id;
        init();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext flags) {
        super.appendTooltip(stack, world, tooltip, flags);
        String tipId = getTranslationKey(stack) + ".tip";
        if (LocaleUtil.canLocalize(tipId)) {
            tooltip.add(Text.translatable(tipId).formatted(Formatting.GRAY));
        } else if (flags.isAdvanced()) {
            tooltip.add(Text.literal(tipId).formatted(Formatting.GRAY));
        }
    }

    // ---- 1.12.2 compat no-ops ----
    protected void setMaxStackSize(int n) {}
    protected void setHasSubtypes(boolean has) {}
    protected void setMaxDamage(int n) {}
    public net.minecraft.util.Identifier getRegistryName() {
        return net.minecraft.registry.Registries.ITEM.getId(this);
    }
}
