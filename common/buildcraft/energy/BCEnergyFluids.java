/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.MapColor;
import net.minecraft.util.Identifier;


import buildcraft.lib.fluid.BCFluid;
import buildcraft.lib.fluid.BCFluidBlock;
import buildcraft.lib.fluid.FluidManager;

public class BCEnergyFluids {
    public static BCFluid[] crudeOil;
    /** All 3 fuels (no residue) */
    public static BCFluid[] oilDistilled;
    /** The 3 heaviest components (fuelLight, fuelDense and oilResidue) */
    public static BCFluid[] oilHeavy;
    /** The 2 lightest fuels (no dense fuel) */
    public static BCFluid[] fuelMixedLight;
    /** The 2 heaviest fuels (no gaseous fuel) */
    public static BCFluid[] fuelMixedHeavy;
    /** The 2 heaviest products (fuelDense and oilResidue) */
    public static BCFluid[] oilDense;

    // End products in order from least to most dense
    public static BCFluid[] fuelGaseous;
    public static BCFluid[] fuelLight;
    public static BCFluid[] fuelDense;
    public static BCFluid[] oilResidue;

    public static BCFluid tar;

    public static final List<BCFluid> allFluids = new ArrayList<>();

    public static void register(boolean stickyEnabled, boolean flammableEnabled) {
        if (!allFluids.isEmpty()) throw new IllegalStateException("BuildCraft fluids already registered");
        int[][] data = { //@formatter:off
            // Tabular form of all the fluid values
            // density, viscosity, boil, spread,  tex_light,   tex_dark, sticky, flammable
            {      900,      2000,    3,      6, 0x50_50_50, 0x05_05_05,      1,         1 },// Crude Oil
            {     1200,      4000,    3,      4, 0x10_0F_10, 0x42_10_42,      1,         0 },// Residue
            {      850,      1800,    3,      6, 0xA0_8F_1F, 0x42_35_20,      1,         1 },// Heavy Oil
            {      950,      1600,    3,      5, 0x87_6E_77, 0x42_24_24,      1,         1 },// Dense Oil
            {      750,      1400,    2,      8, 0xE4_AF_78, 0xB4_7F_00,      0,         1 },// Distilled Oil
            {      600,       800,    2,      7, 0xFF_AF_3F, 0xE0_7F_00,      0,         1 },// Dense Fuel
            {      700,      1000,    2,      7, 0xF2_A7_00, 0xC4_87_00,      0,         1 },// Mixed Heavy Fuels
            {      400,       600,    1,      8, 0xFF_FF_30, 0xE4_CF_00,      0,         1 },// Light Fuel
            {      650,       900,    1,      9, 0xF6_D7_00, 0xC4_B7_00,      0,         1 },// Mixed Light Fuels
            {      300,       500,    0,     10, 0xFA_F6_30, 0xE0_D9_00,      0,         1 },// Gas Fuel
        };//@formatter:on
        {
            int index = 0;

            // Add all of the fluid states
            crudeOil = defineFluids(data[index++], "oil", stickyEnabled, flammableEnabled);
            oilResidue = defineFluids(data[index++], "oil_residue", stickyEnabled, flammableEnabled);
            oilHeavy = defineFluids(data[index++], "oil_heavy", stickyEnabled, flammableEnabled);
            oilDense = defineFluids(data[index++], "oil_dense", stickyEnabled, flammableEnabled);
            oilDistilled = defineFluids(data[index++], "oil_distilled", stickyEnabled, flammableEnabled);
            fuelDense = defineFluids(data[index++], "fuel_dense", stickyEnabled, flammableEnabled);
            fuelMixedHeavy = defineFluids(data[index++], "fuel_mixed_heavy", stickyEnabled, flammableEnabled);
            fuelLight = defineFluids(data[index++], "fuel_light", stickyEnabled, flammableEnabled);
            fuelMixedLight = defineFluids(data[index++], "fuel_mixed_light", stickyEnabled, flammableEnabled);
            fuelGaseous = defineFluids(data[index++], "fuel_gaseous", stickyEnabled, flammableEnabled);
        }
    }

    private static BCFluid[] defineFluids(int[] data, String name, boolean stickyEnabled, boolean flammableEnabled) {
        BCFluid[] arr = new BCFluid[3];
        for (int h = 0; h < 3; h++) {
            arr[h] = defineFluid(data, h, name, stickyEnabled, flammableEnabled);
        }
        return arr;
    }

    private static BCFluid defineFluid(int[] data, int heat, String name, boolean stickyEnabled, boolean flammableEnabled) {
        final int density = data[0];
        final int baseViscosity = data[1];
        final int boilPoint = data[2];
        final int baseQuanta = data[3];
        final int texLight = data[4];
        final int texDark = data[5];
        final boolean sticky = stickyEnabled && data[6] == 1;
        final boolean flammable = flammableEnabled && data[7] == 1;

        String fullName = name + (heat == 0 ? "" : "_heat_" + heat);
        int tempAdjustedViscosity = baseViscosity * (4 - heat) / 4;
        int boilAdjustedDensity = density * (heat >= boilPoint ? -1 : 1);

        String fluidTexture = "buildcraftenergy:blocks/fluids/" + name + "_heat_" + heat;
        BCFluid def = new BCFluid(fullName, new Identifier(fluidTexture + "_still"),
            new Identifier(fluidTexture + "_flow"));
        def.setBlockName(name + "_heat_" + heat);
        def.setMapColour(getMapColor(texDark));
        def.setFlammable(flammable);
        def.setHeat(heat);
        def.setUnlocalizedName(name);
        def.setTemperature(300 + 20 * heat);
        def.setViscosity(tempAdjustedViscosity);
        def.setDensity(boilAdjustedDensity);
        def.setColour(texLight, texDark);
        def.setHeatable(true);
        def.setLightOpacity(3);
        def.setQuantaPerBlock(baseQuanta + (baseQuanta > 6 ? heat : heat / 2));
        FluidManager.register(def);

        BCFluidBlock block = (BCFluidBlock) def.getBlock();
        block.setSticky(sticky);

        allFluids.add(def);
        return def;
    }

    private static MapColor getMapColor(int color) {
        MapColor bestMapColor = MapColor.BLACK;
        int currentDifference = Integer.MAX_VALUE;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        for (int mapId = 0; mapId < 64; mapId++) {
            MapColor mapColor = MapColor.get(mapId);
            if (mapColor == null || mapColor.color == 0) {
                continue;
            }
            int mr = (mapColor.color >> 16) & 0xFF;
            int mg = (mapColor.color >> 8) & 0xFF;
            int mb = mapColor.color & 0xFF;

            int dr = mr - r;
            int dg = mg - g;
            int db = mb - b;

            int difference = dr * dr + dg * dg + db * db;

            if (difference < currentDifference) {
                currentDifference = difference;
                bestMapColor = mapColor;
            }
        }
        return bestMapColor;
    }
}
