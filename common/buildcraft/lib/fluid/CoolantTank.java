package buildcraft.lib.fluid;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

public final class CoolantTank extends Tank {
    public CoolantTank(String name, int capacity, BlockEntity tile) {
        super(name, capacity, tile, fluid -> BuildcraftFuelRegistry.coolant.getCoolant(fluid) != null);
    }
    public boolean interactHand(PlayerEntity player, Hand hand) {
        if (BuildcraftFuelRegistry.coolant.getSolidCoolant(player.getStackInHand(hand)) == null) return false;
        var context = ContainerItemContext.forPlayerInteraction(player, hand);
        if (player.getWorld().isClient) return BuildcraftFuelRegistry.coolant.getSolidCoolant(player.getStackInHand(hand)) != null;
        return interact(context, player);
    }
    @Override protected boolean interact(ContainerItemContext context, PlayerEntity player) {
        if (player.getWorld().isClient) return false;
        try (Transaction tx = Transaction.openOuter()) {
            if (SolidCoolantTransfer.exchange(BuildcraftFuelRegistry.coolant, this, context, tx)) {
                tx.commit(); return true;
            }
        }
        return super.interact(context, player);
    }
}
