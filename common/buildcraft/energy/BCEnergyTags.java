/* Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, see https://mozilla.org/MPL/2.0/.
 */
package buildcraft.energy;

import java.util.function.Consumer;
import buildcraft.lib.registry.TagManager;
import buildcraft.lib.registry.TagManager.EnumTagType;
import buildcraft.lib.registry.TagManager.TagEntry;

/** Registry metadata shared by the native initializer and legacy migration references. */
public final class BCEnergyTags {
    private static boolean registered;
    private BCEnergyTags() {}
    public static void register() {
        if (registered) return;
        registered = true;
        startBatch();
        // Items
        registerTag("item.glob.oil").reg("glob_of_oil").oldReg("glob_oil").locale("globOil").model("glob_oil");

        // Item Blocks
        registerTag("item.block.mj_dynamo").reg("mj_dynamo").locale("mjDynamo").model("mj_dynamo");

        // Blocks
        registerTag("block.mj_dynamo").reg("mj_dynamo").locale("mjDynamo").model("mj_dynamo");

        // Tiles
        registerTag("tile.engine.stone").reg("engine.stone");
        registerTag("tile.engine.iron").reg("engine.iron");
        registerTag("tile.engine.rf").reg("engine.rf");
        registerTag("tile.spring.oil").reg("spring.oil");
        registerTag("tile.mj_dynamo").reg("mj_dynamo");

        endBatch(TagManager.prependTags("buildcraftenergy:", EnumTagType.REGISTRY_NAME, EnumTagType.MODEL_LOCATION)
            .andThen(TagManager.setTab("buildcraft.main")));
    }
    private static TagEntry registerTag(String id) { return TagManager.registerTag(id); }
    private static void startBatch() { TagManager.startBatch(); }
    private static void endBatch(Consumer<TagEntry> action) { TagManager.endBatch(action); }
}
