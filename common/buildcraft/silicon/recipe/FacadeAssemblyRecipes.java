/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.silicon.recipe;

import java.util.*;
import com.google.gson.*;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.plug.*;
import buildcraft.transport.BCTransportItems;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Original 64 MJ / 3 structure pipes + one block -> six solid or hollow facades. */
public final class FacadeAssemblyRecipes {
    private FacadeAssemblyRecipes(){}
    public static Identifier id(FacadeBlockStateInfo info,boolean hollow){
        String properties=info.state.getEntries().entrySet().stream().sorted(Comparator.comparing(e->e.getKey().getName()))
            .map(e->e.getKey().getName()+"="+e.getValue()).collect(java.util.stream.Collectors.joining(","));
        String suffix="";
        if(!properties.isEmpty())try{
            suffix="/"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(properties.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        var block=Registries.BLOCK.getId(info.state.getBlock());
        return new Identifier("buildcraftsilicon","assembly/facades/"+block.getNamespace()+"/"+block.getPath()+suffix+(hollow?"/hollow":"/solid"));
    }
    public static Map<Identifier,JsonElement> addDefaults(Map<Identifier,JsonElement> loaded){
        var result=new HashMap<Identifier,JsonElement>(loaded);
        for(var info:FacadeStateManager.INSTANCE.getValidFacades())if(info.isVisible)for(boolean hollow:new boolean[]{false,true}){
            var id=id(info,hollow);if(result.containsKey(id))continue;
            var json=new JsonObject();json.addProperty("type","buildcraftsilicon:assembly");json.addProperty("mj",64);
            var ingredients=new JsonArray();var required=info.getRequiredStack();
            var block=new JsonObject();var blockIngredient=new JsonObject();blockIngredient.addProperty("item",Registries.ITEM.getId(required.getItem()).toString());
            block.add("ingredient",blockIngredient);if(required.hasNbt())block.addProperty("nbt",required.getNbt().toString());ingredients.add(block);
            var pipes=new JsonObject();var pipeIngredient=new JsonObject();pipeIngredient.addProperty("item",Registries.ITEM.getId(BCTransportItems.pipeStructure).toString());
            pipes.add("ingredient",pipeIngredient);pipes.addProperty("count",3);ingredients.add(pipes);json.add("ingredients",ingredients);
            var output=new JsonObject();output.addProperty("item",Registries.ITEM.getId(BCSiliconItems.plugFacade).toString());output.addProperty("count",6);
            output.addProperty("nbt",BCSiliconItems.plugFacade.createFacadeStack(FacadeInstance.createSingle(info,hollow)).getNbt().toString());
            json.add("result",output);result.put(id,json);
        }
        return result;
    }
}