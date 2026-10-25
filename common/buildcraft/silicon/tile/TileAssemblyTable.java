/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native assembly, recipe selection and laser work. Remake by FelixMantra.
 */
package buildcraft.silicon.tile;

import java.io.IOException;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import buildcraft.api.core.EnumPipePart;
import buildcraft.lib.misc.*;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.*;
import buildcraft.silicon.*;
import buildcraft.silicon.recipe.NativeAssemblyRecipe;

public class TileAssemblyTable extends TileLaserTableBase {
    public static final IdAllocator IDS=TileBC_Neptune.IDS.makeChild("assembly_table");
    public static final int MAX_SELECTIONS=512;
    public final ItemHandlerSimple inv=itemManager.addInvHandler("inv",12,ItemHandlerManager.EnumAccess.BOTH,EnumPipePart.VALUES);
    public SortedMap<Identifier,EnumAssemblyRecipeState> recipesStates=new TreeMap<>();
    private final Set<Identifier> selected=new HashSet<>();
    private Identifier active,after;
    private NativeAssemblyRecipe recipe;
    private long refreshed=Long.MIN_VALUE;
    private boolean dirty=true,crafting;
    public TileAssemblyTable(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override public IdAllocator getIdAllocator(){return IDS;}
    @Override protected void onSlotChange(Object handler,int slot,ItemStack before,ItemStack after) {
        super.onSlotChange(handler,slot,before,after);dirty=true;
    }
    private NativeAssemblyRecipe find(Identifier id) {
        if(world==null || id==null)return null;
        var found=world.getRecipeManager().get(id).orElse(null);
        return found instanceof NativeAssemblyRecipe assembly?assembly:null;
    }
    public ItemStack preview(Identifier id){var found=find(id);return found==null?ItemStack.EMPTY:found.output();}
    public boolean isSelected(Identifier id){return selected.contains(id);}
    public Identifier getActiveRecipeId(){refresh();return active;}
    public SortedMap<Identifier,EnumAssemblyRecipeState> getChoices(){refresh();return Collections.unmodifiableSortedMap(recipesStates);}
    public boolean selectRecipe(Identifier id,boolean value) {
        if(world==null || world.isClient || !isStorageAvailable())return false;
        var found=find(id);
        if(found==null || value && (!selected.contains(id) && selected.size()>=MAX_SELECTIONS || found.allocate(inv.stacks)==null))return false;
        if(value)selected.add(id);else selected.remove(id);
        dirty=true;markDirty();refresh();return true;
    }
    private void refresh() {
        if(world==null || world.isClient || crafting || !isStorageAvailable()
            || !dirty && refreshed==world.getTime() && (active==null || find(active)==recipe))return;
        dirty=false;refreshed=world.getTime();
        SortedMap<Identifier,EnumAssemblyRecipeState> next=new TreeMap<>();
        SortedMap<Identifier,NativeAssemblyRecipe> ready=new TreeMap<>();
        var available=world.getRecipeManager().listAllOfType(BCSiliconRecipes.ASSEMBLY);
        var known=new HashSet<Identifier>();for(var found:available)known.add(found.getId());
        if(selected.removeIf(id->!known.contains(id)))markDirty();
        for(var found:available) {
            boolean enough=found.allocate(inv.stacks)!=null,chosen=selected.contains(found.getId());
            if(chosen || enough)next.put(found.getId(),chosen?(enough?EnumAssemblyRecipeState.SAVED_ENOUGH:EnumAssemblyRecipeState.SAVED):EnumAssemblyRecipeState.POSSIBLE);
            if(chosen && enough)ready.put(found.getId(),found);
        }
        if(active==null || !ready.containsKey(active)) {
            active=null;
            if(!ready.isEmpty()) {
                active=ready.firstKey();
                if(after!=null)for(var id:ready.keySet())if(id.compareTo(after)>0){active=id;break;}
            }
        }
        recipe=active==null?null:ready.get(active);
        if(active!=null)next.put(active,EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE);
        // Keep queued/active entries visible even with very large data packs.
        if(next.size()>MAX_SELECTIONS) {
            var trim=next.entrySet().iterator();
            while(next.size()>MAX_SELECTIONS && trim.hasNext())if(trim.next().getValue()==EnumAssemblyRecipeState.POSSIBLE)trim.remove();
        }
        if(!next.equals(recipesStates)){recipesStates=next;sendNetworkGuiUpdate(NET_GUI_DATA);}
    }
    @Override public long getTarget() {
        if(world!=null && world.isClient) {
            for(var entry:recipesStates.entrySet())if(entry.getValue()==EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE) {
                var found=find(entry.getKey());return found==null?0:found.cost();
            }
            return 0;
        }
        refresh();return recipe==null?0:recipe.cost();
    }
    @Override public void tick() {
        super.tick();
        if(world==null || world.isClient || !isStorageAvailable())return;
        refresh();
        if(recipe==null || power<recipe.cost())return;
        var current=recipe;int[] used=current.allocate(inv.stacks);if(used==null)return;
        var extracted=new LinkedHashMap<ItemVariant,Long>();
        var remainders=new ArrayList<ItemStack>();
        for(int i=0;i<used.length;i++)if(used[i]>0) {
            var stack=inv.stacks.get(i);
            extracted.merge(ItemVariant.of(stack),(long)used[i],Long::sum);
            if(stack.getItem().hasRecipeRemainder())for(int n=0;n<used[i];n++)remainders.add(new ItemStack(stack.getItem().getRecipeRemainder()));
        }
        crafting=true;
        try {
            try(Transaction tx=Transaction.openOuter()) {
                var storage=inv.asStorage(false,true);
                for(var entry:extracted.entrySet())if(storage.extract(entry.getKey(),entry.getValue(),tx)!=entry.getValue())return;
                tx.commit();
            }
            power-=current.cost();markDirty();
            InventoryUtil.addToBestAcceptor(world,pos,null,current.output());
            for(var remainder:remainders)InventoryUtil.addToBestAcceptor(world,pos,null,remainder);
            AdvancementUtil.unlockAdvancement((ServerWorld)world,getOwner().getId(),new Identifier("buildcraftsilicon","laser_power"));
            after=current.getId();active=null;
        } finally {crafting=false;dirty=true;}
        refresh();
    }
    @Override public boolean onActivated(PlayerEntity player,Hand hand,Direction side,float x,float y,float z) {
        if(!canInteractWith(player) || player.getStackInHand(hand).getItem() instanceof buildcraft.api.tools.IToolWrench)return false;
        if(!world.isClient)BCSiliconScreens.openAssembly(player,this);
        return true;
    }
    @Override public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);var list=new NbtList();selected.stream().sorted().forEach(id->list.add(NbtString.of(id.toString())));nbt.put("assembly_selected",list);
        if(active!=null)nbt.putString("assembly_active",active.toString());
        if(after!=null)nbt.putString("assembly_after",after.toString());
    }
    @Override public void readFromNBT(NbtCompound nbt) {
        super.readFromNBT(nbt);selected.clear();
        var list=nbt.getList("assembly_selected",NbtElement.STRING_TYPE);
        for(int i=0;i<Math.min(MAX_SELECTIONS,list.size());i++){var id=Identifier.tryParse(list.getString(i));if(id!=null)selected.add(id);}
        active=Identifier.tryParse(nbt.getString("assembly_active"));after=Identifier.tryParse(nbt.getString("assembly_after"));
        // Preserve compatible selections from the original assembly table format.
        if(selected.isEmpty()) {
            var legacy=nbt.getList("recipes_states",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<Math.min(MAX_SELECTIONS,legacy.size());i++) {
                var entry=legacy.getCompound(i);var id=Identifier.tryParse(entry.getString("recipe"));
                if(id!=null && entry.getInt("state")>0)selected.add(id);
            }
        }
        recipe=null;dirty=true;refreshed=Long.MIN_VALUE;
    }
    @Override public void writePayload(int id,PacketBufferBC buffer,NetSide side) {
        super.writePayload(id,buffer,side);
        if(side==NetSide.SERVER && id==NET_GUI_DATA) {
            refresh();buffer.writeVarInt(Math.min(MAX_SELECTIONS,recipesStates.size()));
            int count=0;for(var entry:recipesStates.entrySet()) {
                if(count++>=MAX_SELECTIONS)break;buffer.writeIdentifier(entry.getKey());buffer.writeByte(entry.getValue().ordinal());
            }
        }
    }
    @Override public void readPayload(int id,PacketBufferBC buffer,NetSide side,Object context) throws IOException {
        super.readPayload(id,buffer,side,context);
        if(side==NetSide.CLIENT && id==NET_GUI_DATA) {
            int count=buffer.readVarInt();if(count<0 || count>MAX_SELECTIONS)throw new IOException("Invalid assembly choice count");
            SortedMap<Identifier,EnumAssemblyRecipeState> next=new TreeMap<>();int activeCount=0;
            for(int i=0;i<count;i++) {
                var key=buffer.readIdentifier();int value=buffer.readUnsignedByte();
                if(value>=EnumAssemblyRecipeState.values().length || next.containsKey(key))throw new IOException("Invalid assembly choice");
                var state=EnumAssemblyRecipeState.values()[value];if(state==EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE)activeCount++;
                next.put(key,state);
            }
            if(activeCount>1)throw new IOException("Multiple active assembly recipes");
            recipesStates=next;
        }
    }
}
