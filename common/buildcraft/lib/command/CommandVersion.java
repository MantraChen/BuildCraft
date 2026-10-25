package buildcraft.lib.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class CommandVersion {
    private CommandVersion() {}
    public static int execute(CommandContext<ServerCommandSource> ctx) {
        String version = FabricLoader.getInstance().getModContainer("buildcraft")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("development");
        ctx.getSource().sendFeedback(() -> Text.literal("BuildCraft Fabric " + version + " · Remake by FelixMantra"), false);
        return 1;
    }
}
