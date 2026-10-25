/* Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, see https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import java.util.function.Consumer;
import buildcraft.lib.registry.TagManager;
import buildcraft.lib.registry.TagManager.EnumTagType;
import buildcraft.lib.registry.TagManager.TagEntry;

/** Registry metadata shared by the native initializer and legacy migration references. */
public final class BCRoboticsTags {
    private static boolean registered;
    private BCRoboticsTags() {}
    public static void register() {
        if (registered) return;
        registered = true;
        startBatch();

        // Items

        // Item Blocks
        registerTag("item.block.zone_planner").reg("zone_planner").locale("zonePlannerBlock").model("zone_planner");

        // Blocks
        registerTag("block.zone_planner").reg("zone_planner").oldReg("zonePlannerBlock").locale("zonePlannerBlock").model("zone_planner");

        // Tiles
        registerTag("tile.zone_planner").reg("zone_planner");

        endBatch(TagManager.prependTags("buildcraftrobotics:", EnumTagType.REGISTRY_NAME, EnumTagType.MODEL_LOCATION).andThen(TagManager.setTab("buildcraft.main")));
    }
    private static TagEntry registerTag(String id) { return TagManager.registerTag(id); }
    private static void startBatch() { TagManager.startBatch(); }
    private static void endBatch(Consumer<TagEntry> action) { TagManager.endBatch(action); }
}
