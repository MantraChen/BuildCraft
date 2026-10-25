package buildcraft.test.lib.world;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import buildcraft.lib.fluid.FluidTankStorage;
import buildcraft.lib.fluid.FluidTransfers;

public class TankInteractionGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void survivalStackedBucketsUseInventoryOverflowWithoutDuplication(TestContext test) {
        var player = FakePlayer.get(test.getWorld(), new GameProfile(UUID.fromString("5bd87dbe-d4cd-4135-a03c-a74177a19941"), "TankSurvival"));
        player.changeGameMode(GameMode.SURVIVAL); player.getInventory().clear();
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET, 2));
        var tank = new FluidTankStorage(FluidConstants.BUCKET, () -> {});
        try (Transaction tx = Transaction.openOuter()) {
            tank.insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, tx); tx.commit();
        }
        test.assertTrue(FluidTransfers.interact(tank, ContainerItemContext.forPlayerInteraction(player, Hand.MAIN_HAND), player), "Bucket exchange must succeed");
        test.assertTrue(player.getMainHandStack().isOf(Items.BUCKET) && player.getMainHandStack().getCount() == 1, "Only one empty bucket is consumed");
        test.assertTrue(player.getInventory().count(Items.WATER_BUCKET) == 1 && tank.getAmount() == 0, "Filled bucket goes to inventory and fluid is removed once");
        player.getInventory().clear(); test.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeContainerIsPreservedAndReturnItemIsNotDuplicated(TestContext test) {
        var player = FakePlayer.get(test.getWorld(), new GameProfile(UUID.fromString("5bd87dbe-d4cd-4135-a03c-a74177a19942"), "TankCreative"));
        player.changeGameMode(GameMode.CREATIVE); player.getInventory().clear();
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        var tank = new FluidTankStorage(2 * FluidConstants.BUCKET, () -> {});
        for (int i = 0; i < 2; i++) test.assertTrue(FluidTransfers.interact(tank,
            ContainerItemContext.forPlayerInteraction(player, Hand.MAIN_HAND), player), "Creative bucket must fill tank");
        test.assertTrue(player.getMainHandStack().isOf(Items.WATER_BUCKET), "Creative source container must remain");
        test.assertTrue(player.getInventory().count(Items.BUCKET) == 1, "Vanilla creative context adds only one returned container");
        test.assertTrue(tank.getAmount() == 2 * FluidConstants.BUCKET, "Tank stores exactly two buckets");
        player.getInventory().clear(); test.complete();
    }
}
