package buildcraft.lib.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import buildcraft.lib.script.ReloadableRegistryManager;
import buildcraft.api.core.BCLog;

public final class CommandReloadRegistries {
    private CommandReloadRegistries() {}
    public static int execute(CommandContext<ServerCommandSource> ctx) {
        try {
            ReloadableRegistryManager.DATA_PACKS.reloadAll();
            ctx.getSource().sendFeedback(() -> Text.translatable("command.buildcraft.reload.success"), true);
            return 1;
        } catch (RuntimeException failure) {
            BCLog.logger.error("BuildCraft registry reload failed", failure);
            ctx.getSource().sendError(Text.translatable("command.buildcraft.reload.failure"));
            return 0;
        }
    }
}
