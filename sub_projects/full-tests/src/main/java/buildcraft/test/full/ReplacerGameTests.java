/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.schematics.*;
import buildcraft.builders.*;
import buildcraft.builders.container.ContainerReplacer;
import buildcraft.builders.item.*;
import buildcraft.builders.snapshot.*;
import buildcraft.builders.tile.*;
import buildcraft.lib.tile.TileBC_Neptune;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

public final class ReplacerGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(2,170,2);
    private static final Map<ServerWorld,Set<BlockPos>> DENIED=new WeakHashMap<>(),SWAP=new WeakHashMap<>();
    static {UseBlockCallback.EVENT.register((p,w,h,hit)->{
        if(DENIED.getOrDefault(w,Set.of()).contains(hit.getBlockPos()))return ActionResult.FAIL;
        if(SWAP.getOrDefault(w,Set.of()).contains(hit.getBlockPos()))p.setStackInHand(h,new ItemStack(Items.STICK));
        return ActionResult.PASS;
    });}
    private static BlockPos base(TestContext c){var p=c.getAbsolutePos(POS);for(var at:BlockPos.iterate(p.add(-1,-1,-1),p.add(6,4,5)))c.getWorld().setBlockState(at,Blocks.AIR.getDefaultState());return p;}
    private static ServerPlayerEntity player(TestContext c,BlockPos p){var user=c.createMockCreativeServerPlayerInWorld();user.getInventory().clear();user.refreshPositionAndAngles(p.getX()+.5,p.getY()+2,p.getZ()+.5,0,0);return user;}
    private static ActionResult use(ServerPlayerEntity p,BlockPos at){return p.interactionManager.interactBlock(p,p.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(at).add(0,.5,0),Direction.UP,at,false));}
    private static ISchematicBlock schematic(TestContext c,BlockPos at,BlockState state){c.getWorld().setBlockState(at,state);return SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(c.getWorld(),at,at,state,state.getBlock()));}
    private static ItemStack sample(ISchematicBlock s){var stack=new ItemStack(BCBuildersItems.schematicSingle);stack.getOrCreateNbt().put(ItemSchematicSingle.NBT_KEY,SchematicBlockManager.writeToNBT(s));return stack;}
    private static Blueprint plan(ISchematicBlock...blocks){var b=new Blueprint();b.size=new BlockPos(blocks.length,1,1);b.offset=BlockPos.ORIGIN;b.facing=Direction.NORTH;b.data=new int[blocks.length];for(int i=0;i<blocks.length;i++){b.palette.add(blocks[i]);b.data[i]=i;}b.computeKey();return b;}
    private static TileReplacer machine(TestContext c,BlockPos at,Blueprint b,ISchematicBlock from,ISchematicBlock to){c.getWorld().setBlockState(at,BCBuildersBlocks.replacer.getDefaultState());var tile=(TileReplacer)c.getWorld().getBlockEntity(at);tile.onPlacedBy(player(c,at),new ItemStack(BCBuildersBlocks.replacer));c.assertTrue(GlobalSavedDataSnapshots.get(c.getWorld()).addSnapshot(b),"Original plan save failed");tile.invSnapshot.setStackInSlot(0,BCBuildersItems.snapshot.getUsed(EnumSnapshotType.BLUEPRINT,new Snapshot.Header(b.key,tile.getOwner().getId(),new Date(0),"Original design")));tile.invSchematicFrom.setStackInSlot(0,sample(from));tile.invSchematicTo.setStackInSlot(0,sample(to));return tile;}
    private static TileReplacer reload(TileReplacer t){var w=t.getWorld();var p=t.getPos();var state=t.getCachedState();var n=t.createNbtWithIdentifyingData();w.removeBlockEntity(p);var copy=(TileReplacer)BlockEntity.createFromNbt(p,state,n);w.addBlockEntity(copy);copy.onLoad();return copy;}
    private static int count(ServerPlayerEntity p,Item item){int n=0;for(var s:p.getInventory().main)if(s.isOf(item))n+=s.getCount();return n;}
    private static ItemStack gem(int count){var s=new ItemStack(Items.DIAMOND,count);s.setCustomName(Text.literal("Felix original"));return s;}

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeUseFirstCapturesOneChestWithoutOpeningOrChangingContents(TestContext c)throws Exception{
        var b=base(c);c.getWorld().setBlockState(b,Blocks.CHEST.getDefaultState());var chest=(ChestBlockEntity)c.getWorld().getBlockEntity(b);chest.setStack(0,gem(3));var p=player(c,b);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCBuildersItems.schematicSingle,3));
        c.assertTrue(use(p,b).isAccepted() && p.currentScreenHandler==p.playerScreenHandler,"Schematic use opened chest or failed");
        var captured=p.getInventory().main.stream().filter(ItemSchematicSingle::isUsed).toList();
        c.assertTrue(p.getMainHandStack().getMaxCount()==16 && captured.get(0).getMaxCount()==1,"Native blank/recorded stack limits differ from original");
        c.assertTrue(captured.size()==1 && captured.get(0).getCount()==1 && p.getMainHandStack().getCount()==2 && count(p,BCBuildersItems.schematicSingle)==3,"Capture duplicated or converted all blanks");
        c.assertTrue(ItemStack.areEqual(chest.getStack(0),gem(3)) && ItemSchematicSingle.getSchematic(captured.get(0)).computeRequiredItems().stream().anyMatch(s->ItemStack.areEqual(s,gem(3))),"Chest capture lost exact contents");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sneakClearsNativeAndLegacySamplesWithoutLosingOtherData(TestContext c){
        var b=base(c);var p=player(c,b);var s=sample(schematic(c,b,Blocks.STONE.getDefaultState()));s.setCustomName(Text.literal("Keep name"));s.getOrCreateNbt().putInt("Damage",1);p.setStackInHand(Hand.MAIN_HAND,s);p.setSneaking(true);
        c.assertTrue(use(p,b).isAccepted() && !ItemSchematicSingle.isUsed(p.getMainHandStack()) && p.getMainHandStack().hasCustomName(),"Block-use clear lost unrelated data");
        p.setStackInHand(Hand.MAIN_HAND,sample(schematic(c,b,Blocks.BRICKS.getDefaultState())));BCBuildersItems.schematicSingle.use(c.getWorld(),p,Hand.MAIN_HAND);c.assertTrue(!ItemSchematicSingle.isUsed(p.getMainHandStack()),"Air-use clear failed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void directPlacementPaysExactChestContentsAndRollsBackShortage(TestContext c)throws Exception{
        var b=base(c);c.getWorld().setBlockState(b,Blocks.CHEST.getDefaultState());((ChestBlockEntity)c.getWorld().getBlockEntity(b)).setStack(0,gem(3));var state=c.getWorld().getBlockState(b);var captured=SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(c.getWorld(),b,b,state,state.getBlock()));var p=player(c,b.east(4));p.setStackInHand(Hand.MAIN_HAND,sample(captured));var target=b.east(3);c.getWorld().setBlockState(target.down(),Blocks.STONE.getDefaultState());p.getInventory().setStack(1,new ItemStack(Items.CHEST));p.getInventory().setStack(2,gem(2));p.getInventory().setStack(3,new ItemStack(Items.DIAMOND));
        c.assertTrue(use(p,target.down())==ActionResult.FAIL && c.getWorld().isAir(target) && count(p,Items.CHEST)==1 && count(p,Items.DIAMOND)==3,"Short exact items mutated inventory/world");
        p.getInventory().setStack(3,gem(1));c.assertTrue(use(p,target.down()).isAccepted(),"Paid chest did not place");var result=(ChestBlockEntity)c.getWorld().getBlockEntity(target);
        c.assertTrue(result!=null && ItemStack.areEqual(result.getStack(0),gem(3)) && count(p,Items.CHEST)==0 && count(p,Items.DIAMOND)==0 && ItemSchematicSingle.isUsed(p.getMainHandStack()),"Paid chest lost NBT/material conservation/sample");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeProtectionAndReentrantHeldChangeLeaveResourcesIntact(TestContext c){
        var b=base(c);var p=player(c,b.east(4));var s=schematic(c,b,Blocks.STONE.getDefaultState());p.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCBuildersItems.schematicSingle));DENIED.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(b);
        try{c.assertTrue(use(p,b)==ActionResult.FAIL && !ItemSchematicSingle.isUsed(p.getMainHandStack()),"Native capture protection bypassed");}finally{DENIED.get(c.getWorld()).remove(b);}
        p.setStackInHand(Hand.MAIN_HAND,sample(s));p.getInventory().setStack(1,new ItemStack(Items.STONE));var target=b.up();DENIED.get(c.getWorld()).add(target);
        try{c.assertTrue(use(p,b)==ActionResult.FAIL && c.getWorld().isAir(target)&&count(p,Items.STONE)==1,"Target protection bypassed");}finally{DENIED.get(c.getWorld()).remove(target);}
        SWAP.computeIfAbsent(c.getWorld(),w->new HashSet<>()).add(target);try{c.assertTrue(use(p,b)==ActionResult.FAIL && c.getWorld().isAir(target)&&count(p,Items.STONE)==1,"Stale sample placed after callback");}finally{SWAP.get(c.getWorld()).remove(target);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void unsupportedFluidsAndFailedSupportKeepReplaceableTargets(TestContext c){
        var b=base(c);var p=player(c,b.east(4));var target=b.east(2);c.getWorld().setBlockState(target,Blocks.GRASS.getDefaultState());p.setStackInHand(Hand.MAIN_HAND,sample(schematic(c,b,Blocks.TORCH.getDefaultState())));p.getInventory().setStack(1,new ItemStack(Items.TORCH));
        c.assertTrue(use(p,target)==ActionResult.FAIL && c.getWorld().getBlockState(target).isOf(Blocks.GRASS) && count(p,Items.TORCH)==1,"Failed support removed replaceable target or item");
        p.setStackInHand(Hand.MAIN_HAND,sample(schematic(c,b,Blocks.WATER.getDefaultState())));c.assertTrue(use(p,target)==ActionResult.FAIL && c.getWorld().getBlockState(target).isOf(Blocks.GRASS),"Fluid sample bypassed builder requirement");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedSamplesAndOpenTransactionsNeverMutate(TestContext c){
        var b=base(c);var p=player(c,b.east(4));c.getWorld().setBlockState(b,Blocks.STONE.getDefaultState());var broken=new ItemStack(BCBuildersItems.schematicSingle);broken.getOrCreateNbt().putInt("Damage",1);p.setStackInHand(Hand.MAIN_HAND,broken);c.assertTrue(use(p,b)==ActionResult.FAIL,"Missing legacy data accepted");var tag=new NbtCompound();tag.putString("name","BAD SPACE");broken.getOrCreateNbt().put("schematic",tag);c.assertTrue(use(p,b)==ActionResult.FAIL && ItemSchematicSingle.getSchematicSafe(broken)==null,"Malformed factory crashed/accepted");p.setStackInHand(Hand.MAIN_HAND,new ItemStack(BCBuildersItems.schematicSingle));try(var tx=Transaction.openOuter()){c.assertTrue(use(p,b)==ActionResult.FAIL && !ItemSchematicSingle.isUsed(p.getMainHandStack()),"Capture ran inside foreign transaction");}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void occupiedPlacementWaitsForNativeCollisionToClear(TestContext c){
        var b=base(c);var p=player(c,b.east(4));var s=schematic(c,b,Blocks.STONE.getDefaultState());p.setStackInHand(Hand.MAIN_HAND,sample(s));p.getInventory().setStack(1,new ItemStack(Items.STONE));
        var actor=new net.minecraft.entity.decoration.ArmorStandEntity(c.getWorld(),b.getX()+.5,b.getY()+1,b.getZ()+.5);c.getWorld().spawnEntity(actor);
        c.assertTrue(use(p,b)==ActionResult.FAIL && c.getWorld().isAir(b.up())&&count(p,Items.STONE)==1,"Schematic ignored entity collision");actor.discard();
        c.assertTrue(use(p,b).isAccepted()&&c.getWorld().getBlockState(b.up()).isOf(Blocks.STONE)&&count(p,Items.STONE)==0,"Unblocked sample did not resume");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void replacementUsesNewKeyAndPreservesOriginalStatesAndEntities(TestContext c)throws Exception{
        var b=base(c);var from=schematic(c,b.east(),Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X));var to=schematic(c,b.east(2),Blocks.BRICKS.getDefaultState());var other=schematic(c,b.east(3),Blocks.OAK_LOG.getDefaultState());var original=plan(from,other,from);var armor=new net.minecraft.entity.decoration.ArmorStandEntity(c.getWorld(),b.getX()+.5,b.getY(),b.getZ()+.5);original.entities.add(SchematicEntityManager.getSchematicEntity(new SchematicEntityContext(c.getWorld(),b,armor)));original.computeKey();var before=original.serializeNBT().copy();var tile=machine(c,b,original,from,to);tile.tick();var header=BCBuildersItems.snapshot.getHeader(tile.invSnapshot.getStackInSlot(0));
        c.assertTrue(tile.getStatus()==1 && tile.getReplaced()==2 && !header.key.equals(original.key) && header.name.equals("Original design") && header.owner.equals(tile.getOwner().getId()),"Replacement key/count/header wrong");
        GlobalSavedDataSnapshots.reInit(TileBC_Neptune.NetSide.SERVER);var saved=(Blueprint)GlobalSavedDataSnapshots.get(c.getWorld()).getSnapshot(header.key);var old=GlobalSavedDataSnapshots.get(c.getWorld()).getSnapshot(original.key);
        c.assertTrue(saved.palette.get(0).equals(to)&&saved.palette.get(1).equals(other)&&saved.palette.get(2).equals(to)&&saved.entities.size()==1 && old.serializeNBT().equals(before) && original.serializeNBT().equals(before),"Replacement changed unrelated state/entity/original");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void failedSaveKeepsAllInputsThenRetriesDurably(TestContext c)throws Exception{
        var b=base(c);var from=schematic(c,b.east(),Blocks.STONE.getDefaultState());var to=schematic(c,b.east(2),Blocks.DEEPSLATE_BRICKS.getDefaultState());var original=plan(from,from,from,from,from);original.offset=new BlockPos(3,17,9);original.computeKey();var changed=original.copy();changed.replace(from,to);changed.computeKey();var store=GlobalSavedDataSnapshots.get(c.getWorld());store.removeSnapshot(changed.key);var path=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("snapshots-server").resolve(changed.key+".bcnbt");java.nio.file.Files.createDirectories(path);var tile=machine(c,b,original,from,to);var input=tile.invSnapshot.getStackInSlot(0).copy();
        try{tile.tick();c.assertTrue(tile.getStatus()==5 && ItemStack.areEqual(input,tile.invSnapshot.getStackInSlot(0))&&!tile.invSchematicFrom.getStackInSlot(0).isEmpty()&&!tile.invSchematicTo.getStackInSlot(0).isEmpty(),"Failed save consumed inputs");}finally{java.nio.file.Files.delete(path);}
        for(int i=0;i<22;i++)tile.tick();c.assertTrue(tile.getStatus()==1 && BCBuildersItems.snapshot.getHeader(tile.invSnapshot.getStackInSlot(0)).key.equals(changed.key),"Failed save did not recover");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void noMatchAndInvalidInputAreRecoverableWithoutConsumption(TestContext c){
        var b=base(c);var from=schematic(c,b.east(),Blocks.STONE.getDefaultState());var to=schematic(c,b.east(2),Blocks.BRICKS.getDefaultState());var tile=machine(c,b,plan(to),from,to);tile.tick();c.assertTrue(tile.getStatus()==2&&!tile.invSchematicFrom.getStackInSlot(0).isEmpty(),"No-op consumed samples");tile.invSchematicFrom.setStackInSlot(0,sample(to));tile.invSchematicTo.setStackInSlot(0,sample(from));tile.tick();c.assertTrue(tile.getStatus()==1,"Corrected no-match did not resume");var malformed=sample(from);malformed.getNbt().getCompound("schematic").putString("name","BAD!");tile.invSchematicFrom.setStackInSlot(0,malformed);tile.invSchematicTo.setStackInSlot(0,sample(to));tile.tick();c.assertTrue(tile.getStatus()==3&&!tile.invSchematicTo.getStackInSlot(0).isEmpty(),"Invalid sample consumed input");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void largeReplacementCountsPreserveBothPropertyHalves(TestContext c){
        var b=base(c);var from=schematic(c,b.east(),Blocks.STONE.getDefaultState());var to=schematic(c,b.east(2),Blocks.BRICKS.getDefaultState());var large=plan(from);large.size=new BlockPos(500,200,1);large.data=new int[100_000];large.computeKey();var tile=machine(c,b,large,from,to);tile.tick();
        c.assertTrue(tile.getStatus()==1 && tile.getReplaced()==100_000 && ((tile.properties.get(2)<<16)|tile.properties.get(1))==100_000,"Large replacement count was truncated to a network short");
        var received=new TileReplacer(tile.getType(),tile.getPos(),tile.getCachedState());for(int i=0;i<3;i++)received.properties.set(i,(short)tile.properties.get(i));c.assertTrue(received.getReplaced()==100_000,"Signed native property values lost high/low bits");
        var result=(Blueprint)GlobalSavedDataSnapshots.get(c.getWorld()).getSnapshot(BCBuildersItems.snapshot.getHeader(tile.invSnapshot.getStackInSlot(0)).key);
        c.assertTrue(result.data.length==100_000 && result.palette.get(0).equals(to),"Large plan lost data");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeScreenQuickMoveFiltersAndRejectsStaleOrDistantAccess(TestContext c){
        var b=base(c);var from=schematic(c,b.east(),Blocks.STONE.getDefaultState());var to=schematic(c,b.east(2),Blocks.BRICKS.getDefaultState());var tile=machine(c,b,plan(from),from,to);tile.invSchematicFrom.setStackInSlot(0,ItemStack.EMPTY);tile.invSchematicTo.setStackInSlot(0,ItemStack.EMPTY);var p=player(c,b);BCBuildersScreens.openReplacer(p,b);c.assertTrue(p.currentScreenHandler instanceof ContainerReplacer&&p.currentScreenHandler.getType()==BCBuildersScreens.REPLACER,"Native replacer screen unavailable");var h=(ContainerReplacer)p.currentScreenHandler;p.getInventory().setStack(9,new ItemStack(Items.DIAMOND));c.assertTrue(h.quickMove(p,3).isEmpty(),"Invalid item entered replacer");var captured=sample(from);captured.setCount(3);p.getInventory().setStack(9,captured);h.quickMove(p,3);c.assertTrue(tile.invSchematicFrom.getStackInSlot(0).getCount()==1&&tile.invSchematicTo.getStackInSlot(0).getCount()==1&&p.getInventory().getStack(9).getCount()==1,"Sample slots exceeded one-item limit");
        var storage=ItemStorage.SIDED.find(c.getWorld(),b,Direction.UP);try(var tx=Transaction.openOuter()){c.assertTrue(storage==null||storage.extract(ItemVariant.of(tile.invSnapshot.getStackInSlot(0)),1,tx)==0,"Replacer exposed unsupported automation");}
        p.refreshPositionAndAngles(b.getX()+100,b.getY(),b.getZ(),0,0);c.assertTrue(!h.canUse(p)&&h.quickMove(p,0).isEmpty(),"Distant screen mutated machine");p.refreshPositionAndAngles(b.getX(),b.getY(),b.getZ(),0,0);c.getWorld().removeBlockEntity(b);c.assertTrue(!h.canUse(p),"Stale screen retained access");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void reloadPreservesInputsStatusAndStaleTilesCannotReplace(TestContext c){
        var b=base(c);var from=schematic(c,b.east(),Blocks.STONE.getDefaultState());var to=schematic(c,b.east(2),Blocks.BRICKS.getDefaultState());var tile=machine(c,b,plan(from,from),from,to);var old=tile;tile=reload(tile);old.tick();c.assertTrue(!old.invSchematicFrom.getStackInSlot(0).isEmpty(),"Stale replacer spent input");try(var tx=Transaction.openOuter()){tile.tick();c.assertTrue(tile.getStatus()==0,"Replacer ran inside storage transaction");}tile.tick();tile=reload(tile);c.assertTrue(tile.getStatus()==1&&tile.getReplaced()==2&&tile.invSchematicFrom.getStackInSlot(0).isEmpty()&&tile.invSchematicTo.getStackInSlot(0).isEmpty(),"Completed replacement lost reload state");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void replacedBlueprintBuildsNativeChangedBlocks(TestContext c){
        var b=base(c);var from=schematic(c,b.east(),Blocks.STONE.getDefaultState());var to=schematic(c,b.east(2),Blocks.BRICKS.getDefaultState());var tile=machine(c,b,plan(from,from),from,to);tile.tick();var at=b.south(3);c.getWorld().setBlockState(at,BCBuildersBlocks.builder.getDefaultState().with(BCBuildersBlocks.builder.getFacingProperty(),Direction.NORTH));var builder=(TileBuilder)c.getWorld().getBlockEntity(at);builder.onPlacedBy(player(c,at),new ItemStack(BCBuildersBlocks.builder));builder.invSnapshot.setStackInSlot(0,tile.invSnapshot.getStackInSlot(0).copy());builder.invResources.setStackInSlot(0,new ItemStack(Items.BRICKS,2));builder.getBattery().addPower(500*MjAPI.MJ,false);for(int i=0;i<30;i++)builder.tick();c.assertTrue(builder.isFinished()&&c.getWorld().getBlockState(at.south()).isOf(Blocks.BRICKS)&&c.getWorld().getBlockState(at.south().east()).isOf(Blocks.BRICKS)&&builder.invResources.getStackInSlot(0).isEmpty(),"Replacer output still built original palette");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeSurvivalRecipesProduceUsableSamplesAndReplacer(TestContext c){
        var b=base(c);var p=player(c,b);var h=new net.minecraft.screen.CraftingScreenHandler(1,p.getInventory(),net.minecraft.screen.ScreenHandlerContext.create(c.getWorld(),b));h.getSlot(1).setStack(new ItemStack(Items.PAPER));h.getSlot(2).setStack(new ItemStack(Items.PAPER));h.getSlot(3).setStack(new ItemStack(Items.LAPIS_LAZULI));h.onContentChanged(h.getSlot(1).inventory);var out=h.getSlot(0).getStack();c.assertTrue(out.isOf(BCBuildersItems.schematicSingle)&&out.getCount()==4&&!ItemSchematicSingle.isUsed(out),"Blank sample recipe missing");h.onSlotClick(0,0,net.minecraft.screen.slot.SlotActionType.PICKUP,p);for(int i=1;i<=9;i++)c.assertTrue(h.getSlot(i).getStack().isEmpty(),"Sample recipe retained ingredients");
        var items=new Item[]{Items.IRON_INGOT,Items.REDSTONE,Items.IRON_INGOT,buildcraft.core.BCCoreItems.gearGold,Items.CRAFTING_TABLE,buildcraft.core.BCCoreItems.gearGold,Items.IRON_INGOT,Items.REDSTONE,Items.IRON_INGOT};for(int i=0;i<9;i++)h.getSlot(i+1).setStack(new ItemStack(items[i]));h.onContentChanged(h.getSlot(1).inventory);c.assertTrue(h.getSlot(0).getStack().isOf(BCBuildersBlocks.replacer.asItem()),"Replacer survival recipe missing");c.complete();
    }
}
