/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.list;

import buildcraft.api.lists.ListRegistry;

public class VanillaListHandlers {
    private static boolean initialized, tagsInitialized;
    public static void fmlInit() {
        if (initialized) return;
        initialized = true;
        ListRegistry.registerHandler(new ListMatchHandlerClass());
        ListRegistry.registerHandler(new ListMatchHandlerFluid());
        ListRegistry.registerHandler(new ListMatchHandlerTools());
        ListRegistry.registerHandler(new ListMatchHandlerArmor());
    }

    public static void fmlPostInit() {
        if (tagsInitialized) return;
        tagsInitialized = true;
        // Membership is read from the current tag table; datapack reloads need no stale ID cache.
        ListRegistry.registerHandler(new ListMatchHandlerTags());
    }
}
