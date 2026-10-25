/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team.
 * This Source Code Form is subject to the Mozilla Public License, v. 2.0.
 */
package buildcraft.lib.client.model.json;

import com.google.gson.JsonObject;
import net.minecraft.util.math.Direction;
import buildcraft.lib.expression.FunctionContext;
import buildcraft.lib.client.model.json.JsonVariableModel.ITextureGetter;

/** LED faces use a white atlas texel, with colour and emission supplied by the model. */
public class VariablePartLed extends VariablePartCuboidBase {
    public VariablePartLed(JsonObject obj, FunctionContext context) { super(obj, context); }

    @Override
    protected VariableFaceData getFaceData(Direction side, ITextureGetter textures) {
        VariableFaceData face = new VariableFaceData();
        face.sprite = textures.get("buildcraftlib:blocks/white").sprite;
        face.uvs.minU = face.uvs.maxU = 0.5F;
        face.uvs.minV = face.uvs.maxV = 0.5F;
        return face;
    }
}
