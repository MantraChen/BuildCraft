package buildcraft.test.full;

import java.util.ArrayList;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockRenderType;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/** Checks the actual packaged mod after Minecraft finishes baking resources with a real GL context. */
public final class FullClientSmoke implements ClientModInitializer {
    private boolean finished;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("buildcraft.fullClientSmoke")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished || client.getOverlay() != null || !(client.currentScreen instanceof TitleScreen)) return;
            finished = true;
            var failures = new ArrayList<String>();
            if (!buildcraft.api.statements.StatementManager.statements.containsKey("buildcraftcore:trigger.true"))
                failures.add("Core statements were not registered during normal startup");
            var missing = client.getBakedModelManager().getMissingModel();
            int states = 0, items = 0;
            for (var block : Registries.BLOCK) {
                if (!Registries.BLOCK.getId(block).getNamespace().startsWith("buildcraft")) continue;
                for (var state : block.getStateManager().getStates()) {
                    if (state.getRenderType() == BlockRenderType.INVISIBLE) continue;
                    states++;
                    try {
                        var model = client.getBlockRenderManager().getModel(state);
                        check(model, missing, state.toString(), failures);
                        if (state.getRenderType() == BlockRenderType.MODEL) {
                            checkQuads(model, state, state.toString(), failures);
                        }
                    } catch (Exception | LinkageError error) { failures.add(state + ": " + error); }
                }
            }
            for (var item : Registries.ITEM) {
                var id = Registries.ITEM.getId(item);
                if (!id.getNamespace().startsWith("buildcraft")) continue;
                items++;
                try {
                    var model = client.getItemRenderer().getModel(new ItemStack(item), null, null, 0);
                    check(model, missing, id.toString(), failures);
                    if (!model.isBuiltin()) checkQuads(model, null, id.toString(), failures);
                } catch (Exception | LinkageError error) { failures.add(id + ": " + error); }
            }
            int variants = checkItemVariants(client, failures) + checkPipeGeometry(client, failures) + checkGateGeometry(client, failures);
            checkWireGeometry(failures);
            checkFluidPipeGeometry(failures);
            var statements = new java.util.HashSet<>(buildcraft.api.statements.StatementManager.statements.values());
            int transportStatements = 0, coreStatements = 0;
            for (var statement : statements) if (statement.getClass().getPackageName().equals("buildcraft.transport.statements")
                    || statement.getClass().getPackageName().equals("buildcraft.core.statements")) {
                if (statement.getClass().getPackageName().equals("buildcraft.transport.statements")) transportStatements++;
                else coreStatements++;
                var icon = statement.getSprite();
                if (!(icon instanceof buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder holder)
                        || holder.getSprite().getContents().getId().equals(MissingSprite.getMissingSpriteId())) failures.add("Missing statement icon: " + statement.getUniqueTag());
                String description = statement.getDescription();
                if (description == null || description.isBlank() || description.startsWith("gate.") || description.contains("Bad Format") || description.matches(".*%[sd].*"))
                    failures.add("Untranslated statement: " + statement.getUniqueTag() + " = " + description);
            }
            if (transportStatements != 105) failures.add("Expected 105 unique transport statements, found " + transportStatements);
            System.out.println("BUILDCRAFT GATE STATEMENTS: " + coreStatements + " core, " + transportStatements + " transport");
            for (var slot : buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli.SlotIndex.VALUES) {
                var sprite = buildcraft.transport.BCTransportSprites.ACTION_EXTRACTION_PRESET.get(slot).getSprite();
                if (sprite == null || sprite.getContents().getId().equals(MissingSprite.getMissingSpriteId())) failures.add(slot + ": missing gate extraction preset sprite");
            }
            for (var item : new net.minecraft.item.Item[] {buildcraft.factory.BCFactoryBlocks.chute.asItem(), buildcraft.factory.BCFactoryBlocks.tank.asItem(),
                    buildcraft.core.BCCoreItems.gearWood, buildcraft.core.BCCoreItems.gearStone, buildcraft.core.BCCoreItems.wrench}) {
                var stack = new ItemStack(item);
                if (stack.getName().getString().equals(stack.getTranslationKey())) failures.add(stack.getTranslationKey() + ": missing localized name");
            }
            for (var key : java.util.List.of("command.buildcraft.reload.success", "command.buildcraft.reload.failure")) {
                if (!net.minecraft.client.resource.language.I18n.hasTranslation(key)) failures.add(key + ": missing localized command text");
            }
            System.out.println("BUILDCRAFT FULL CLIENT: " + states + " states, " + items + " items, "
                + variants + " item variants, failures=" + failures.size());
            failures.forEach(message -> System.err.println("MODEL FAILURE: " + message));
            if (!failures.isEmpty()) throw new IllegalStateException("Full client resource checks failed: " + failures.size());
            client.scheduleStop();
        });
    }

    private static int checkPipeGeometry(net.minecraft.client.MinecraftClient client, ArrayList<String> failures) {
        int count = 0;
        for (var def : buildcraft.transport.pipe.PipeRegistry.INSTANCE.getAllRegisteredPipes()) {
            var stack = new ItemStack((net.minecraft.item.Item) buildcraft.transport.pipe.PipeRegistry.INSTANCE.getItemForPipe(def));
            if (stack.getName().getString().equals(stack.getTranslationKey())) failures.add(def.identifier + ": missing pipe name");
            var baked = client.getItemRenderer().getModel(stack, null, null, 0);
            if (!(baked instanceof buildcraft.transport.client.model.ModelPipe model)) {
                failures.add(def.identifier + ": native pipe model missing"); continue;
            }
            for (var colour : new net.minecraft.util.DyeColor[] {null, net.minecraft.util.DyeColor.BLUE, net.minecraft.util.DyeColor.RED}) {
                var key = buildcraft.transport.client.model.ModelPipe.itemKey(def, colour);
                var geometry = model.getGeometry(key);
                if (geometry.cutout().isEmpty()) failures.add(def.identifier + ": empty pipe geometry");
                for (var quad : geometry.all()) {
                    if (quad.getSprite().getContents().getId().equals(MissingSprite.getMissingSpriteId()))
                        failures.add(def.identifier + ": missing pipe face/overlay");
                    var data = quad.getVertexData();
                    for (int vertex = 0; vertex < 4; vertex++) for (int axis = 0; axis < 3; axis++) {
                        float position = Float.intBitsToFloat(data[vertex * 8 + axis]);
                        if (!Float.isFinite(position) || position < -0.001 || position > 1.001)
                            failures.add(def.identifier + ": pipe geometry outside native block bounds");
                    }
                }
                count++;
            }
        }
        return count;
    }

    private static int checkGateGeometry(net.minecraft.client.MinecraftClient client, ArrayList<String> failures) {
        int count = 0;
        for (var variant : buildcraft.silicon.item.ItemPluggableGate.variants()) {
            var stack = buildcraft.silicon.BCSiliconItems.plugGate.getStack(variant);
            var model = client.getItemRenderer().getModel(stack, null, null, 0);
            if (!model.isBuiltin() || stack.getName().getString().contains("gate.")) failures.add("Invalid gate item " + variant.getVariantName());
            for (var side : Direction.values()) for (boolean on : new boolean[] {false, true}) {
                var quads = buildcraft.silicon.client.BCSiliconClient.MODEL.getQuads(variant, side, on);
                if (quads.length < 12) failures.add("Empty gate geometry " + variant.getVariantName());
                for (var quad : quads) {
                    if (quad.toBakedBlock().getSprite().getContents().getId().equals(MissingSprite.getMissingSpriteId())) failures.add("Missing gate texture " + variant.getVariantName());
                    var data = quad.toBakedBlock().getVertexData();
                    for (int v=0;v<4;v++) for(int axis=0;axis<3;axis++) {
                        float p = Float.intBitsToFloat(data[v*8+axis]);
                        if (!Float.isFinite(p) || p<0 || p>1) failures.add("Gate geometry outside block " + variant.getVariantName());
                    }
                }
            }
            count++;
        }
        return count;
    }

    private static int checkItemVariants(net.minecraft.client.MinecraftClient client, ArrayList<String> failures) {
        int count = 0;
        for (var colour : net.minecraft.util.DyeColor.values()) {
            var stack = buildcraft.transport.BCTransportItems.wire.stack(colour);
            String name = colour == net.minecraft.util.DyeColor.LIGHT_GRAY ? "silver" : colour.getName();
            checkItemTexture(client, stack, "buildcrafttransport:items/wire/" + name, failures);
            if (stack.getName().getString().contains("item.") || stack.getName().getString().contains("color.")) failures.add("Untranslated wire: " + colour);
            count++;
        }
        for (var type : buildcraft.api.items.IMapLocation.MapLocationType.values()) {
            var stack = new ItemStack(buildcraft.core.BCCoreItems.mapLocation);
            type.setToStack(stack);
            checkItemTexture(client, stack, "buildcraftcore:items/map/" + type.name().toLowerCase(java.util.Locale.ROOT), failures);
            count++;
        }
        String[] decorationTextures = {"buildcraftcore:blocks/misc/texture_red_dark", "buildcraftcore:blocks/blueprint/blue",
            "buildcraftcore:blocks/blueprint/black", "buildcraftcore:blocks/misc/paper", "buildcraftcore:blocks/misc/leather",
            "buildcraftsilicon:blocks/laser/bottom"};
        for (var type : buildcraft.api.enums.EnumDecoratedBlock.VALUES) {
            var stack = new ItemStack(buildcraft.core.BCCoreBlocks.decorated);
            stack.getOrCreateSubNbt("BlockStateTag").putString(
                buildcraft.core.block.BlockDecoration.DECORATED_TYPE.getName(), type.asString());
            checkItemTexture(client, stack, decorationTextures[type.ordinal()], failures);
            count++;
        }
        var list = new ItemStack(buildcraft.core.BCCoreItems.list);
        for (var type : buildcraft.builders.item.ItemSnapshot.EnumItemSnapshotType.values()) {
            var stack = buildcraft.builders.BCBuildersItems.snapshot.getClean(type.snapshotType);
            if (type.used) stack.getOrCreateNbt().put("header", new net.minecraft.nbt.NbtCompound());
            String kind = type.snapshotType.name().toLowerCase(java.util.Locale.ROOT);
            checkItemTexture(client, stack, "buildcraftbuilders:items/" + kind + "/" + (type.used ? "used" : "clean"), failures);
            count++;
        }
        checkItemTexture(client, list, "buildcraftcore:items/list/clean", failures);
        var lines = buildcraft.lib.list.ListHandler.getLines(list);
        lines[0].setStack(0, new ItemStack(net.minecraft.item.Items.STONE));
        buildcraft.lib.list.ListHandler.saveLines(list, lines);
        checkItemTexture(client, list, "buildcraftcore:items/list/used", failures);
        var schematic = new ItemStack(buildcraft.builders.BCBuildersItems.schematicSingle);
        checkItemTexture(client, schematic, "buildcraftbuilders:items/schematic_single/clean", failures);
        schematic.getOrCreateNbt().put(buildcraft.builders.item.ItemSchematicSingle.NBT_KEY, new net.minecraft.nbt.NbtCompound());
        checkItemTexture(client, schematic, "buildcraftbuilders:items/schematic_single/used", failures);
        return count + 4;
    }
    private static void checkItemTexture(net.minecraft.client.MinecraftClient client, ItemStack stack,
            String expectedTexture, ArrayList<String> failures) {
        var model = client.getItemRenderer().getModel(stack, null, null, 0);
        String label = Registries.ITEM.getId(stack.getItem()) + " " + stack.getNbt();
        check(model, client.getBakedModelManager().getMissingModel(), label, failures);
        checkQuads(model, null, label, failures);
        String actual = model.getParticleSprite().getContents().getId().toString();
        if (!actual.equals(expectedTexture)) failures.add(label + ": expected " + expectedTexture + ", got " + actual);
    }

    private static void checkWireGeometry(ArrayList<String> failures) {
        var tile = buildcraft.transport.BCTransportBlocks.pipeHolderTile.instantiate(net.minecraft.util.math.BlockPos.ORIGIN,
            buildcraft.transport.BCTransportBlocks.pipeHolder.getDefaultState());
        var matrices = new net.minecraft.client.util.math.MatrixStack(); matrices.translate(2,3,4);
        for (var colour : net.minecraft.util.DyeColor.values()) for (boolean on : new boolean[]{false,true}) {
            var sprite = buildcraft.transport.client.render.PipeWireRenderer.getWireSprite(colour).getSprite();
            if (sprite.getContents().getId().equals(MissingSprite.getMissingSpriteId())) failures.add("Missing wire texture: " + colour);
            for (var part : buildcraft.api.transport.EnumWirePart.VALUES) tile.wireManager.parts.put(part,colour);
            for (var between : buildcraft.transport.wire.EnumWireBetween.VALUES) tile.wireManager.betweens.put(between,colour);
            tile.wireManager.poweredClient.clear(); if (on) tile.wireManager.poweredClient.addAll(tile.wireManager.parts.keySet());
            var probe = new WireVertices(sprite,on);
            buildcraft.transport.client.render.PipeWireRenderer.renderWires(tile,matrices,probe,0);
            if (probe.vertices != 768 || !probe.valid) failures.add("Wire geometry/UV/light/matrix failure: " + colour + " powered=" + on + " vertices=" + probe.vertices);
        }
        System.out.println("BUILDCRAFT WIRE RENDER: 16 colours, 8 corners + 36 connections, lit/unlit");
    }

    private static void checkFluidPipeGeometry(ArrayList<String> failures) {
        int cases=0;
        var matrices=new net.minecraft.client.util.math.MatrixStack(); matrices.translate(2,3,4);
        for (var fluid : new net.minecraft.fluid.Fluid[]{net.minecraft.fluid.Fluids.WATER,net.minecraft.fluid.Fluids.LAVA}) {
            var variant=net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid);
            var sprites=net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering.getSprites(variant);
            var sprite=sprites[sprites.length>1?1:0];
            for (int mask=0;mask<64;mask++) for (long amount:new long[]{0,81,8100,40500,81000}) {
                double[] amounts=new double[7],lengths=new double[6]; java.util.Arrays.fill(amounts,amount);
                for(var side:Direction.values()) if ((mask & (1<<side.ordinal()))!=0) lengths[side.ordinal()]=0.25;
                var probe=new FluidVertices(sprite,fluid==net.minecraft.fluid.Fluids.LAVA?240:0);
                buildcraft.transport.client.render.PipeFlowRendererFluids.renderSections(variant,amounts,lengths,81000,matrices,layer -> {
                    probe.valid &= layer==net.minecraft.client.render.RenderLayer.getEntityTranslucent(net.minecraft.screen.PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
                    return probe;
                },0);
                int boxes=Integer.bitCount(mask)+1;
                if (amount<81000 && (mask & (1<<Direction.UP.ordinal()))!=0 && (mask & 60)!=0) boxes++;
                if (!probe.valid || probe.vertices!=(amount==0?0:boxes*24)) failures.add("Fluid pipe geometry/UV/light failure: "+fluid+" mask="+mask+" amount="+amount+" vertices="+probe.vertices);
                cases++;
            }
        }
        System.out.println("BUILDCRAFT FLUID PIPE RENDER: "+cases+" water/lava, connection and fill combinations");
    }

    private static final class FluidVertices implements net.minecraft.client.render.VertexConsumer {
        final net.minecraft.client.texture.Sprite sprite; final int blockLight; boolean valid=true; int vertices;
        FluidVertices(net.minecraft.client.texture.Sprite sprite,int blockLight) { this.sprite=sprite; this.blockLight=blockLight; }
        public net.minecraft.client.render.VertexConsumer vertex(double x,double y,double z) {
            valid &= Double.isFinite(x+y+z) && x>=1.9999 && x<=3.0001 && y>=2.9999 && y<=4.0001 && z>=3.9999 && z<=5.0001; return this;
        }
        public net.minecraft.client.render.VertexConsumer color(int r,int g,int b,int a) { valid &= a>0; return this; }
        public net.minecraft.client.render.VertexConsumer texture(float u,float v) {
            valid &= Float.isFinite(u+v) && u>=sprite.getMinU()-1e-6 && u<=sprite.getMaxU()+1e-6 && v>=sprite.getMinV()-1e-6 && v<=sprite.getMaxV()+1e-6; return this;
        }
        public net.minecraft.client.render.VertexConsumer overlay(int u,int v) { return this; }
        public net.minecraft.client.render.VertexConsumer light(int u,int v) { valid &= u==blockLight && v==0; return this; }
        public net.minecraft.client.render.VertexConsumer normal(float x,float y,float z) { valid &= Float.isFinite(x+y+z); return this; }
        public void next() { vertices++; }
        public void fixedColor(int r,int g,int b,int a) {}
        public void unfixColor() {}
    }

    private static final class WireVertices implements net.minecraft.client.render.VertexConsumer {
        final net.minecraft.client.texture.Sprite sprite; final boolean on; boolean valid = true; int vertices;
        WireVertices(net.minecraft.client.texture.Sprite sprite,boolean on) { this.sprite=sprite; this.on=on; }
        public net.minecraft.client.render.VertexConsumer vertex(double x,double y,double z) {
            valid &= Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && x>=2 && x<=3 && y>=3 && y<=4 && z>=4 && z<=5; return this;
        }
        public net.minecraft.client.render.VertexConsumer color(int r,int g,int b,int a) { valid &= a==255; return this; }
        public net.minecraft.client.render.VertexConsumer texture(float u,float v) {
            valid &= Float.isFinite(u) && Float.isFinite(v) && u>=sprite.getMinU()-1e-6 && u<=sprite.getMaxU()+1e-6
                && v>=sprite.getMinV()-1e-6 && v<=sprite.getMaxV()+1e-6;
            float fraction=(v-sprite.getMinV())/(sprite.getMaxV()-sprite.getMinV());
            valid &= on ? fraction>=0.9374 : fraction<=0.0626; return this;
        }
        public net.minecraft.client.render.VertexConsumer overlay(int u,int v) { return this; }
        public net.minecraft.client.render.VertexConsumer light(int u,int v) { valid &= on ? u==240 && v==240 : u==0 && v==0; return this; }
        public net.minecraft.client.render.VertexConsumer normal(float x,float y,float z) { valid &= Float.isFinite(x+y+z); return this; }
        public void next() { vertices++; }
        public void fixedColor(int r,int g,int b,int a) { }
        public void unfixColor() { }
    }
    private static void check(BakedModel model, BakedModel missing, String label, ArrayList<String> failures) {
        if (model == missing) failures.add(label + ": missing model");
        else if (model.getParticleSprite().getContents().getId().equals(MissingSprite.getMissingSpriteId()))
            failures.add(label + ": missing particle texture");
    }
    private static void checkQuads(BakedModel model, net.minecraft.block.BlockState state, String label, ArrayList<String> failures) {
        var sides = new Direction[] {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (var side : sides) for (var quad : model.getQuads(state, side, Random.create(0)))
            if (quad.getSprite().getContents().getId().equals(MissingSprite.getMissingSpriteId()))
                failures.add(label + ": missing face texture on " + side);
    }
}
