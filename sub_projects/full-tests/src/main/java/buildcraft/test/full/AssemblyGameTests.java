/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.test.full;

import java.util.*;
import buildcraft.api.enums.EnumRedstoneChipset;
import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.registry.BlockEntityRegistry;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.silicon.*;
import buildcraft.silicon.container.ContainerAssemblyTable;
import buildcraft.silicon.gate.*;
import buildcraft.silicon.item.ItemPluggableGate;
import buildcraft.silicon.recipe.*;
import buildcraft.silicon.tile.*;
import buildcraft.transport.*;
import buildcraft.transport.tile.TilePipeHolder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.*;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.recipe.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

public final class AssemblyGameTests implements FabricGameTest {
    private static final BlockPos POS=new BlockPos(3,3,3);
    private static Identifier id(String name){return new Identifier("buildcraftsilicon","assembly/"+name);}
    private static NativeAssemblyRecipe recipe(TestContext c,String name){return (NativeAssemblyRecipe)c.getWorld().getRecipeManager().get(id(name)).orElseThrow();}
    private static TileAssemblyTable table(TestContext c,BlockPos pos) {
        c.setBlockState(pos,BCSiliconBlocks.assemblyTable);var tile=(TileAssemblyTable)c.getBlockEntity(pos);tile.onLoad();
        tile.onPlacedBy(c.createMockCreativeServerPlayerInWorld(),new ItemStack(BCSiliconBlocks.assemblyTable));return tile;
    }
    private static TileAssemblyTable table(TestContext c){return table(c,POS);}
    private static void red(TileAssemblyTable tile,int count){tile.inv.setStackInSlot(0,new ItemStack(Items.REDSTONE,count));tile.selectRecipe(id("red_chipset"),true);}
    private static IMjReceiver receiver(TileLaser laser){return (IMjReceiver)MjAPI.CONNECTOR.find(laser.getWorld(),laser.getPos(),Direction.UP);}
    private static TileLaser laser(TestContext c,BlockPos pos,Direction face) {
        c.setBlockState(pos,BCSiliconBlocks.laser.getDefaultState().with(BuildCraftProperties.BLOCK_FACING_6,face));
        var laser=(TileLaser)c.getBlockEntity(pos);laser.onLoad();return laser;
    }
    private static int count(BarrelBlockEntity barrel,Item item){int n=0;for(int i=0;i<barrel.size();i++)if(barrel.getStack(i).isOf(item))n+=barrel.getStack(i).getCount();return n;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allNativeAssemblyRecipesRoundTripAndProduceExactOutput(TestContext c) {
        var recipes=c.getWorld().getRecipeManager().listAllOfType(BCSiliconRecipes.ASSEMBLY).stream().filter(r->!r.getId().getPath().startsWith("assembly/facades/")).toList();
        c.assertTrue(recipes.size()==83,"Expected five chipsets, 24 gates, sixteen wires, three controls, 34 lenses/filters and the gate copier");
        var tile=table(c);c.setBlockState(POS.down(),Blocks.BARREL);var barrel=(BarrelBlockEntity)c.getBlockEntity(POS.down());
        for(var original:recipes) {
            var packet=new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
            NativeAssemblyRecipe copy;
            try {BCSiliconRecipes.ASSEMBLY_SERIALIZER.write(packet,original);copy=BCSiliconRecipes.ASSEMBLY_SERIALIZER.read(original.getId(),packet);}
            finally {packet.release();}
            c.assertTrue(copy.cost()==original.cost() && ItemStack.areEqual(copy.output(),original.output()),"Recipe synchronization lost NBT, count or MJ");
            for(var old:List.copyOf(tile.getChoices().keySet()))tile.selectRecipe(old,false);
            for(int i=0;i<12;i++)tile.inv.setStackInSlot(i,ItemStack.EMPTY);barrel.clear();
            for(int i=0;i<copy.inputs().size();i++){var input=copy.inputs().get(i);var stack=input.ingredient().getMatchingStacks()[0].copyWithCount(input.count());stack.setNbt(input.nbt());tile.inv.setStackInSlot(i,stack);}
            c.assertTrue(tile.selectRecipe(copy.getId(),true),"Valid native assembly recipe not selectable: "+copy.getId());
            c.assertTrue(tile.receiveLaserPower(copy.cost()+1)==1,"Assembly overcharged exact MJ cost");tile.tick();
            c.assertTrue(ItemStack.areEqual(barrel.getStack(0),copy.output()) && tile.inv.stacks.stream().allMatch(ItemStack::isEmpty) && tile.power==0,"Assembly lost or duplicated ingredients/output: "+copy.getId());
        }
        long[] costs={10000,20000,40000,60000,80000};int i=0;
        for(var type:EnumRedstoneChipset.values()){var result=recipe(c,type.name().toLowerCase()+"_chipset");c.assertTrue(result.cost()==costs[i++]*MjAPI.MJ && EnumRedstoneChipset.fromStack(result.output())==type,"Original chipset cost/variant changed");}
        for(var colour:DyeColor.values())c.assertTrue(recipe(c,"wire_"+colour.getName()).output().getCount()==8 && recipe(c,"wire_"+colour.getName()).output().getNbt().getInt("wire_colour")==colour.getId(),"Wire colour/count changed");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void overlappingIngredientsSplitStacksAndRejectShortage(TestContext c) {
        var broad=new NativeAssemblyRecipe.Input(Ingredient.ofItems(Items.OAK_PLANKS,Items.BIRCH_PLANKS),3,null);
        var exact=new NativeAssemblyRecipe.Input(Ingredient.ofItems(Items.OAK_PLANKS),2,null);
        var recipe=new NativeAssemblyRecipe(id("test_overlap"),List.of(broad,exact),new ItemStack(Items.STICK),MjAPI.MJ);
        var inventory=List.of(new ItemStack(Items.OAK_PLANKS,2),new ItemStack(Items.BIRCH_PLANKS,1),new ItemStack(Items.BIRCH_PLANKS,2));
        c.assertTrue(Arrays.equals(recipe.allocate(inventory),new int[]{2,1,2}),"Broad ingredient consumed the only exact ingredient");
        c.assertTrue(recipe.allocate(List.of(new ItemStack(Items.OAK_PLANKS,2),new ItemStack(Items.BIRCH_PLANKS,2)))==null,"Insufficient ingredients were counted twice");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dataPackReplacementChangesOutputAndRemovalStopsPaidRecipe(TestContext c) {
        var tile=table(c);red(tile,2);tile.receiveLaserPower(2500*MjAPI.MJ);
        var manager=c.getWorld().getRecipeManager();var saved=new ArrayList<Recipe<?>>(manager.values());
        try {
            var replacement=new ArrayList<Recipe<?>>(saved.stream().filter(r->!r.getId().equals(id("red_chipset"))).toList());
            replacement.add(new NativeAssemblyRecipe(id("red_chipset"),List.of(new NativeAssemblyRecipe.Input(Ingredient.ofItems(Items.REDSTONE),1,null)),new ItemStack(Items.DIAMOND),3000*MjAPI.MJ));
            manager.setRecipes(replacement);c.setBlockState(POS.down(),Blocks.BARREL);
            c.assertTrue(tile.getTarget()==3000*MjAPI.MJ && tile.power==2500*MjAPI.MJ,"Reload reused old recipe or lost generic MJ");tile.receiveLaserPower(500*MjAPI.MJ);tile.tick();
            c.assertTrue(count((BarrelBlockEntity)c.getBlockEntity(POS.down()),Items.DIAMOND)==1,"Replaced recipe used stale output");
            manager.setRecipes(replacement.stream().filter(r->!r.getId().equals(id("red_chipset"))).toList());tile.tick();
            c.assertTrue(tile.getTarget()==0 && tile.inv.getStackInSlot(0).getCount()==1 && !tile.isSelected(id("red_chipset")),"Removed recipe crafted or blocked selection capacity");
        }finally{manager.setRecipes(saved);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void countedContainerIngredientsReturnEveryNativeRemainder(TestContext c) {
        var manager=c.getWorld().getRecipeManager();var saved=new ArrayList<Recipe<?>>(manager.values());var all=new ArrayList<Recipe<?>>(saved);
        var r=new NativeAssemblyRecipe(id("milk_test"),List.of(new NativeAssemblyRecipe.Input(Ingredient.ofItems(Items.MILK_BUCKET),2,null)),new ItemStack(Items.CAKE),MjAPI.MJ);all.add(r);
        try {
            manager.setRecipes(all);var tile=table(c);c.setBlockState(POS.down(),Blocks.BARREL);
            tile.inv.setStackInSlot(0,new ItemStack(Items.MILK_BUCKET));tile.inv.setStackInSlot(1,new ItemStack(Items.MILK_BUCKET));tile.selectRecipe(r.getId(),true);tile.receiveLaserPower(MjAPI.MJ);tile.tick();
            var barrel=(BarrelBlockEntity)c.getBlockEntity(POS.down());c.assertTrue(count(barrel,Items.CAKE)==1 && count(barrel,Items.BUCKET)==2 && tile.inv.stacks.stream().allMatch(ItemStack::isEmpty),"Counted ingredient remainders lost or duplicated");
        }finally{manager.setRecipes(saved);}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void laserReloadKeepsBatteryAndTruncatedRenderPacketCannotMutateIt(TestContext c) throws Exception {
        var tile=table(c);red(tile,1);var original=laser(c,POS.west(2),Direction.EAST);receiver(original).receivePower(100*MjAPI.MJ,false);original.tick();long stored=original.getStoredPower();
        var copy=BlockEntityRegistry.create(TileLaser.class,original.getPos(),original.getCachedState());copy.readNbt(original.createNbt());
        c.getWorld().removeBlockEntity(original.getPos());c.getWorld().addBlockEntity(copy);copy.onLoad();
        c.assertTrue(copy.getStoredPower()==stored && copy.getTargetPos()==null,"Laser persisted stale target or lost MJ");copy.tick();c.assertTrue(copy.getLastTransfer()>0,"Reloaded laser did not reacquire work");
        var buffer=new PacketBufferBC(io.netty.buffer.Unpooled.buffer());try {
            copy.writePayload(TileBC_Neptune.NET_RENDER_DATA,buffer,TileBC_Neptune.NetSide.SERVER);buffer.writerIndex(buffer.writerIndex()-1);long before=copy.getStoredPower();boolean failed=false;
            try{copy.readPayload(TileBC_Neptune.NET_RENDER_DATA,buffer,TileBC_Neptune.NetSide.CLIENT,null);}catch(IndexOutOfBoundsException expected){failed=true;}
            c.assertTrue(failed && copy.getStoredPower()==before,"Truncated beam packet partially mutated battery");
        }finally{buffer.release();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void exactNbtGateUpgradeNeverConsumesWrongChipset(TestContext c) {
        var r=recipe(c,"gate_iron_and_quartz");var gate=BCSiliconItems.plugGate.getStack(new GateVariant(EnumGateLogic.AND,EnumGateMaterial.IRON,EnumGateModifier.NO_MODIFIER));
        c.assertTrue(r.allocate(List.of(gate,EnumRedstoneChipset.DIAMOND.getStack()))==null,"Wrong chipset satisfied exact quartz ingredient");
        c.assertTrue(r.allocate(List.of(gate,EnumRedstoneChipset.QUARTZ.getStack()))!=null,"Correct quartz chipset rejected");
        gate.getOrCreateNbt().putString("extra","preserve");c.assertTrue(r.allocate(List.of(gate,EnumRedstoneChipset.QUARTZ.getStack()))==null,"Exact NBT silently discarded extra gate data");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void selectedRecipesRoundRobinAndRememberMissingIngredients(TestContext c) {
        var tile=table(c);red(tile,3);tile.inv.setStackInSlot(1,new ItemStack(Items.IRON_INGOT));tile.selectRecipe(id("iron_chipset"),true);
        tile.receiveLaserPower(tile.getTarget());tile.tick();
        c.assertTrue(id("iron_chipset").equals(tile.getActiveRecipeId()),"Ready recipe queue did not advance");
        tile.receiveLaserPower(tile.getTarget());tile.tick();
        c.assertTrue(tile.isSelected(id("iron_chipset")) && tile.getChoices().get(id("iron_chipset"))==EnumAssemblyRecipeState.SAVED
            && id("red_chipset").equals(tile.getActiveRecipeId()),"Missing ingredient removed saved selection or blocked another ready recipe");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void deselectionAndMissingInputPreserveGenericPaidWorkWithoutOverflow(TestContext c) {
        var tile=table(c);red(tile,1);tile.receiveLaserPower(2500*MjAPI.MJ);tile.selectRecipe(id("red_chipset"),false);
        c.assertTrue(tile.getRequiredLaserPower()==0 && tile.receiveLaserPower(MjAPI.MJ)==MjAPI.MJ && tile.power==2500*MjAPI.MJ,"Deselection lost paid work or accepted unused MJ");
        tile.selectRecipe(id("red_chipset"),true);tile.inv.setStackInSlot(0,ItemStack.EMPTY);tile.tick();
        c.assertTrue(!tile.hasWork() && tile.power==2500*MjAPI.MJ,"Missing input wasted MJ");
        red(tile,1);c.assertTrue(tile.receiveLaserPower(Long.MAX_VALUE)==Long.MAX_VALUE-7500*MjAPI.MJ && tile.getRequiredLaserPower()==0,"MJ overflow or wrong remaining cost");
        tile.tick();c.assertTrue(tile.power==0,"Craft charged incorrect energy");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void partialWorkAndSelectionSurviveNbtBeforeWorldAttachment(TestContext c) {
        var old=table(c);red(old,2);old.receiveLaserPower(4321*MjAPI.MJ);var storage=ItemStorage.SIDED.find(c.getWorld(),old.getPos(),Direction.UP);
        var copy=BlockEntityRegistry.create(TileAssemblyTable.class,old.getPos(),old.getCachedState());copy.readNbt(old.createNbt());
        c.getWorld().removeBlockEntity(old.getPos());c.getWorld().addBlockEntity(copy);copy.onLoad();
        c.assertTrue(copy.power==4321*MjAPI.MJ && copy.isSelected(id("red_chipset")) && copy.getRequiredLaserPower()==5679*MjAPI.MJ,"Saved work/selection lost before inputs loaded");
        c.assertTrue(old.receiveLaserPower(1)==1,"Stale table accepted laser work");
        try(var tx=Transaction.openOuter()){c.assertTrue(storage.insert(ItemVariant.of(Items.REDSTONE),1,tx)==0,"Stale table accepted materials");}
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allNativeInventoryFacesRollbackAndRemovalDropsExactlyOnce(TestContext c) {
        var tile=table(c);
        for(var face:Direction.values())try(var tx=Transaction.openOuter()) {
            var storage=ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),face);c.assertTrue(storage!=null && storage.insert(ItemVariant.of(Items.REDSTONE),5,tx)==5,"Input face unavailable");
        }
        c.assertTrue(tile.inv.stacks.stream().allMatch(ItemStack::isEmpty),"Aborted insertion leaked");red(tile,5);
        for(var face:Direction.values())try(var tx=Transaction.openOuter()){c.assertTrue(ItemStorage.SIDED.find(c.getWorld(),tile.getPos(),face).extract(ItemVariant.of(Items.REDSTONE),2,tx)==2,"Native extraction unavailable");}
        c.getWorld().breakBlock(tile.getPos(),false);tile.tick();
        long red=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->e.getStack().isOf(Items.REDSTONE)).stream().mapToInt(e->e.getStack().getCount()).sum();
        c.assertTrue(red==5,"Assembly removal duplicated or lost materials");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=160)
    public void craftedItemsPreferNativePipeAndArriveAtBarrel(TestContext c) {
        var tile=table(c);red(tile,1);c.setBlockState(POS.down(),Blocks.BARREL);
        for(int i=1;i<=2;i++){c.setBlockState(POS.east(i),BCTransportBlocks.pipeHolder);((TilePipeHolder)c.getBlockEntity(POS.east(i))).onPlacedBy(null,new ItemStack(BCTransportItems.pipeItemStone));}
        c.setBlockState(POS.east(3),Blocks.BARREL);
        for(int i=2;i>=1;i--)((TilePipeHolder)c.getBlockEntity(POS.east(i))).tick();
        tile.receiveLaserPower(tile.getTarget());tile.tick();
        c.assertTrue(count((BarrelBlockEntity)c.getBlockEntity(POS.down()),BCSiliconItems.redstoneChipset)==0,"Inventory won over native pipe output");
        c.waitAndRun(90,()->{c.assertTrue(count((BarrelBlockEntity)c.getBlockEntity(POS.east(3)),BCSiliconItems.redstoneChipset)==1,"Pipe lost assembled chipset");c.complete();});
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void fullInventorySpillsExactlyOneProductAndUnlocksAdvancement(TestContext c) {
        var tile=table(c);red(tile,1);c.setBlockState(POS.down(),Blocks.BARREL);var barrel=(BarrelBlockEntity)c.getBlockEntity(POS.down());for(int i=0;i<barrel.size();i++)barrel.setStack(i,new ItemStack(Items.STONE,64));
        tile.receiveLaserPower(tile.getTarget());tile.tick();tile.tick();
        long drops=c.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(tile.getPos()).expand(1),e->e.getStack().isOf(BCSiliconItems.redstoneChipset)).stream().mapToInt(e->e.getStack().getCount()).sum();
        var player=c.getWorld().getServer().getPlayerManager().getPlayer(tile.getOwner().getId());var adv=c.getWorld().getServer().getAdvancementLoader().get(new Identifier("buildcraftsilicon:laser_power"));
        c.assertTrue(drops==1 && player.getAdvancementTracker().getProgress(adv).isDone(),"Output spill/owner advancement failed");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sixLaserFacingsDeliverConservedMj(TestContext c) {
        var tile=table(c);red(tile,1);
        for(var face:Direction.values()) {
            var laser=laser(c,POS.offset(face.getOpposite(),2),face);receiver(laser).receivePower(TileLaser.CAPACITY,false);long before=tile.power;laser.tick();
            c.assertTrue(laser.getLastTransfer()==4*MjAPI.MJ && tile.power-before==4*MjAPI.MJ && laser.getStoredPower()==TileLaser.CAPACITY-4*MjAPI.MJ,"Wrong facing, rate or energy conservation: "+face);
            c.getWorld().removeBlock(laser.getPos(),false);
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void laserConeUsesSixBlockRangeAndStrictDepthWidth(TestContext c) {
        var laser=laser(c,new BlockPos(0,3,3),Direction.EAST);receiver(laser).receivePower(TileLaser.CAPACITY,false);
        var beyond=table(c,new BlockPos(7,3,3));red(beyond,1);laser.tick();c.assertTrue(laser.getLastTransfer()==0,"Laser exceeded six-block range");
        c.getWorld().removeBlock(beyond.getPos(),false);var edge=table(c,new BlockPos(2,3,5));red(edge,1);laser.onLoad();laser.tick();c.assertTrue(laser.getLastTransfer()==0,"Laser exceeded cone width");
        c.getWorld().removeBlock(edge.getPos(),false);var within=table(c,new BlockPos(6,3,3));red(within,1);laser.onLoad();laser.tick();c.assertTrue(laser.getLastTransfer()>0,"Six-block target rejected");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void transparentObstructionRotationAndRemovedTargetStopImmediately(TestContext c) {
        var tile=table(c);red(tile,1);var laser=laser(c,POS.west(2),Direction.EAST);receiver(laser).receivePower(TileLaser.CAPACITY,false);laser.tick();long before=tile.power;
        c.setBlockState(POS.west(),Blocks.GLASS);laser.tick();c.assertTrue(laser.getLastTransfer()==0 && tile.power==before,"Laser passed through a glass obstruction");
        c.setBlockState(POS.west(),Blocks.AIR);laser.onLoad();laser.tick();c.assertTrue(tile.power>before,"Unblocked laser failed to resume");
        c.getWorld().setBlockState(laser.getPos(),laser.getCachedState().with(BuildCraftProperties.BLOCK_FACING_6,Direction.WEST));laser.tick();c.assertTrue(laser.getLastTransfer()==0,"Rotated laser kept old target");
        c.getWorld().setBlockState(laser.getPos(),laser.getCachedState().with(BuildCraftProperties.BLOCK_FACING_6,Direction.EAST));laser.onLoad();laser.tick();
        c.getWorld().removeBlock(tile.getPos(),false);laser.tick();c.assertTrue(laser.getLastTransfer()==0,"Removed target still received MJ");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void laserBatterySimulationRampingRemainderAndStaleHandle(TestContext c) {
        var tile=table(c);red(tile,1);var laser=laser(c,POS.west(2),Direction.EAST);var receiver=receiver(laser);
        c.assertTrue(receiver.receivePower(Long.MAX_VALUE,true)==Long.MAX_VALUE-TileLaser.CAPACITY && laser.getStoredPower()==0,"Laser simulation modified battery");
        receiver.receivePower(10*MjAPI.MJ,false);laser.tick();
        c.assertTrue(laser.getLastTransfer()>0 && laser.getLastTransfer()<4*MjAPI.MJ && tile.power+laser.getStoredPower()==10*MjAPI.MJ,"Low battery ramp or conservation broken");
        receiver.receivePower(Long.MAX_VALUE,false);tile.receiveLaserPower(tile.getRequiredLaserPower()-123);laser.tick();
        c.assertTrue(laser.getLastTransfer()==123 && laser.getStoredPower()==TileLaser.CAPACITY-123 && tile.getRequiredLaserPower()==0,"Laser lost exact final fractional MJ remainder");
        c.getWorld().removeBlock(laser.getPos(),false);c.assertTrue(receiver.receivePower(99,false)==99 && receiver.getPowerRequested()==0,"Stale laser connector accepted MJ");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void maliciousNbtIsBoundedAndNegativeEnergyRejected(TestContext c) {
        var tile=table(c);var nbt=tile.createNbt();nbt.putLong("power",Long.MAX_VALUE);tile.readNbt(nbt);c.assertTrue(tile.power==NativeAssemblyRecipe.MAX_COST,"Unbounded table NBT");
        var laser=laser(c,POS.west(2),Direction.EAST);var data=laser.createNbt();data.getCompound("battery").putLong("stored",Long.MAX_VALUE);laser.readNbt(data);c.assertTrue(laser.getStoredPower()==TileLaser.CAPACITY,"Unbounded laser NBT");
        boolean rejected=false;try{receiver(laser).receivePower(-1,false);}catch(IllegalArgumentException expected){rejected=true;}c.assertTrue(rejected,"Negative MJ accepted");
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void tablePowerPacketTruncationAndInvalidValuesAreAtomic(TestContext c) throws Exception {
        var tile=table(c);red(tile,1);tile.receiveLaserPower(123);var buffer=new PacketBufferBC(io.netty.buffer.Unpooled.buffer());
        try {buffer.writeLong(999);boolean rejected=false;try{tile.readPayload(TileBC_Neptune.NET_GUI_TICK,buffer,TileBC_Neptune.NetSide.CLIENT,null);}catch(IndexOutOfBoundsException expected){rejected=true;}c.assertTrue(rejected && tile.power==123,"Truncated payload partially changed energy");
            buffer.clear();buffer.writeLong(-1);buffer.writeLong(0);rejected=false;try{tile.readPayload(TileBC_Neptune.NET_GUI_TICK,buffer,TileBC_Neptune.NetSide.CLIENT,null);}catch(java.io.IOException expected){rejected=true;}c.assertTrue(rejected && tile.power==123,"Invalid payload changed energy");
        }finally{buffer.release();}c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void nativeContainerShiftClickAndForgedSelectionsCannotStealPreviews(TestContext c) throws Exception {
        var tile=table(c);var player=c.createMockCreativeServerPlayerInWorld();var center=Vec3d.ofCenter(tile.getPos());player.refreshPositionAndAngles(center.x,center.y,center.z-2,0,0);
        player.getInventory().setStack(9,new ItemStack(Items.REDSTONE,3));var menu=new ContainerAssemblyTable(player,1,tile);
        c.assertTrue(menu.slots.size()==48,"Recipe previews became stealable inventory slots");menu.onSlotClick(12,0,SlotActionType.QUICK_MOVE,player);
        c.assertTrue(tile.inv.getStackInSlot(0).getCount()==3 && player.getInventory().getStack(9).isEmpty(),"Native shift-click lost input");
        var packet=new PacketBufferBC(io.netty.buffer.Unpooled.buffer());try{packet.writeIdentifier(id("diamond_chipset"));packet.writeBoolean(true);menu.readMessage(ContainerAssemblyTable.NET_SELECT,packet,false,null);}finally{packet.release();}
        c.assertTrue(!tile.isSelected(id("diamond_chipset")),"Forged missing-material selection accepted");
        player.refreshPositionAndAngles(center.x+100,center.y,center.z,0,0);packet=new PacketBufferBC(io.netty.buffer.Unpooled.buffer());try{packet.writeIdentifier(id("red_chipset"));packet.writeBoolean(true);menu.readMessage(ContainerAssemblyTable.NET_SELECT,packet,false,null);}finally{packet.release();}
        c.assertTrue(!tile.isSelected(id("red_chipset")),"Distant screen changed selections");c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void ordinaryCraftingMakesBasicGatesAndIronUpgrades(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();var menu=new CraftingScreenHandler(1,player.getInventory(),ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        Item[] materials={Items.BRICK,Items.IRON_INGOT,Items.NETHER_BRICK};EnumGateMaterial[] variants={EnumGateMaterial.CLAY_BRICK,EnumGateMaterial.IRON,EnumGateMaterial.NETHER_BRICK};
        for(int k=0;k<materials.length;k++) {
            for(int i=1;i<=9;i++)menu.getSlot(i).setStack(ItemStack.EMPTY);
            for(int slot:new int[]{2,4,6})menu.getSlot(slot).setStack(new ItemStack(materials[k]));menu.getSlot(5).setStack(new ItemStack(Items.REDSTONE));menu.getSlot(8).setStack(new ItemStack(Items.COBBLESTONE));menu.onContentChanged(menu.getSlot(1).inventory);
            c.assertTrue(menu.getSlot(0).getStack().isOf(BCSiliconItems.plugGate) && ItemPluggableGate.getVariant(menu.getSlot(0).getStack()).material==variants[k],"Basic gate native craft missing");
            menu.onSlotClick(0,0,SlotActionType.PICKUP,player);for(int i=1;i<=9;i++)c.assertTrue(menu.getSlot(i).getStack().isEmpty(),"Gate craft did not consume exact materials");menu.setCursorStack(ItemStack.EMPTY);
        }
        for(int k=0;k<2;k++) {
            for(int slot:new int[]{2,4,6,8})menu.getSlot(slot).setStack(new ItemStack(k==0?Items.LAPIS_LAZULI:Items.QUARTZ));menu.getSlot(5).setStack(BCSiliconItems.plugGate.getStack(new GateVariant(EnumGateLogic.AND,EnumGateMaterial.IRON,EnumGateModifier.NO_MODIFIER)));menu.onContentChanged(menu.getSlot(1).inventory);
            c.assertTrue(ItemPluggableGate.getVariant(menu.getSlot(0).getStack()).modifier==(k==0?EnumGateModifier.LAPIS:EnumGateModifier.QUARTZ),"Iron gate native upgrade missing");
            menu.onSlotClick(0,0,SlotActionType.PICKUP,player);menu.setCursorStack(ItemStack.EMPTY);
        }
        c.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void allTwentyFourLogicFlipsPreserveNamesAndRejectExtraIngredients(TestContext c) {
        var player=c.createMockCreativeServerPlayerInWorld();var menu=new CraftingScreenHandler(1,player.getInventory(),ScreenHandlerContext.create(c.getWorld(),c.getAbsolutePos(POS)));
        for(var variant:ItemPluggableGate.variants()) {
            if(!variant.material.canBeModified)continue;
            var stack=BCSiliconItems.plugGate.getStack(variant);stack.setCustomName(net.minecraft.text.Text.literal("Factory control"));menu.getSlot(1).setStack(stack);menu.onContentChanged(menu.getSlot(1).inventory);
            var output=menu.getSlot(0).getStack();var flipped=ItemPluggableGate.getVariant(output);
            c.assertTrue(output.isOf(BCSiliconItems.plugGate) && flipped.logic!=variant.logic && flipped.material==variant.material && flipped.modifier==variant.modifier && output.getName().getString().equals("Factory control"),"Gate flip lost properties");
            menu.getSlot(2).setStack(new ItemStack(Items.STICK));menu.onContentChanged(menu.getSlot(1).inventory);c.assertTrue(menu.getSlot(0).getStack().isEmpty(),"Extra input accepted by flip recipe");menu.getSlot(2).setStack(ItemStack.EMPTY);
        }
        c.complete();
    }
}
