/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.transport.*;
import buildcraft.core.BCCoreStatements;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.statement.*;
import buildcraft.silicon.*;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.silicon.container.ContainerGate;
import buildcraft.transport.*;
import buildcraft.transport.item.ItemWire;
import buildcraft.transport.tile.TilePipeHolder;
import buildcraft.transport.wire.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

public final class WireGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(2,3,3);
    private static final EnumWirePart WEST=EnumWirePart.WEST_UP_NORTH, EAST=EnumWirePart.EAST_UP_NORTH;
    private static final Set<BlockPos> DENIED=new HashSet<>();
    static { PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,tile)->!DENIED.contains(pos)); }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allWireColoursPlaceOnEightCornersAndRetainDropsAndNbt(TestContext c) {
        var tile=pipe(c,POS); var player=c.createMockCreativeServerPlayerInWorld(); player.changeGameMode(GameMode.SURVIVAL);
        for(var colour:DyeColor.values()) for(var part:EnumWirePart.VALUES) {
            tile.wireManager.removeParts(new ArrayList<>(tile.wireManager.parts.keySet()));
            var target=Vec3d.of(tile.getPos()).add(0.5+part.x.offset()*0.15,0.5+part.y.offset()*0.15,0.5+part.z.offset()*0.25);
            aim(player,target,target.add(0,0,part.z.offset()*2));
            var stack=BCTransportItems.wire.stack(colour); stack.setCount(2); player.setStackInHand(Hand.MAIN_HAND,stack);
            var hit=new BlockHitResult(target,part.z.offset()<0?Direction.NORTH:Direction.SOUTH,tile.getPos(),false);
            var result=BCTransportBlocks.pipeHolder.onUse(tile.getCachedState(),c.getWorld(),tile.getPos(),player,Hand.MAIN_HAND,hit);
            c.assertTrue(result.isAccepted() && tile.wireManager.getColorOfPart(part)==colour && stack.getCount()==1,"Wire placement/count failed: "+colour+"/"+part);
            var copy=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState()); copy.readNbt(tile.createNbt());
            c.assertTrue(copy.wireManager.getColorOfPart(part)==colour,"Wire NBT lost corner/colour");
            var loot=new net.minecraft.loot.context.LootContextParameterSet.Builder(c.getWorld())
                .add(net.minecraft.loot.context.LootContextParameters.BLOCK_ENTITY,tile)
                .add(net.minecraft.loot.context.LootContextParameters.ORIGIN,Vec3d.ofCenter(tile.getPos()))
                .add(net.minecraft.loot.context.LootContextParameters.TOOL,ItemStack.EMPTY);
            var drops=BCTransportBlocks.pipeHolder.getDroppedStacks(tile.getCachedState(),loot);
            c.assertTrue(drops.size()==2 && drops.stream().anyMatch(s->s.isOf(BCTransportItems.wire) && ItemWire.getColour(s)==colour && s.getCount()==1),"Whole-pipe drop lost wire colour or duplicated contents");
        }
        var invalid=BCTransportItems.wire.stack(DyeColor.RED); invalid.getOrCreateNbt().putInt("wire_colour",99);
        c.assertTrue(ItemWire.getColour(invalid)==DyeColor.WHITE,"Invalid stack colour was not normalized");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=80)
    public void actualGateSignalCrossesWiresRetractsOnBreakAndReturnsAfterReload(TestContext c) {
        var chain=chain(c); var sender=gate(chain[0]); var receiver=gate(chain[2]);
        configure(sender,BCCoreStatements.TRIGGER_TRUE,BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.RED.ordinal()]);
        configure(receiver,BCTransportStatements.TRIGGER_PIPE_SIGNAL[DyeColor.RED.ordinal()*2+1],BCCoreStatements.ACTION_REDSTONE);
        var player=c.createMockCreativeServerPlayerInWorld(); player.refreshPositionAndAngles(chain[0].getPos(),0,0);
        BCSiliconScreens.open(player,sender); var screen=(ContainerGate)player.currentScreenHandler;
        c.assertTrue(screen.actions.stream().anyMatch(a->a.delegate==BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.RED.ordinal()])
            && screen.actions.stream().noneMatch(a->a.delegate==BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.BLUE.ordinal()]),"Gate offered a missing wire colour");
        player.closeHandledScreen();
        c.waitAndRun(5,()->{
            c.assertTrue(chain[2].wireManager.isAnyPowered(DyeColor.RED) && chain[2].getRedstoneOutput(Direction.SOUTH)==15,"Remote gate did not receive signal through real world tick callback");
            chain[1].wireManager.removePart(WEST);
        });
        c.waitAndRun(10,()->{
            c.assertTrue(!chain[2].wireManager.isAnyPowered(DyeColor.RED) && chain[2].getRedstoneOutput(Direction.SOUTH)==0,"Broken network retained power");
            chain[1].wireManager.addPart(WEST,DyeColor.RED);
        });
        c.waitAndRun(15,()->{
            c.assertTrue(chain[2].getRedstoneOutput(Direction.SOUTH)==15,"Rejoined network did not recover");
            var old=chain[1]; var copy=BCTransportBlocks.pipeHolderTile.instantiate(old.getPos(),old.getCachedState()); copy.readNbt(old.createNbt());
            c.getWorld().removeBlockEntity(old.getPos()); c.getWorld().addBlockEntity(copy); chain[1]=copy;
        });
        c.waitAndRun(20,()->{
            c.assertTrue(chain[2].getRedstoneOutput(Direction.SOUTH)==15,"Reconstructed pipe failed to rejoin network");
            chain[0].replacePluggable(Direction.UP,null);
        });
        c.waitAndRun(25,()->{
            c.assertTrue(!chain[2].wireManager.isAnyPowered(DyeColor.RED) && chain[2].getRedstoneOutput(Direction.SOUTH)==0,"Removed emitter remained cached");
            c.complete();
        });
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void wirePlacementExtendsToNeighbourAndAwardsConnectionAdvancement(TestContext c) {
        var first=pipe(c,POS); var next=pipe(c,POS.east()); first.wireManager.addPart(EAST,DyeColor.RED);
        first.tick(); next.tick();
        var player=c.createMockCreativeServerPlayerInWorld(); player.changeGameMode(GameMode.SURVIVAL);
        var target=EAST.boundingBox.getCenter().add(Vec3d.of(first.getPos())); aim(player,target,target.add(2,0,0));
        var stack=BCTransportItems.wire.stack(DyeColor.RED); stack.setCount(2); player.setStackInHand(Hand.MAIN_HAND,stack);
        var hit=new BlockHitResult(target,Direction.EAST,first.getPos(),false);
        c.assertTrue(BCTransportBlocks.pipeHolder.onUse(first.getCachedState(),c.getWorld(),first.getPos(),player,Hand.MAIN_HAND,hit).isAccepted()
            && next.wireManager.getColorOfPart(WEST)==DyeColor.RED && stack.getCount()==1,"Clicking a wire did not extend it to the adjacent pipe");
        var advancement=c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcrafttransport:logic_transportation"));
        c.assertTrue(advancement!=null && player.getAdvancementTracker().getProgress(advancement).isDone(),"Connecting wire did not award restored advancement");
        c.assertTrue(!BCTransportBlocks.pipeHolder.onUse(first.getCachedState(),c.getWorld(),first.getPos(),player,Hand.MAIN_HAND,hit).isAccepted()
            && stack.getCount()==1,"Occupied wire destination consumed another item");
        player.refreshPositionAndAngles(first.getPos().add(20,0,0),0,0);
        c.assertTrue(!BCTransportBlocks.pipeHolder.onUse(first.getCachedState(),c.getWorld(),first.getPos(),player,Hand.MAIN_HAND,hit).isAccepted(),"Distant wire placement bypassed permissions");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void wireNetworksRespectColourBoundariesAndCombineEmitters(TestContext c) {
        var chain=chain(c); var first=gate(chain[0]); var second=gate(chain[2]);
        configure(first,BCCoreStatements.TRIGGER_TRUE,BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.RED.ordinal()]);
        configure(second,BCCoreStatements.TRIGGER_TRUE,BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.RED.ordinal()]);
        for(var tile:chain) {
            tile.wireManager.addPart(EnumWirePart.WEST_DOWN_SOUTH,DyeColor.BLUE);
            tile.wireManager.addPart(EnumWirePart.EAST_DOWN_SOUTH,DyeColor.BLUE);
        }
        tick(c,chain);
        c.assertTrue(chain[1].wireManager.isAnyPowered(DyeColor.RED) && !chain[1].wireManager.isAnyPowered(DyeColor.BLUE),"Different wire colours shorted");
        chain[0].replacePluggable(Direction.UP,null); tick(c,chain);
        c.assertTrue(chain[1].wireManager.isAnyPowered(DyeColor.RED),"Removing one emitter disabled the other");
        chain[2].getPipe().setColour(DyeColor.BLUE); chain[1].getPipe().setColour(DyeColor.RED); tick(c,chain);
        c.assertTrue(!chain[1].wireManager.isAnyPowered(DyeColor.RED),"Wire crossed disconnected dyed pipes");
        chain[1].getPipe().setColour(null); tick(c,chain);
        c.assertTrue(chain[1].wireManager.isAnyPowered(DyeColor.RED),"Wire did not reconnect after pipe dye changed");
        chain[2].onChunkUnload(); WorldSavedDataWireSystems.get(c.getWorld()).tick();
        c.assertTrue(!chain[1].wireManager.isAnyPowered(DyeColor.RED),"Unloaded emitter remained active");
        chain[2].onLoad(); tick(c,chain);
        c.assertTrue(chain[1].wireManager.isAnyPowered(DyeColor.RED),"Loaded emitter failed to rejoin");
        var stale=new net.minecraft.nbt.NbtCompound(); stale.putString("entries","invalid legacy graph");
        c.assertTrue(WorldSavedDataWireSystems.fromNbt(stale,c.getWorld()).wireSystems.isEmpty(),"World graph resurrected stale saved entries");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void wirePayloadIsBoundedAtomicAndServerAuthoritative(TestContext c) throws java.io.IOException {
        var tile=pipe(c,POS); for(var part:EnumWirePart.VALUES) tile.wireManager.addPart(part,DyeColor.RED);
        var gate=gate(tile); configure(gate,BCCoreStatements.TRIGGER_TRUE,BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.RED.ordinal()]); tick(c,new TilePipeHolder[]{tile});
        var copy=BCTransportBlocks.pipeHolderTile.instantiate(tile.getPos(),tile.getCachedState());
        var packet=new PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {
            tile.wireManager.writePayload(packet,EnvType.SERVER);
            c.assertTrue(packet.readableBytes()==10,"Wire snapshot exceeded eight packed corners plus count and power mask");
            copy.wireManager.readPayload(packet,EnvType.CLIENT,null);
            c.assertTrue(copy.wireManager.parts.equals(tile.wireManager.parts) && copy.wireManager.poweredClient.size()==8 && !packet.isReadable(),"Wire creation/power snapshot lost state");
        } finally { packet.release(); }
        for(byte[] bad:new byte[][]{{9},{1,(byte)0xf0,0},{2,0,0,0}}) {
            packet=new PacketBufferBC(io.netty.buffer.Unpooled.wrappedBuffer(bad)); boolean rejected=false;
            try { copy.wireManager.readPayload(packet,EnvType.CLIENT,null); } catch(java.io.IOException expected){rejected=true;} finally{packet.release();}
            c.assertTrue(rejected && copy.wireManager.parts.size()==8,"Malformed wire snapshot partially replaced state");
        }
        packet=new PacketBufferBC(io.netty.buffer.Unpooled.wrappedBuffer(new byte[]{0,0}));
        try { copy.wireManager.readPayload(packet,EnvType.SERVER,null); c.assertTrue(packet.readerIndex()==0 && copy.wireManager.parts.size()==8,"Client packet changed server wires"); } finally {packet.release();}
        var malformed=new net.minecraft.nbt.NbtCompound(); malformed.putIntArray("parts",new int[]{-1,0,8,0,0,16,1,14,7});
        copy.wireManager.readFromNbt(malformed);
        c.assertTrue(copy.wireManager.parts.size()==1 && copy.wireManager.getColorOfPart(EnumWirePart.VALUES[1])==DyeColor.RED,"Malformed NBT was not bounded");
        c.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativePartialBreakingPreservesPipeAndHonoursProtection(TestContext c) {
        var tile=pipe(c,POS); tile.wireManager.addPart(WEST,DyeColor.BLUE);
        var player=c.createMockCreativeServerPlayerInWorld(); player.changeGameMode(GameMode.SURVIVAL);
        var target=WEST.boundingBox.getCenter().add(Vec3d.of(tile.getPos())); aim(player,target,target.add(0,0,-2));
        var trace=BCTransportBlocks.pipeHolder.rayTrace(c.getWorld(),tile.getPos(),player);
        c.assertTrue(trace!=null && buildcraft.transport.block.BlockPipeHolder.getWirePartHit(trace)==WEST,"Test did not aim at wire");
        DENIED.add(tile.getPos());
        try { player.interactionManager.tryBreakBlock(tile.getPos()); } finally { DENIED.remove(tile.getPos()); }
        c.assertTrue(tile.wireManager.getColorOfPart(WEST)==DyeColor.BLUE && drops(c,tile)==0,"Protected break still removed a wire");
        c.assertTrue(!player.interactionManager.tryBreakBlock(tile.getPos()) && c.getWorld().getBlockEntity(tile.getPos())==tile,"Partial break removed whole pipe");
        c.assertTrue(tile.wireManager.parts.isEmpty() && drops(c,tile)==1,"Partial wire break lost or duplicated drop");
        var gate=gate(tile); target=gate.getBoundingBox().getCenter().add(Vec3d.of(tile.getPos())); aim(player,target,target.add(0,2,0));
        player.interactionManager.tryBreakBlock(tile.getPos());
        c.assertTrue(tile.getPluggable(Direction.UP)==null && c.getWorld().getBlockEntity(tile.getPos())==tile && drops(c,tile)==2,"Partial gate break lost drop or pipe");
        player.changeGameMode(GameMode.CREATIVE); tile.wireManager.addPart(WEST,DyeColor.RED);
        target=WEST.boundingBox.getCenter().add(Vec3d.of(tile.getPos())); aim(player,target,target.add(0,0,-2)); player.interactionManager.tryBreakBlock(tile.getPos());
        c.assertTrue(tile.wireManager.parts.isEmpty() && drops(c,tile)==2,"Creative partial break produced survival loot");
        c.complete();
    }

    private static int drops(TestContext c,TilePipeHolder tile) { return c.getWorld().getEntitiesByClass(ItemEntity.class,new Box(tile.getPos()).expand(1),e->true).stream().mapToInt(e->e.getStack().getCount()).sum(); }
    private static TilePipeHolder pipe(TestContext c,BlockPos pos) { c.setBlockState(pos,BCTransportBlocks.pipeHolder); var tile=(TilePipeHolder)c.getBlockEntity(pos); tile.onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone)); return tile; }
    private static TilePipeHolder[] chain(TestContext c) {
        var chain=new TilePipeHolder[]{pipe(c,POS),pipe(c,POS.east()),pipe(c,POS.east(2))};
        for(var tile:chain) { tile.wireManager.addPart(WEST,DyeColor.RED); tile.wireManager.addPart(EAST,DyeColor.RED); }
        return chain;
    }
    private static PluggableGate gate(TilePipeHolder tile) { var gate=new PluggableGate(BCSiliconPlugs.gate,tile,Direction.UP,new GateVariant(EnumGateLogic.OR,EnumGateMaterial.IRON,EnumGateModifier.NO_MODIFIER)); tile.replacePluggable(Direction.UP,gate); return gate; }
    private static void configure(PluggableGate gate,buildcraft.api.statements.ITriggerInternal trigger,buildcraft.api.statements.IActionInternal action) {
        gate.logic.statements[0].trigger.set(new TriggerWrapper.TriggerWrapperInternal(trigger)); gate.logic.statements[0].action.set(new ActionWrapper.ActionWrapperInternal(action));
    }
    private static void tick(TestContext c,TilePipeHolder[] tiles) { for(var tile:tiles)tile.tick(); WorldSavedDataWireSystems.get(c.getWorld()).tick(); }
    private static void aim(ServerPlayerEntity player,Vec3d target,Vec3d eye) {
        var look=target.subtract(eye).normalize(); player.refreshPositionAndAngles(eye.x,eye.y-player.getEyeHeight(player.getPose()),eye.z,
            (float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.asin(look.y))); player.setHeadYaw(player.getYaw());
    }
}
