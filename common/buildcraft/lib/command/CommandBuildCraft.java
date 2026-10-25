package buildcraft.lib.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.literal;

/** Native Fabric/Brigadier command tree. */
public final class CommandBuildCraft {
    private CommandBuildCraft() {}
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("buildcraft")
            .executes(ctx -> {
                ctx.getSource().sendFeedback(() -> Text.literal("/buildcraft <version|changelog|reload>"), false);
                return 1;
            })
            .then(literal("version").executes(CommandVersion::execute))
            .then(literal("changelog").executes(CommandChangelog::execute))
            .then(literal("reload").requires(source -> source.hasPermissionLevel(2)).executes(CommandReloadRegistries::execute)));
    }
}
