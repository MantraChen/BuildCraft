/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.cap;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.MjCapabilityHelper;

/** Collects machine providers. Native lookups expose the corresponding typed interfaces. */
public class CapabilityHelper {

    private final List<Object> additional = new ArrayList<>();

    public CapabilityHelper() {}

    public Object addProvider(Object provider) {
        if (provider != null) {
            additional.add(provider);
        }
        return provider;
    }

    /** Machine-wide MJ provider; tiles with face restrictions override their sided getter. */
    @Nullable
    public IMjConnector getMjConnector() {
        for (Object provider : additional) {
            if (provider instanceof MjCapabilityHelper helper) return helper.connector;
            if (provider instanceof IMjConnector connector) return connector;
        }
        return null;
    }
}
