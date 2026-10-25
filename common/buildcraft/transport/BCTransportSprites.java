/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */

package buildcraft.transport;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;

import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;

import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli.SlotIndex;

public class BCTransportSprites {

    private static final String NS = "buildcrafttransport:";

    public static final SpriteHolder EMPTY_FILTERED_BUFFER_SLOT =
        getHolder("gui/empty_filtered_buffer_slot");
    public static final SpriteHolder NOTHING_FILTERED_BUFFER_SLOT =
        getHolder("gui/nothing_filtered_buffer_slot");
    public static final SpriteHolder PIPE_COLOUR            = getHolder("pipes/overlay_stained");
    public static final SpriteHolder COLOUR_ITEM_BOX        = getHolder("pipes/colour_item_box");
    public static final SpriteHolder PIPE_COLOUR_BORDER_OUTER = getHolder("pipes/colour_border_outer");
    public static final SpriteHolder PIPE_COLOUR_BORDER_INNER = getHolder("pipes/colour_border_inner");

    public static final SpriteHolder TRIGGER_POWER_REQUESTED   = getHolder("triggers/trigger_pipecontents_requestsenergy");
    public static final SpriteHolder TRIGGER_ITEMS_TRAVERSING  = getHolder("triggers/trigger_pipecontents_containsitems");
    public static final SpriteHolder TRIGGER_FLUIDS_TRAVERSING = getHolder("triggers/trigger_pipecontents_containsfluids");

    public static final SpriteHolder POWER_FLOW          = getHolder("pipes/power_flow");
    public static final SpriteHolder POWER_FLOW_OVERLOAD = getHolder("pipes/power_flow_overload");
    public static final SpriteHolder POWER_FLOW_RF       = getHolder("pipes/rf_flow");

    public static final SpriteHolder[] ACTION_PIPE_COLOUR;
    public static final SpriteHolder[] POWER_LIMIT;
    public static final SpriteHolder[] POWER_LIMIT_RF;
    public static final EnumMap<SlotIndex, SpriteHolder> ACTION_EXTRACTION_PRESET;

    private static final EnumMap<DyeColor, SpriteHolder> PIPE_SIGNAL_ON  = new EnumMap<>(DyeColor.class);
    private static final EnumMap<DyeColor, SpriteHolder> PIPE_SIGNAL_OFF = new EnumMap<>(DyeColor.class);
    private static final EnumMap<Direction, SpriteHolder> ACTION_PIPE_DIRECTION = new EnumMap<>(Direction.class);

    static {
        ACTION_PIPE_COLOUR = new SpriteHolder[DyeColor.values().length];
        POWER_LIMIT    = new SpriteHolder[7];
        POWER_LIMIT_RF = new SpriteHolder[7];
        for (DyeColor c : DyeColor.values()) {
            String name = c == DyeColor.LIGHT_GRAY ? "silver" : c.getName();
            ACTION_PIPE_COLOUR[c.getId()] = SpriteHolderRegistry.getHolder("buildcraftcore:items/paintbrush/" + name);
            PIPE_SIGNAL_ON.put(c,  getHolder("triggers/trigger_pipesignal_" + name + "_active"));
            PIPE_SIGNAL_OFF.put(c, getHolder("triggers/trigger_pipesignal_" + name + "_inactive"));
        }
        int[] originalIcons = {256, 128, 64, 16, 8, 2, 0};
        for (int i = 0; i < originalIcons.length; i++) {
            POWER_LIMIT[i]    = getHolder("triggers/trigger_limiter_m" + originalIcons[i]);
            POWER_LIMIT_RF[i] = getHolder("triggers/trigger_rf_limiter_m" + originalIcons[i]);
        }
        ACTION_EXTRACTION_PRESET = new EnumMap<>(SlotIndex.class);
        for (SlotIndex slot : SlotIndex.values()) {
            ACTION_EXTRACTION_PRESET.put(slot,
                getHolder("triggers/extraction_preset_" + slot.colour.getName()));
        }
        for (Direction face : Direction.values()) {
            ACTION_PIPE_DIRECTION.put(face,
                SpriteHolderRegistry.getHolder("buildcraftcore:triggers/trigger_dir_" + face.getName()));
        }
    }

    private static SpriteHolder getHolder(String path) {
        return SpriteHolderRegistry.getHolder(NS + path);
    }

    public static SpriteHolder getPipeSignal(boolean active, DyeColor colour) {
        return (active ? PIPE_SIGNAL_ON : PIPE_SIGNAL_OFF).get(colour);
    }

    public static SpriteHolder getPipeDirection(Direction face) {
        return ACTION_PIPE_DIRECTION.get(face);
    }
}
