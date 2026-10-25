/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.core.client.render;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.math.*;
import buildcraft.lib.client.render.DetachedRenderer;

/** Native translucent atlas geometry in the active world-render frame. */
public final class VolumeRender {
    private VolumeRender(){}
    public static Sprite white(){return MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(new net.minecraft.util.Identifier("buildcraftlib:blocks/white"));}
    public static void cube(Box box,Sprite sprite,float alpha){for(var side:Direction.values())face(box,side,sprite,alpha);}
    public static void face(Box box,Direction side,Sprite sprite,float alpha){
        var context=DetachedRenderer.context();var camera=context.camera().getPos();var matrix=context.matrixStack().peek();
        var vertices=MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayer.getEntityTranslucent(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
        double a=box.minX,b=box.minY,c=box.minZ,d=box.maxX,e=box.maxY,f=box.maxZ;
        double[][] points=switch(side){
            case NORTH->new double[][]{{a,e,c},{d,e,c},{d,b,c},{a,b,c}};
            case SOUTH->new double[][]{{a,b,f},{d,b,f},{d,e,f},{a,e,f}};
            case DOWN->new double[][]{{a,b,c},{d,b,c},{d,b,f},{a,b,f}};
            case UP->new double[][]{{a,e,f},{d,e,f},{d,e,c},{a,e,c}};
            case WEST->new double[][]{{a,b,f},{a,e,f},{a,e,c},{a,b,c}};
            case EAST->new double[][]{{d,b,c},{d,e,c},{d,e,f},{d,b,f}};
        };
        float shade=switch(side){case UP->1;case DOWN->.5f;case NORTH,SOUTH->.8f;default->.6f;};
        float[] u={sprite.getMinU(),sprite.getMaxU(),sprite.getMaxU(),sprite.getMinU()},v={sprite.getMinV(),sprite.getMinV(),sprite.getMaxV(),sprite.getMaxV()};
        for(int i=0;i<4;i++)vertices.vertex(matrix.getPositionMatrix(),(float)(points[i][0]-camera.x),(float)(points[i][1]-camera.y),(float)(points[i][2]-camera.z))
            .color(shade,shade,shade,alpha).texture(u[i],v[i]).overlay(OverlayTexture.DEFAULT_UV).light(15728880)
            .normal(matrix.getNormalMatrix(),side.getOffsetX(),side.getOffsetY(),side.getOffsetZ()).next();
    }
}
