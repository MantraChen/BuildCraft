package buildcraft.lib.inventory;

import buildcraft.lib.tile.item.IItemHandlerAdv;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/** Native screen slots must remove from the real handler, not a placeholder SimpleInventory. */
public final class ItemHandlerInventory implements Inventory {
    private final IItemHandlerAdv handler;
    public ItemHandlerInventory(IItemHandlerAdv handler) { this.handler = handler; }
    @Override public int size() { return handler.getSlots(); }
    @Override public boolean isEmpty() {
        for (int slot = 0; slot < size(); slot++) if (!getStack(slot).isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getStack(int slot) { return handler.getStackInSlot(slot); }
    @Override public ItemStack removeStack(int slot, int amount) { return handler.extractItem(slot, amount, false); }
    @Override public ItemStack removeStack(int slot) { return removeStack(slot, Integer.MAX_VALUE); }
    @Override public void setStack(int slot, ItemStack stack) { handler.setStackInSlot(slot, stack); }
    @Override public void markDirty() {
        // Vanilla can mutate a slot's existing stack in place before this notification.
        for (int slot = 0; slot < size(); slot++) handler.setStackInSlot(slot, handler.getStackInSlot(slot));
    }
    @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
    @Override public boolean isValid(int slot, ItemStack stack) { return handler.canSet(slot, stack); }
    @Override public void clear() { for (int slot = 0; slot < size(); slot++) setStack(slot, ItemStack.EMPTY); }
}
