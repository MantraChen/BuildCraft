/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 *
 * Ported to Fabric 1.20.1 by R.Chen (https://github.com/MantraChen).
 */
package buildcraft.transport;

import buildcraft.api.mj.MjAPI;

// Native MJ throughput/loss settings; other transport settings currently retain their defaults.
public class BCTransportConfig {

    public enum PowerLossMode {
        LOSSLESS, PERCENTAGE, ABSOLUTE;

        public static final PowerLossMode DEFAULT = LOSSLESS;
        public static final PowerLossMode[] VALUES = values();
    }

    private static final long MJ_REQ_MILLIBUCKET_MIN = 100;
    private static final long MJ_REQ_ITEM_MIN = 50_000;

    public static long mjPerMillibucket = 1_000;
    public static long mjPerItem = MjAPI.MJ;
    public static int baseFlowRate = 10;
    public static int basePowerRate = 4;
    public static int baseRfRate = 40;
    public static boolean fluidPipeColourBorder = false;
    public static boolean disableRfPipe = false;
    public static boolean powerPipeUseOldMjTexture = false;
    public static PowerLossMode lossMode = PowerLossMode.DEFAULT;
    public static int networkUpdateRate = 4;
    private static buildcraft.lib.config.Property propBasePowerRate, propBaseRfRate, propLossMode;

    public static void preInit() {
        var config = buildcraft.core.BCCoreConfig.config;
        propBasePowerRate = config.get("general", "pipes.basePowerRate", 4).setMinValue(1).setMaxValue(40);
        propBaseRfRate = config.get("general", "pipes.baseRfRate", 40).setMinValue(10).setMaxValue(4000);
        buildcraft.lib.config.EnumRestartRequirement.WORLD.setTo(propBaseRfRate);
        propLossMode = config.get("experimental", "kinesisLossMode", "lossless");
        buildcraft.lib.misc.ConfigUtil.setEnumProperty(propLossMode, PowerLossMode.VALUES);
        buildcraft.lib.config.EnumRestartRequirement.WORLD.setTo(propBasePowerRate);
        buildcraft.lib.config.EnumRestartRequirement.WORLD.setTo(propLossMode);
        reloadConfig();
        buildcraft.core.BCCoreConfig.addReloadListener(restart -> {
            if (buildcraft.lib.config.EnumRestartRequirement.WORLD.hasBeenRestarted(restart)) reloadConfig();
        });
    }

    public static void reloadConfig() {
        if (propBasePowerRate == null) return;
        basePowerRate = Math.max(1, Math.min(40, propBasePowerRate.getInt()));
        baseRfRate = Math.max(10, Math.min(4000, propBaseRfRate.getInt()));
        lossMode = buildcraft.lib.misc.ConfigUtil.parseEnumForConfig(propLossMode, PowerLossMode.DEFAULT);
        configurePowerPipes();
    }

    public static void configurePowerPipes() {
        int rate = Math.max(1, Math.min(40, basePowerRate));
        power(BCTransportPipes.cobblePower, rate, 16, false);
        power(BCTransportPipes.stonePower, rate * 2, 32, false);
        power(BCTransportPipes.woodPower, rate * 4, 128, true);
        power(BCTransportPipes.sandstonePower, rate * 4, 32, false);
        power(BCTransportPipes.quartzPower, rate * 8, 32, false);
        power(BCTransportPipes.ironPower, rate * 8, 32, false);
        power(BCTransportPipes.goldPower, rate * 32, 32, false);
        power(BCTransportPipes.diamondPower, rate * 64, 32, false);
        power(BCTransportPipes.diaWoodPower, rate * 64, 32, true);
        int rfRate = Math.max(10, Math.min(4000, baseRfRate));
        rf(BCTransportPipes.cobbleRf, rfRate, false);
        rf(BCTransportPipes.stoneRf, rfRate * 2, false);
        rf(BCTransportPipes.woodRf, rfRate * 4, true);
        rf(BCTransportPipes.sandstoneRf, rfRate * 4, false);
        rf(BCTransportPipes.quartzRf, rfRate * 8, false);
        rf(BCTransportPipes.ironRf, rfRate * 8, false);
        rf(BCTransportPipes.goldRf, rfRate * 32, false);
        rf(BCTransportPipes.diamondRf, rfRate * 64, false);
        rf(BCTransportPipes.diaWoodRf, rfRate * 64, true);
    }
    private static void rf(buildcraft.api.transport.pipe.PipeDefinition pipe, int rate, boolean receiver) {
        if (pipe != null) buildcraft.api.transport.pipe.PipeApi.rfTransferData.put(pipe,
            new buildcraft.api.transport.pipe.PipeApi.RedstoneFluxTransferInfo(rate, receiver));
    }
    private static void power(buildcraft.api.transport.pipe.PipeDefinition pipe, int rate, int divisor, boolean receiver) {
        if (pipe != null) buildcraft.api.transport.pipe.PipeApi.powerTransferData.put(pipe,
            buildcraft.api.transport.pipe.PipeApi.PowerTransferInfo.createFromResistance(rate * MjAPI.MJ, MjAPI.MJ / divisor, receiver));
    }
}
