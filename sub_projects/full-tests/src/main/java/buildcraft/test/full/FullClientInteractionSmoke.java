/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.concurrent.CompletableFuture;
import buildcraft.api.transport.EnumWirePart;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;

/** Exercises actual client prediction, sequenced break packets and server replies in a fresh world. */
public final class FullClientInteractionSmoke implements ClientModInitializer {
    private static final BlockPos POS = new BlockPos(2, -60, 2);
    private static final EnumWirePart WIRE = EnumWirePart.WEST_UP_NORTH;
    private int stage, ticks;
    private long deadline;
    private CompletableFuture<Void> serverWork;
    private TilePipeHolder clientTile;
    private boolean sawFluid;
    private net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant testFluid;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("buildcraft.fullClientInteractionSmoke")) return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        if (stage == 0) {
            if (client.getOverlay() != null || !(client.currentScreen instanceof TitleScreen)) return;
            client.options.pauseOnLostFocus = false;
            deadline = System.nanoTime() + 180_000_000_000L;
            stage = 1;
            var rules = new GameRules();
            rules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
            client.createIntegratedServerLoader().createAndStart("attachment-smoke-" + System.currentTimeMillis(),
                new LevelInfo("BuildCraft attachment prediction smoke", GameMode.CREATIVE, false,
                    Difficulty.PEACEFUL, true, rules, DataConfiguration.SAFE_MODE),
                new GeneratorOptions(4754L, false, false),
                registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder());
            return;
        }
        if (stage == 11) return;
        if (System.nanoTime() > deadline) throw new IllegalStateException("Attachment client smoke timed out at stage " + stage);
        if (client.world == null || client.player == null || client.interactionManager == null || client.getServer() == null) return;
        ticks++;
        if (stage == 1) {
            serverWork = client.getServer().submit(() -> {
                var world = client.getServer().getOverworld();
                var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                world.setBlockState(POS, BCTransportBlocks.pipeHolder.getDefaultState());
                var tile = (TilePipeHolder) world.getBlockEntity(POS);
                tile.onPlacedBy(null, new ItemStack(BCTransportItems.pipeItemStone));
                tile.wireManager.addPart(WIRE, DyeColor.RED);
                tile.wireManager.addPart(EnumWirePart.EAST_UP_NORTH, DyeColor.RED);
                tile.replacePluggable(Direction.UP, new PluggableGate(BCSiliconPlugs.gate, tile, Direction.UP,
                    new GateVariant(EnumGateLogic.OR, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER)));
                player.changeGameMode(GameMode.CREATIVE);
                aim(player, WIRE.boundingBox.getCenter().add(Vec3d.of(POS)));
            });
            advance();
        } else if (stage == 2 && ready()) {
            if (!(client.world.getBlockEntity(POS) instanceof TilePipeHolder tile) || tile.getPipe() == null
                    || tile.wireManager.parts.size() != 2 || tile.getPluggable(Direction.UP) == null) return;
            clientTile = tile;
            attack(client, 13 + WIRE.ordinal());
            require(client.world.getBlockEntity(POS) == clientTile, "Wire prediction replaced the pipe block entity");
            advance();
        } else if (stage == 3 && ticks >= 15) {
            require(client.world.getBlockEntity(POS) == clientTile && clientTile.getPipe() != null,
                "Wire acknowledgement lost the existing client pipe");
            require(clientTile.wireManager.getColorOfPart(WIRE) == null && clientTile.wireManager.parts.size() == 1
                && clientTile.getPluggable(Direction.UP) != null, "Wire break changed unrelated attachments");
            serverWork = client.getServer().submit(() -> {
                var tile = (TilePipeHolder) client.getServer().getOverworld().getBlockEntity(POS);
                require(tile != null && tile.getPipe() != null && tile.wireManager.parts.size() == 1, "Server did not preserve pipe after wire break");
                var gate = tile.getPluggable(Direction.UP);
                aim(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()), gate.getBoundingBox().getCenter().add(Vec3d.of(POS)));
            });
            advance();
        } else if (stage == 4 && ready()) {
            attack(client, 7 + Direction.UP.ordinal());
            require(client.world.getBlockEntity(POS) == clientTile, "Gate prediction replaced the pipe block entity");
            advance();
        } else if (stage == 5 && ticks >= 15) {
            require(client.world.getBlockEntity(POS) == clientTile && clientTile.getPipe() != null
                && clientTile.getPluggable(Direction.UP) == null && clientTile.wireManager.parts.size() == 1,
                "Gate acknowledgement lost pipe/wire or retained gate");
            serverWork = client.getServer().submit(() -> {
                var tile = (TilePipeHolder) client.getServer().getOverworld().getBlockEntity(POS);
                require(tile != null && tile.getPluggable(Direction.UP) == null, "Server retained removed gate");
                aim(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()), Vec3d.ofCenter(POS));
            });
            advance();
        } else if (stage == 6 && ready()) {
            attack(client, 0);
            advance();
        } else if (stage == 7 && ticks >= 15) {
            require(client.world.isAir(POS), "Centre break no longer removes the whole pipe on the client");
            if (serverWork == null) {
                serverWork = client.getServer().submit(() -> require(client.getServer().getOverworld().isAir(POS),
                    "Centre break no longer removes the whole pipe on the server"));
                return;
            }
            if (!serverWork.isDone()) return;
            serverWork.join();
            System.out.println("BUILDCRAFT FULL CLIENT INTERACTIONS: wire break, gate break, whole pipe break; failures=0");
            advance();
        } else if (stage == 8) {
            var tag=new net.minecraft.nbt.NbtCompound(); tag.putString("batch","Remake by FelixMantra");
            testFluid=net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.fluid.Fluids.WATER,tag);
            serverWork=client.getServer().submit(() -> {
                var world=client.getServer().getOverworld();
                world.setBlockState(POS.west(),buildcraft.factory.BCFactoryBlocks.tank.getDefaultState());
                world.setBlockState(POS.east(2),buildcraft.factory.BCFactoryBlocks.tank.getDefaultState());
                for (int i=0;i<2;i++) {
                    world.setBlockState(POS.east(i),BCTransportBlocks.pipeHolder.getDefaultState());
                    var tile=(TilePipeHolder)world.getBlockEntity(POS.east(i));
                    tile.onPlacedBy(null,new ItemStack(i==0?BCTransportItems.pipeFluidWood:BCTransportItems.pipeFluidStone));
                }
                var source=net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(world,POS.west(),Direction.EAST);
                try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    require(source.insert(testFluid,81037,tx)==81037,"Client fixture tank fill failed"); tx.commit();
                }
                var wood=(TilePipeHolder)world.getBlockEntity(POS); wood.tick();
                ((TilePipeHolder)world.getBlockEntity(POS.east())).tick();
                var mj=(buildcraft.api.mj.IMjReceiver)buildcraft.api.mj.MjAPI.CONNECTOR.find(world,POS,Direction.UP);
                require(mj.receivePower(2*buildcraft.api.mj.MjAPI.MJ,false)<2*buildcraft.api.mj.MjAPI.MJ,"Client fixture MJ extraction failed");
            });
            advance();
        } else if (stage == 9 && ready()) {
            for(int i=0;i<2;i++) if (client.world.getBlockEntity(POS.east(i)) instanceof TilePipeHolder tile
                    && tile.getPipe()!=null && tile.getPipe().flow instanceof buildcraft.transport.pipe.flow.PipeFlowFluids flow) {
                var fluid=flow.getFluidStackForRender();
                if (fluid!=null && !fluid.isBlank()) {
                    require(fluid.equals(testFluid),"Native fluid object-cache reply lost variant NBT");
                    sawFluid |= java.util.Arrays.stream(flow.getAmountsForRender(1)).sum()>0;
                }
            }
            if(ticks<300) return;
            require(sawFluid,"Client never received renderable fluid identity and amounts");
            serverWork=client.getServer().submit(() -> {
                var world=client.getServer().getOverworld();
                var destination=net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(world,POS.east(2),Direction.WEST);
                long total=0; for(var view:destination.nonEmptyViews()) { require(view.getResource().equals(testFluid),"Networked delivery lost NBT"); total+=view.getAmount(); }
                require(total==81037,"Integrated-server fluid delivery lost exact droplets: "+total);
            });
            advance();
        } else if (stage == 10 && ready()) {
            for(int i=0;i<2;i++) {
                var tile=(TilePipeHolder)client.world.getBlockEntity(POS.east(i));
                var flow=(buildcraft.transport.pipe.flow.PipeFlowFluids)tile.getPipe().flow;
                require(flow.getFluidStackForRender()==null && java.util.Arrays.stream(flow.getAmountsForRender(1)).sum()==0,
                    "Client retained a ghost fluid after the server emptied the pipe");
            }
            System.out.println("BUILDCRAFT FULL CLIENT FLUIDS: native cache NBT, visible transport, exact 81037 droplets, empty sync; failures=0");
            stage = 11;
            client.world.disconnect();
            client.disconnect();
            client.scheduleStop();
        }
    }

    private void advance() { stage++; ticks = 0; serverWork = stage == 3 || stage == 5 || stage == 7 ? null : serverWork; }
    private boolean ready() { if (serverWork == null || !serverWork.isDone() || ticks < 15) return false; serverWork.join(); return true; }
    private static void attack(MinecraftClient client, int expectedPart) {
        var trace = BCTransportBlocks.pipeHolder.rayTrace(client.world, POS, client.player);
        require(trace != null && trace.subHit == expectedPart, "Client test aimed at the wrong pipe part: " + (trace == null ? "miss" : trace.subHit));
        client.interactionManager.attackBlock(POS, trace.getSide());
    }
    private static void aim(ServerPlayerEntity player, Vec3d target) {
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        double eyeY = -60 + player.getEyeHeight(player.getPose());
        float pitch = (float) Math.toDegrees(Math.atan2(eyeY - target.y, target.z - 0.5));
        player.teleport(player.getServerWorld(), target.x, -60, 0.5, 0, pitch);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
