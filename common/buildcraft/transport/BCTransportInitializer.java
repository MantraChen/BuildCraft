/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.transport;

import net.fabricmc.api.ModInitializer;

/** Register common transport types before any client renderer or world can use them. */
public final class BCTransportInitializer implements ModInitializer {
    @Override public void onInitialize() {
        buildcraft.BuildCraftFabric.LOGGER.info("[BCTransport] Initializing common pipe registry");
        BCTransport.preInit();
        BCTransport.init();
    }
}
