/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.filler;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import buildcraft.api.filler.IFillerPattern;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.containers.IFillerStatementContainer;

import buildcraft.lib.statement.FullStatement;

import buildcraft.builders.snapshot.Template;

public class FillerUtil {
    public static Template.BuildingInfo createBuildingInfo(IFillerStatementContainer filler,
                                                           FullStatement<IFillerPattern> patternStatement,
                                                           IStatementParameter[] params,
                                                           boolean inverted) {
        if (!filler.hasBox()) return null;
        try {
            buildcraft.builders.snapshot.Snapshot.getDataSize(filler.getBox().size());
        } catch (IllegalArgumentException invalidSize) {
            return null;
        }
        Template.FilledTemplate filledTemplate = (Template.FilledTemplate) patternStatement.get().createTemplate(
            filler,
            params
        );
        if (filledTemplate == null) {
            return null;
        }
        if (inverted) {
            filledTemplate.getTemplate().invert();
        }
        filledTemplate.getTemplate().computeKey();
        return filledTemplate.getTemplate().new BuildingInfo(BlockPos.ORIGIN, BlockRotation.NONE);
    }
}
