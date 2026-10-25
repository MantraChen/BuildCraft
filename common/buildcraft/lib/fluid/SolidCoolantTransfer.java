package buildcraft.lib.fluid;

import buildcraft.api.fuels.ICoolantManager;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;

/** One ice/packed-ice item melts only when its complete coolant volume fits. */
public final class SolidCoolantTransfer {
    private SolidCoolantTransfer() {}
    public static boolean exchange(ICoolantManager manager, Storage<FluidVariant> tank,
            ContainerItemContext context, TransactionContext transaction) {
        var item = context.getItemVariant();
        if (item.isBlank()) return false;
        var coolant = manager.getSolidCoolant(item.toStack(1));
        if (coolant == null) return false;
        var fluid = coolant.getFluidFromSolidCoolant(item.toStack(1));
        if (fluid == null || fluid.isEmpty()) return false;
        try (Transaction tx = transaction.openNested()) {
            if (tank.insert(fluid.getFluidVariant(), fluid.amount, tx) == fluid.amount && context.extract(item, 1, tx) == 1) {
                tx.commit(); return true;
            }
        }
        return false;
    }
}
