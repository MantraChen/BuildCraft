package buildcraft.lib.engine;

import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.item.ItemStack;

public final class SolidFuelBurner {
    private SolidFuelBurner() {}
    public static int burnTime(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Integer ticks = FuelRegistry.INSTANCE.get(stack.getItem());
        return ticks == null ? 0 : Math.max(0, ticks);
    }
}
