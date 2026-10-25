package buildcraft.lib.fluid;

import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Settles a bottom-to-top column without crossing a tank containing another variant. */
public final class FluidTankColumn {
    private FluidTankColumn() {}
    public static long settle(List<FluidTankStorage> tanks) {
        long moved = 0;
        try (Transaction tx = Transaction.openOuter()) {
            for (int sourceIndex = 1; sourceIndex < tanks.size(); sourceIndex++) {
                FluidTankStorage source = tanks.get(sourceIndex);
                if (source.isResourceBlank()) continue;
                FluidVariant variant = source.getResource();
                int bottom = sourceIndex;
                while (bottom > 0) {
                    FluidTankStorage below = tanks.get(bottom - 1);
                    if ((!below.isResourceBlank() && !below.getResource().equals(variant)) || !below.accepts(variant)) break;
                    bottom--;
                }
                for (int i = bottom; i < sourceIndex && source.getAmount() > 0; i++) {
                    try (Transaction transfer = tx.openNested()) {
                        long inserted = tanks.get(i).insert(variant, source.getAmount(), transfer);
                        if (source.extract(variant, inserted, transfer) == inserted) {
                            moved += inserted;
                            transfer.commit();
                        }
                    }
                }
            }
            tx.commit();
        }
        return moved;
    }
}
