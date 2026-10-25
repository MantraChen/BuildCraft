/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0
 * Native integration and atomic laser work. Remake by FelixMantra.
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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.*;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.recipes.*;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.recipe.IngredientAllocation;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.*;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.silicon.BCSiliconRecipes;
import buildcraft.silicon.recipe.NativeAssemblyRecipe;

/** Automatic central-item recipes from data packs or the public Java recipe registry. */
public final class TileIntegrationTable extends TileLaserTableBase {
    public static final IdAllocator IDS=TileBC_Neptune.IDS.makeChild("integrationTable");
    // Outputs are offered first to extraction; the original inputs remain bidirectional.
    public final ItemHandlerSimple invResult=itemManager.addInvHandler("result",1,EnumAccess.EXTRACT,EnumPipePart.VALUES);
    public final ItemHandlerSimple invTarget=itemManager.addInvHandler("target",1,EnumAccess.BOTH,EnumPipePart.VALUES);
    public final ItemHandlerSimple invToIntegrate=itemManager.addInvHandler("toIntegrate",8,EnumAccess.BOTH,EnumPipePart.VALUES);
    private Identifier preferred;
    public long costClient;
    public int statusClient;
    private record Plan(Identifier id,int centerCount,int[] components,ItemStack output,long cost) {}

