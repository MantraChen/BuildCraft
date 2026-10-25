package buildcraft.lib.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class CommandChangelog {
    private CommandChangelog() {}
    public static int execute(CommandContext<ServerCommandSource> ctx) {
        String url = "https://github.com/MantraChen/BuildCraft-Fabric/commits/main/";
        ctx.getSource().sendFeedback(() -> Text.literal(url).formatted(Formatting.AQUA, Formatting.UNDERLINE)
            .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))), false);
        return 1;
    }
}
