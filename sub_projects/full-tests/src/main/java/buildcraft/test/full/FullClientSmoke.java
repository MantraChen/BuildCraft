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
            int variants = checkItemVariants(client, failures) + checkPipeGeometry(client, failures);
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

    private static int checkItemVariants(net.minecraft.client.MinecraftClient client, ArrayList<String> failures) {
        int count = 0;
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
