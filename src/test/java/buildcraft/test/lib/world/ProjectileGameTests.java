package buildcraft.test.lib.world;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import buildcraft.lib.inventory.TransactorEntityArrow;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.projectile.*;
import net.minecraft.item.*;
import net.minecraft.potion.*;
import net.minecraft.test.*;
import net.minecraft.text.Text;

public class ProjectileGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tippedArrowsKeepPotionDataAcrossSimulationAndExtraction(TestContext test) {
        ItemStack expected = PotionUtil.setPotion(new ItemStack(Items.TIPPED_ARROW), Potions.LONG_POISON);
        ArrowEntity arrow = new ArrowEntity(test.getWorld(), 0, 2, 0);
        arrow.initFromStack(expected); arrow.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
        var transactor = new TransactorEntityArrow(arrow);
        ItemStack simulated = transactor.extract(stack -> true, 1, 1, true);
        test.assertTrue(ItemStack.canCombine(expected, simulated) && !arrow.isRemoved(), "Simulation must preserve the native tipped-arrow data and entity");
        simulated.setCount(50);
        ItemStack extracted = transactor.extract(stack -> true, 1, 1, false);
        test.assertTrue(ItemStack.canCombine(expected, extracted) && extracted.getCount() == 1 && arrow.isRemoved(), "Extraction must return one real pickup stack and remove the projectile");
        test.assertTrue(transactor.extract(stack -> true, 1, 1, false).isEmpty(), "A removed projectile cannot be extracted again");
        test.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rejectedProjectileExtractionLeavesEntityUntouched(TestContext test) {
        SpectralArrowEntity arrow = new SpectralArrowEntity(test.getWorld(), 0, 2, 0);
        var transactor = new TransactorEntityArrow(arrow);
        for (var permission : new PersistentProjectileEntity.PickupPermission[] {
                PersistentProjectileEntity.PickupPermission.DISALLOWED, PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY}) {
            arrow.pickupType = permission;
            test.assertTrue(transactor.extract(stack -> true, 1, 1, false).isEmpty(), "Restricted pickup must not create an item");
        }
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
        test.assertTrue(transactor.extract(stack -> false, 1, 1, false).isEmpty(), "Rejected filter must not discard the arrow");
        test.assertTrue(transactor.extract(stack -> true, 2, 2, false).isEmpty(), "One arrow cannot satisfy a two-item minimum");
        test.assertTrue(transactor.extract(stack -> true, 0, 0, false).isEmpty() && !arrow.isRemoved(), "Zero maximum must not remove the entity");
        test.assertTrue(transactor.extract(stack -> stack.isOf(Items.SPECTRAL_ARROW), 1, 1, false).isOf(Items.SPECTRAL_ARROW), "Native spectral pickup must retain its type");
        test.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nativeProjectileOverridesPreserveTridentDamageAndNbt(TestContext test) {
        var owner = FakePlayer.get(test.getWorld(), new GameProfile(UUID.randomUUID(), "ProjectileTest"));
        ItemStack expected = new ItemStack(Items.TRIDENT);
        expected.setDamage(27); expected.setCustomName(Text.literal("Named trident"));
        expected.addEnchantment(net.minecraft.enchantment.Enchantments.UNBREAKING, 3);
        TridentEntity trident = new TridentEntity(test.getWorld(), owner, expected.copy());
        trident.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
        ItemStack result = new TransactorEntityArrow(trident).extract(stack -> true, 1, 1, false);
        test.assertTrue(ItemStack.canCombine(expected, result) && result.getDamage() == 27, "Virtual pickup method must preserve subclass item state");
        test.complete();
    }
}
