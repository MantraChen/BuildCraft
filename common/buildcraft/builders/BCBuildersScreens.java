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
