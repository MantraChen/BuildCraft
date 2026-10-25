/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.engine;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import buildcraft.api.fuels.*;
import buildcraft.lib.compat.FluidStackBC;
import buildcraft.lib.fluid.FluidTankStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.nbt.*;

/** Fuel work and residue fractions survive ticks, reloads, redstone pauses and variant changes. */
public final class CombustionEngineLogic {
    private static final long BUCKET = FluidConstants.BUCKET;
    private static final BigInteger DENOMINATOR = BigInteger.valueOf(BUCKET);
    private final IFuelManager fuels;
    private final ICoolantManager coolants;
    private FluidVariant active = FluidVariant.blank();
    // Each consumed droplet buys totalBurningTime units; one full work tick spends BUCKET units.
    private long workCredit, powerRemainder;
    private final Map<FluidVariant, Long> residueFractions = new HashMap<>();
    private int penaltyCooling;
    private boolean lastPowered;

    public CombustionEngineLogic(IFuelManager fuels, ICoolantManager coolants) {
        this.fuels = java.util.Objects.requireNonNull(fuels);
        this.coolants = java.util.Objects.requireNonNull(coolants);
    }
    public int getPenaltyCooling() { return penaltyCooling; }
    public boolean hasFuel(FluidTankStorage fuel) {
        return workCredit > 0 || (!fuel.isResourceBlank() && fuels.getFuel(FluidStackBC.of(fuel.getResource(), 1)) != null);
    }

    public double cool(double heat, boolean powered, FluidTankStorage coolant) {
        if (!Double.isFinite(heat)) heat = 20;
        if (lastPowered && !powered) penaltyCooling = 10;
        lastPowered = powered;
        boolean coolingDown = penaltyCooling > 0 || !powered;
        if (coolingDown) heat = Math.max(20, heat - .05);
        double target = coolingDown ? 20 : 100;
        if (heat > target && !coolant.isResourceBlank()) {
            float perMb = coolants.getDegreesPerMb(FluidStackBC.of(coolant.getResource(), coolant.getAmount()), (float) heat);
            if (Float.isFinite(perMb) && perMb > 0) {
                long needed = (long) Math.ceil((heat - target) * 81 / perMb);
                long request = Math.min(40 * 81, Math.min(needed, coolant.getAmount()));
                try (Transaction tx = Transaction.openOuter()) {
                    long used = coolant.extract(coolant.getResource(), request, tx);
                    heat = Math.max(target, heat - used * perMb / 81.0);
                    tx.commit();
                }
            }
        }
        if (heat <= 20 && penaltyCooling > 0) penaltyCooling--;
        return Math.max(20, heat);
    }

    /** Returns micro-MJ generated this tick. A blocked residue output aborts fuel consumption. */
    public long burn(FluidTankStorage input, FluidTankStorage residue, boolean powered) {
        if (!powered || penaltyCooling > 0) return 0;
        if (workCredit == 0) active = input.getResource();
        IFuel recipe = active.isBlank() ? null : fuels.getFuel(FluidStackBC.of(active, 1));
        if (recipe == null || recipe.getTotalBurningTime() <= 0 || recipe.getPowerPerCycle() <= 0) return 0;
        int duration = recipe.getTotalBurningTime();
        if (workCredit < BUCKET && input.getResource().equals(active)) {
            long consume = Math.min(input.getAmount(), (BUCKET - workCredit + duration - 1) / duration);
            if (consume > 0) {
                FluidVariant residueVariant = FluidVariant.blank();
                long residueAmount = 0, residueFraction = 0;
                if (recipe instanceof IFuelManager.IDirtyFuel dirty) {
                    FluidStackBC output = dirty.getResidue();
                    if (output == null || output.isEmpty()) return 0;
                    residueVariant = output.getFluidVariant();
                    BigInteger total = BigInteger.valueOf(consume).multiply(BigInteger.valueOf(output.amount))
                        .add(BigInteger.valueOf(residueFractions.getOrDefault(residueVariant, 0L)));
                    BigInteger[] divided = total.divideAndRemainder(DENOMINATOR);
                    if (divided[0].compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return 0;
                    residueAmount = divided[0].longValueExact(); residueFraction = divided[1].longValueExact();
                }
                try (Transaction tx = Transaction.openOuter()) {
                    if (residueAmount > 0 && residue.insert(residueVariant, residueAmount, tx) != residueAmount) return 0;
                    if (input.extract(active, consume, tx) != consume) return 0;
                    tx.commit();
                }
                workCredit += consume * duration;
                if (!residueVariant.isBlank()) {
                    if (residueFraction == 0) residueFractions.remove(residueVariant);
                    else residueFractions.put(residueVariant, residueFraction);
                }
            }
        }
        long work = Math.min(workCredit, BUCKET);
        workCredit -= work;
        BigInteger[] output = BigInteger.valueOf(recipe.getPowerPerCycle()).multiply(BigInteger.valueOf(work))
            .add(BigInteger.valueOf(powerRemainder)).divideAndRemainder(DENOMINATOR);
        powerRemainder = output[1].longValueExact();
        return output[0].longValueExact();
    }

    public NbtCompound writeNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.put("active", active.toNbt()); nbt.putLong("workCredit", workCredit);
        nbt.putLong("powerRemainder", powerRemainder); nbt.putInt("penaltyCooling", penaltyCooling);
        nbt.putBoolean("lastPowered", lastPowered);
        NbtList fractions = new NbtList();
        residueFractions.forEach((variant, amount) -> {
            NbtCompound tag = new NbtCompound(); tag.put("variant", variant.toNbt()); tag.putLong("amount", amount); fractions.add(tag);
        });
        nbt.put("residueFractions", fractions);
        return nbt;
    }
    public void readNbt(NbtCompound nbt) {
        active = FluidVariant.fromNbt(nbt.getCompound("active"));
        workCredit = active.isBlank() ? 0 : Math.max(0, Math.min(Integer.MAX_VALUE, nbt.getLong("workCredit")));
        powerRemainder = Math.max(0, Math.min(BUCKET - 1, nbt.getLong("powerRemainder")));
        penaltyCooling = Math.max(0, Math.min(10, nbt.getInt("penaltyCooling")));
        lastPowered = nbt.getBoolean("lastPowered"); residueFractions.clear();
        for (NbtElement entry : nbt.getList("residueFractions", NbtElement.COMPOUND_TYPE)) {
            NbtCompound tag = (NbtCompound) entry;
            FluidVariant variant = FluidVariant.fromNbt(tag.getCompound("variant"));
            long amount = tag.getLong("amount");
            if (!variant.isBlank() && amount > 0 && amount < BUCKET) residueFractions.put(variant, amount);
        }
    }
}
