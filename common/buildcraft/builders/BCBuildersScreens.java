package buildcraft.builders;

import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.tile.TileFiller;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.builders.container.ContainerBuilder;
import buildcraft.lib.gui.ContainerBC_Neptune;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class BCBuildersScreens {
    private BCBuildersScreens() {}
    public static final ScreenHandlerType<ContainerBC_Neptune> PLANNER=new ExtendedScreenHandlerType<>((syncId,inventory,data)->{
        var tag=data.readNbt();var slot=data.readEnumConstant(buildcraft.core.marker.volume.EnumAddonSlot.class);
        if(tag==null)throw new IllegalStateException("Missing planner volume");
        var volume=buildcraft.core.marker.volume.ClientVolumeBoxes.INSTANCE.upsert(inventory.player.getWorld(),tag);
        if(!(volume.addons.get(slot) instanceof buildcraft.builders.addon.AddonFillerPlanner addon))throw new IllegalStateException("Missing planner addon");
        return new buildcraft.builders.container.ContainerFillerPlanner(inventory.player,syncId,addon);
    });
    public static final ScreenHandlerType<ContainerBC_Neptune> LIBRARY=new ExtendedScreenHandlerType<>((syncId,inventory,data)->{
        BlockPos pos=data.readBlockPos();
        if(!(inventory.player.getWorld().getBlockEntity(pos) instanceof buildcraft.builders.tile.TileElectronicLibrary tile))
            throw new IllegalStateException("Library screen does not match block entity at "+pos);
        return new buildcraft.builders.container.ContainerElectronicLibrary(inventory.player,syncId,tile);
    });
    public static final ScreenHandlerType<ContainerBC_Neptune> REPLACER=new ExtendedScreenHandlerType<>((syncId,inventory,data)->{
        BlockPos pos=data.readBlockPos();
        if(!(inventory.player.getWorld().getBlockEntity(pos) instanceof buildcraft.builders.tile.TileReplacer tile))
            throw new IllegalStateException("Replacer screen does not match block entity at "+pos);
        return new buildcraft.builders.container.ContainerReplacer(inventory.player,syncId,tile);
    });
    public static final ScreenHandlerType<ContainerBC_Neptune> ARCHITECT = new ExtendedScreenHandlerType<>((syncId, inventory, data) -> {
        BlockPos pos = data.readBlockPos();
        if (!(inventory.player.getWorld().getBlockEntity(pos) instanceof buildcraft.builders.tile.TileArchitectTable tile))
            throw new IllegalStateException("Architect screen does not match block entity at " + pos);
        return new buildcraft.builders.container.ContainerArchitectTable(inventory.player, syncId, tile);
    });
    public static final ScreenHandlerType<ContainerBC_Neptune> FILLER = new ExtendedScreenHandlerType<>((syncId, inventory, data) -> {
        BlockPos pos = data.readBlockPos();
        if (!(inventory.player.getWorld().getBlockEntity(pos) instanceof TileFiller tile))
            throw new IllegalStateException("Filler screen does not match block entity at " + pos);
        return new ContainerFiller(inventory.player, syncId, tile);
    });
    public static final ScreenHandlerType<ContainerBC_Neptune> BUILDER = new ExtendedScreenHandlerType<>((syncId, inventory, data) -> {
        BlockPos pos = data.readBlockPos();
        if (!(inventory.player.getWorld().getBlockEntity(pos) instanceof TileBuilder tile))
            throw new IllegalStateException("Builder screen does not match block entity at " + pos);
        return new ContainerBuilder(inventory.player, syncId, tile);
    });
    public static void register() {
        Registry.register(Registries.SCREEN_HANDLER,new Identifier("buildcraftbuilders","filler_planner"),PLANNER);
        Registry.register(Registries.SCREEN_HANDLER,new Identifier("buildcraftbuilders","library"),LIBRARY);
        Registry.register(Registries.SCREEN_HANDLER,new Identifier("buildcraftbuilders","replacer"),REPLACER);
        Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcraftbuilders", "architect"), ARCHITECT);
        Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcraftbuilders", "filler"), FILLER);
        Registry.register(Registries.SCREEN_HANDLER, new Identifier("buildcraftbuilders", "builder"), BUILDER);
    }
    public static void openReplacer(PlayerEntity player,BlockPos pos){
        if(!(player instanceof ServerPlayerEntity)||!player.getWorld().isChunkLoaded(pos)
            ||!(player.getWorld().getBlockEntity(pos) instanceof buildcraft.builders.tile.TileReplacer tile)||!tile.canInteractWith(player))return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory(){
            public Text getDisplayName(){return Text.translatable("gui.buildcraftbuilders.replacer");}
            public void writeScreenOpeningData(ServerPlayerEntity user,PacketByteBuf buffer){buffer.writeBlockPos(pos);}
            public ScreenHandler createMenu(int syncId,PlayerInventory inventory,PlayerEntity user){
                return tile.canInteractWith(user)?new buildcraft.builders.container.ContainerReplacer(user,syncId,tile):null;
            }
        });
    }
    public static void openLibrary(PlayerEntity player,BlockPos pos){
        if(!(player instanceof ServerPlayerEntity)||!player.getWorld().isChunkLoaded(pos)
            ||!(player.getWorld().getBlockEntity(pos) instanceof buildcraft.builders.tile.TileElectronicLibrary tile)||!tile.canInteractWith(player))return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory(){
            public Text getDisplayName(){return Text.translatable("gui.buildcraftbuilders.library");}
            public void writeScreenOpeningData(ServerPlayerEntity user,PacketByteBuf buffer){buffer.writeBlockPos(pos);}
            public ScreenHandler createMenu(int syncId,PlayerInventory inventory,PlayerEntity user){
                return tile.canInteractWith(user)?new buildcraft.builders.container.ContainerElectronicLibrary(user,syncId,tile):null;
            }
        });
    }
    public static void openPlanner(PlayerEntity player,buildcraft.builders.addon.AddonFillerPlanner addon){
        if(!(player instanceof ServerPlayerEntity)||addon.volumeBox.world!=player.getWorld()
            ||!addon.volumeBox.addons.containsValue(addon))return;
        var probe=new buildcraft.builders.container.ContainerFillerPlanner(player,0,addon);
        if(!probe.canUse(player)||probe.isLocked())return;
        var slot=addon.getSlot();
        player.openHandledScreen(new ExtendedScreenHandlerFactory(){
            public Text getDisplayName(){return Text.translatable("gui.buildcraftbuilders.filler_planner");}
            public void writeScreenOpeningData(ServerPlayerEntity user,PacketByteBuf buffer){buffer.writeNbt(addon.volumeBox.writeToNBT());buffer.writeEnumConstant(slot);}
            public ScreenHandler createMenu(int syncId,PlayerInventory inventory,PlayerEntity user){
                return probe.canUse(user)&&!probe.isLocked()?new buildcraft.builders.container.ContainerFillerPlanner(user,syncId,addon):null;
            }
        });
    }
    public static void openArchitect(PlayerEntity player, BlockPos pos) {
        if (!(player instanceof ServerPlayerEntity) || !player.getWorld().isChunkLoaded(pos)
            || !(player.getWorld().getBlockEntity(pos) instanceof buildcraft.builders.tile.TileArchitectTable tile)
            || !tile.canInteractWith(player)) return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            public Text getDisplayName() { return Text.translatable("gui.buildcraftbuilders.architect"); }
            public void writeScreenOpeningData(ServerPlayerEntity user, PacketByteBuf buffer) { buffer.writeBlockPos(pos); }
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity user) {
                return tile.canInteractWith(user) ? new buildcraft.builders.container.ContainerArchitectTable(user, syncId, tile) : null;
            }
        });
    }
    public static void openFiller(PlayerEntity player, BlockPos pos) {
        if (!(player instanceof ServerPlayerEntity) || !player.getWorld().isChunkLoaded(pos)
            || !(player.getWorld().getBlockEntity(pos) instanceof TileFiller tile) || !tile.canInteractWith(player)) return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            public Text getDisplayName() { return Text.translatable("gui.buildcraftbuilders.filler"); }
            public void writeScreenOpeningData(ServerPlayerEntity user, PacketByteBuf buffer) { buffer.writeBlockPos(pos); }
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity user) {
                return tile.canInteractWith(user) ? new ContainerFiller(user, syncId, tile) : null;
            }
        });
    }
    public static void openBuilder(PlayerEntity player, BlockPos pos) {
        if (!(player instanceof ServerPlayerEntity) || !player.getWorld().isChunkLoaded(pos)
            || !(player.getWorld().getBlockEntity(pos) instanceof TileBuilder tile) || !tile.canInteractWith(player)) return;
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            public Text getDisplayName() { return Text.translatable("gui.buildcraftbuilders.builder"); }
            public void writeScreenOpeningData(ServerPlayerEntity user, PacketByteBuf buffer) { buffer.writeBlockPos(pos); }
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity user) {
                return tile.canInteractWith(user) ? new ContainerBuilder(user, syncId, tile) : null;
            }
        });
    }
}
