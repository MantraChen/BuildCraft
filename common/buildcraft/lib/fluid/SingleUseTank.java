/* Copyright (c) 2016 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.fluid;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import buildcraft.lib.compat.FluidRegistryBC;
import buildcraft.lib.compat.FluidStackBC;

/** The first successful insertion locks the variant, including after the tank empties. */
public class SingleUseTank extends Tank {
    public SingleUseTank(String name, int capacity, BlockEntity tile) {
        super(name, capacity, tile);
        storage.setLockFirst(true);
    }
    public void reset() { storage.setAcceptedVariant(FluidVariant.blank()); }
    public void setAcceptedFluid(Fluid fluid) {
        storage.setAcceptedVariant(fluid == null ? FluidVariant.blank() : FluidVariant.of(fluid));
    }
    public void setAcceptedFluid(FluidStackBC fluid) {
        storage.setAcceptedVariant(fluid == null ? FluidVariant.blank() : fluid.getFluidVariant());
    }
    public FluidStackBC getAcceptedFluid() {
        return storage.getAcceptedVariant().isBlank() ? null : FluidStackBC.ofMilliBuckets(storage.getAcceptedVariant(), 1);
    }
    @Override protected void readTankFromNBT(NbtCompound nbt) {
        if (nbt.contains("storageVersion") || !nbt.contains("acceptedFluid")) return;
        if (nbt.contains("acceptedFluid", NbtElement.STRING_TYPE)) {
            setAcceptedFluid(FluidRegistryBC.getFluid(nbt.getString("acceptedFluid")));
        } else {
            setAcceptedFluid(FluidStackBC.fromNbt(nbt.getCompound("acceptedFluid")));
        }
    }
}
