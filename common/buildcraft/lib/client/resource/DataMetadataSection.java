package buildcraft.lib.client.resource;

import com.google.gson.JsonObject;
import net.minecraft.resource.metadata.ResourceMetadataReader;

/** Generic metadata section, containing any types of data. */
public class DataMetadataSection {
    public static final String SECTION_NAME = "buildcraft_data";

    public final JsonObject data;

    public DataMetadataSection(JsonObject data) {
        this.data = data;
    }

    public static final ResourceMetadataReader<DataMetadataSection> DESERIALISER =
        new ResourceMetadataReader<DataMetadataSection>() {
            @Override
            public DataMetadataSection fromJson(JsonObject json) {
                return new DataMetadataSection(json);
            }

            @Override
            public String getKey() {
                return SECTION_NAME;
            }
        };
}
