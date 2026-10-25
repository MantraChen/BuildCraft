package buildcraft.lib.client.resource;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/** Reads BuildCraft metadata while respecting resource-pack priority. */
public class MetadataLoader {

    /** @param samePack If true, then only the data in the same resource pack will be returned. */
    @Nullable
    public static DataMetadataSection getData(Identifier location, boolean samePack) {
        ResourceManager resManager = MinecraftClient.getInstance().getResourceManager();
        try {
            List<Resource> resources = resManager.getAllResources(location);
            for (int i = resources.size() - 1; i >= 0; i--) {
                DataMetadataSection section = resources.get(i).getMetadata()
                    .decode(DataMetadataSection.DESERIALISER).orElse(null);
                if (section != null || samePack) return section;
            }
            return null;
        } catch (IOException e) {
            buildcraft.BuildCraftFabric.LOGGER.warn("Unable to read resource metadata for {}", location, e);
            return null;
        }
    }
}
