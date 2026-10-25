/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.misc;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;

/** Optional tracing of native renderer invalidations caused by BuildCraft calls. */
public final class DebuggingTools {
    private DebuggingTools() {}
    public static final boolean ENABLE = BCDebugging.shouldDebugComplex("lib.debug.getWorld()");
    public static void traceRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        if (!ENABLE) return;
        var frames = java.util.Arrays.stream(Thread.currentThread().getStackTrace())
            .filter(frame -> frame.getClassName().startsWith("buildcraft.")
                && !frame.getClassName().equals(DebuggingTools.class.getName())).toList();
        if (frames.isEmpty()) return;
        BCLog.logger.info("[lib.debug.getWorld()] scheduleBlockRenders({}, {}, {}, {}, {}, {})", x1, y1, z1, x2, y2, z2);
        for (var frame : frames) BCLog.logger.info("[lib.debug.getWorld()]   at {}", frame);
    }
}
