/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.silicon.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.player.PlayerEntity;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.plug.PluggableGate;

/** Native placement and the original 25 NBT variants. Remake by FelixMantra. */
public class ItemPluggableGate extends Item implements IItemPluggable {

    public ItemPluggableGate(Settings settings) {
        super(settings);
    }

    public static GateVariant getVariant(ItemStack stack) {
        return new GateVariant(stack.getNbt() == null ? new NbtCompound() : stack.getNbt().getCompound("gate"));
    }

    public ItemStack getStack(GateVariant variant) {
        var stack = new ItemStack(this);
        stack.getOrCreateNbt().put("gate", variant.writeToNBT());
        return stack;
    }

    public static java.util.List<GateVariant> variants() {
        var variants = new java.util.ArrayList<GateVariant>();
        for (var material : EnumGateMaterial.VALUES) for (var logic : EnumGateLogic.VALUES) for (var modifier : EnumGateModifier.VALUES) {
            var variant = new GateVariant(logic, material, modifier);
            if (!variants.contains(variant)) variants.add(variant);
        }
        return java.util.List.copyOf(variants);
    }

    @Override public Text getName(ItemStack stack) {
        var variant = getVariant(stack);
        if (!variant.material.canBeModified) return Text.translatable("gate.name.basic");
        return Text.translatable("gate.name", Text.translatable("gate.material." + variant.material.tag), Text.translatable("gate.logic." + variant.logic.tag));
    }

    @Override public void appendTooltip(ItemStack stack, net.minecraft.world.World world, java.util.List<Text> lines, net.minecraft.client.item.TooltipContext context) {
        var variant = getVariant(stack);
        lines.add(Text.translatable("gui.buildcraftsilicon.gate.capacity", variant.numSlots, variant.numTriggerArgs, variant.numActionArgs));
        if (variant.modifier != EnumGateModifier.NO_MODIFIER) lines.add(Text.translatable("gate.modifier." + variant.modifier.tag));
    }

    @Override public PipePluggable onPlace(ItemStack stack, IPipeHolder holder, Direction side, PlayerEntity player, Hand hand) {
        if (holder.getPipe() == null || !holder.canPlayerInteract(player) || holder.getPluggable(side) != null) return null;
        var variant = getVariant(stack);
        var sound = variant.material.block.getDefaultState().getSoundGroup();
        holder.getPipeWorld().playSound(null, holder.getPipePos(), sound.getPlaceSound(), net.minecraft.sound.SoundCategory.BLOCKS,
            (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        return new PluggableGate(BCSiliconPlugs.gate, holder, side, variant);
    }
}
