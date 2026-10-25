/* Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, see https://mozilla.org/MPL/2.0/.
 */
package buildcraft.lib;

import java.util.function.Consumer;
import buildcraft.lib.registry.TagManager;
import buildcraft.lib.registry.TagManager.EnumTagType;
import buildcraft.lib.registry.TagManager.TagEntry;

/** Registry metadata shared by the native initializer and legacy migration references. */
public final class BCLibTags {
    private static boolean registered;
    private BCLibTags() {}
    public static void register() {
        if (registered) return;
        registered = true;
        startBatch();
        registerTag("item.guide").reg("guide").locale("buildcraft.guide").model("guide").tab("vanilla.misc");
        registerTag("item.guide.note").reg("guide_note").locale("buildcraft.guide_note").model("guide_note")
            .tab("vanilla.misc");
        registerTag("item.debugger").reg("debugger").locale("debugger").model("debugger").tab("vanilla.misc");
        endBatch(TagManager.prependTags("buildcraftlib:", EnumTagType.REGISTRY_NAME, EnumTagType.MODEL_LOCATION));
    }
    private static TagEntry registerTag(String id) { return TagManager.registerTag(id); }
    private static void startBatch() { TagManager.startBatch(); }
    private static void endBatch(Consumer<TagEntry> action) { TagManager.endBatch(action); }
}
