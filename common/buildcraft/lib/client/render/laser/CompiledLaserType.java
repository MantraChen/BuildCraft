/* Copyright (c) 2017 SpaceToad and the BuildCraft team. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.client.render.laser;

import java.util.EnumMap;
import java.util.Arrays;
import buildcraft.lib.client.render.laser.LaserData_BC8.*;

/** Reusable texture rows, with short beams and fractional final segments clipped to their actual length. */
public final class CompiledLaserType {
    public final LaserType type;
    private final CompiledLaserRow startCap, endCap, start, end;
    private final EnumMap<LaserSide, CompiledLaserRow> middle = new EnumMap<>(LaserSide.class);
    public CompiledLaserType(LaserType type) {
        this.type = type;
        startCap = row(type.capStart); endCap = row(type.capEnd); start = row(type.start); end = row(type.end);
        for (LaserSide side : LaserSide.VALUES) {
            LaserRow[] rows = Arrays.stream(type.variations)
                .filter(r -> r.width > 0 && r.height > 0 && Arrays.asList(r.validSides).contains(side)).toArray(LaserRow[]::new);
            if (rows.length > 0) middle.put(side, new CompiledLaserRow(rows));
        }
    }
    private static CompiledLaserRow row(LaserRow row) { return row == null || row.width <= 0 || row.height <= 0 ? null : new CompiledLaserRow(row); }
    public void bakeFor(LaserContext context) {
        if (!context.isValid()) return;
        if (startCap != null) startCap.bakeStartCap(context);
        if (endCap != null) endCap.bakeEndCap(context);
        double startLength = start == null ? 0 : start.width;
        double endLength = end == null ? 0 : end.width;
        double ends = startLength + endLength;
        if (ends > context.length) {
            startLength *= context.length / ends;
            endLength = context.length - startLength;
        }
        if (start != null && startLength > 0) start.bakeStart(context, startLength);
        if (end != null && endLength > 0) end.bakeEnd(context, endLength);
        for (var entry : middle.entrySet()) entry.getValue().bakeFor(context, entry.getKey(), startLength, context.length - endLength);
    }
}