    public TileIntegrationTable(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    @Override public IdAllocator getIdAllocator(){return IDS;}
    private static DefaultedList<ItemStack> copies(List<ItemStack> stacks) {
        var result=DefaultedList.<ItemStack>of();for(var stack:stacks)result.add(stack.copy());return result;
    }
    private List<IntegrationRecipe> apiRecipes() {
        var registry=BuildcraftRecipeRegistry.integrationRecipes;
        var result=new ArrayList<IntegrationRecipe>();
        if(registry!=null)for(var recipe:registry.getAllRecipes())if(recipe!=null && recipe.name!=null)result.add(recipe);
        result.sort(Comparator.comparing(recipe->recipe.name.toString()));return result;
    }
    public boolean acceptsCenter(ItemStack stack) {
        if(world==null)return false;
        for(var recipe:world.getRecipeManager().listAllOfType(BCSiliconRecipes.INTEGRATION))if(recipe.acceptsCenter(stack))return true;
        for(var recipe:apiRecipes()) {
            var center=recipe.getCenterStack();if(center!=null && center.ingredient.test(stack))return true;
        }
        return false;
    }
    private Plan plan() {
        if(!isStorageAvailable() || world.isClient)return null;
        ItemStack center=invTarget.getStackInSlot(0);
        if(center.isEmpty())return null;
        var nativeRecipes=world.getRecipeManager().listAllOfType(BCSiliconRecipes.INTEGRATION).stream()
            .sorted(Comparator.comparing((buildcraft.silicon.recipe.NativeIntegrationRecipe r)->!r.getId().equals(preferred))
                .thenComparing(r->r.getId().toString())).toList();
        for(var recipe:nativeRecipes) {
            int[] used=recipe.allocate(center,invToIntegrate.stacks);
            if(used!=null)return new Plan(recipe.getId(),recipe.center().count(),used,recipe.result(center),recipe.cost());
        }
        // Add-ons receive copies, including for output-dependent costs and requirements.
        for(var recipe:apiRecipes()) {
            var target=recipe.getCenterStack();
            if(target==null || target.count>64 || center.getCount()<target.count || !target.ingredient.test(center.copy()))continue;
            var output=recipe.getOutput(center.copy(),copies(invToIntegrate.stacks));
            if(output==null || output.isEmpty() || output.getCount()>output.getMaxCount())continue;
            output=output.copy();
            long cost=recipe.getRequiredMicroJoules(output.copy());
            var requirements=recipe.getRequirements(output.copy());
            if(cost<1 || cost>NativeAssemblyRecipe.MAX_COST || requirements==null || requirements.size()>8
                || requirements.stream().anyMatch(r->r==null || r.count>4096))continue;
            var needs=requirements.stream().map(r->new IngredientAllocation.Requirement(r.ingredient::test,r.count)).toList();
            int[] used=IngredientAllocation.allocate(needs,copies(invToIntegrate.stacks));
            if(used!=null)return new Plan(recipe.name,target.count,used,output,cost);
        }
        return null;
    }
    private boolean room(Plan plan) {
        if(plan==null)return false;
        try(var tx=Transaction.openOuter()) {
            return invResult.asStorage(true,false).insert(ItemVariant.of(plan.output),plan.output.getCount(),tx)==plan.output.getCount();
        }
    }
    public ItemStack getRecipePreview(){var plan=plan();return plan==null?ItemStack.EMPTY:plan.output.copy();}
    public Identifier getRecipeId(){var plan=plan();return plan==null?null:plan.id;}
    public long getRecipeCost(){if(world!=null && world.isClient)return costClient;var plan=plan();return plan==null?0:plan.cost;}
    /** 0: no matching recipe, 1: collecting work, 2: ready, 3: output blocked. */
    public int getStatus(){if(world!=null && world.isClient)return statusClient;var plan=plan();return plan==null?0:!room(plan)?3:power>=plan.cost?2:1;}
    @Override public long getTarget(){if(world!=null && world.isClient)return statusClient==1 || statusClient==2?costClient:0;var plan=plan();return room(plan)?plan.cost:0;}
    private static void remainders(List<ItemStack> result,ItemStack input,int count) {
        if(input.getItem().hasRecipeRemainder())for(int i=0;i<count;i++)result.add(new ItemStack(input.getItem().getRecipeRemainder()));
    }
    @Override public void tick() {
        super.tick();
        var plan=plan();if(plan==null)return;
        preferred=plan.id;
        if(power<plan.cost || !room(plan))return;
        var remainder=new ArrayList<ItemStack>();
        var center=invTarget.getStackInSlot(0);remainders(remainder,center,plan.centerCount);
        var components=new LinkedHashMap<ItemVariant,Long>();
        for(int i=0;i<plan.components.length;i++)if(plan.components[i]>0) {
            var stack=invToIntegrate.getStackInSlot(i);
            components.merge(ItemVariant.of(stack),(long)plan.components[i],Long::sum);
            remainders(remainder,stack,plan.components[i]);
        }
        try(var tx=Transaction.openOuter()) {
            if(invResult.asStorage(true,false).insert(ItemVariant.of(plan.output),plan.output.getCount(),tx)!=plan.output.getCount())return;
            if(invTarget.asStorage(false,true).extract(ItemVariant.of(center),plan.centerCount,tx)!=plan.centerCount)return;
            var storage=invToIntegrate.asStorage(false,true);
            for(var entry:components.entrySet())if(storage.extract(entry.getKey(),entry.getValue(),tx)!=entry.getValue())return;
            tx.commit();
        }
        // Charge the captured recipe cost, even when its last ingredients have disappeared.
        power-=plan.cost;markDirty();
        for(var stack:remainder)InventoryUtil.addToBestAcceptor(world,pos,null,stack);
    }
    @Override public boolean onActivated(PlayerEntity player,Hand hand,Direction face,float x,float y,float z) {
        if(!canInteractWith(player) || player.getStackInHand(hand).getItem() instanceof buildcraft.api.tools.IToolWrench)return false;
        if(!world.isClient)buildcraft.silicon.BCSiliconScreens.openIntegration(player,this);return true;
    }
    @Override public void writeNbt(NbtCompound nbt){super.writeNbt(nbt);if(preferred!=null)nbt.putString("recipe",preferred.toString());}
    @Override public void readFromNBT(NbtCompound nbt){super.readFromNBT(nbt);preferred=Identifier.tryParse(nbt.getString("recipe"));}
    @Override public void writePayload(int id,PacketBufferBC buffer,NetSide side) {
        super.writePayload(id,buffer,side);
        if(side==NetSide.SERVER && id==NET_GUI_TICK){buffer.writeLong(getRecipeCost());buffer.writeByte(getStatus());}
    }
    @Override public void readPayload(int id,PacketBufferBC buffer,NetSide side,Object context) throws IOException {
        super.readPayload(id,buffer,side,context);
        if(side==NetSide.CLIENT && id==NET_GUI_TICK) {
            long cost=buffer.readLong();int status=buffer.readUnsignedByte();
            if(cost<0 || cost>NativeAssemblyRecipe.MAX_COST || status>3 || (status==0)!=(cost==0))throw new IOException("Invalid integration state");
            costClient=cost;statusClient=status;
        }
    }
}
