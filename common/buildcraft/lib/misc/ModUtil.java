package buildcraft.lib.misc;

import javax.annotation.Nullable;

import net.fabricmc.loader.api.FabricLoader;

public class ModUtil {

    @Nullable
    public static String getNameOfMod(String domain) {
        return FabricLoader.getInstance().getModContainer(domain)
            .map(mod -> mod.getMetadata().getName()).orElse(null);
    }
}
