package we.ketamine.club.util.render.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record GlyphBatchRenderState(
   RenderPipeline pipeline,
   TextureSetup textureSetup,
   Matrix3x2f pose,
   List<GlyphBatchRenderState.GlyphQuad> quads,
   @Nullable ScreenRectangle scissorArea,
   @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
   public GlyphBatchRenderState(
      RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, List<GlyphBatchRenderState.GlyphQuad> quads, @Nullable ScreenRectangle scissorArea
   ) {
      this(pipeline, textureSetup, pose, List.copyOf(quads), scissorArea, getBounds(quads, pose, scissorArea));
   }

   public void buildVertices(VertexConsumer vertexConsumer) {
      for (GlyphBatchRenderState.GlyphQuad quad : this.quads) {
         vertexConsumer.addVertexWith2DPose(this.pose, quad.x0, quad.y0).setUv(quad.u0, quad.v0).setColor(quad.color);
         vertexConsumer.addVertexWith2DPose(this.pose, quad.x0, quad.y1).setUv(quad.u0, quad.v1).setColor(quad.color);
         vertexConsumer.addVertexWith2DPose(this.pose, quad.x1, quad.y1).setUv(quad.u1, quad.v1).setColor(quad.color);
         vertexConsumer.addVertexWith2DPose(this.pose, quad.x1, quad.y0).setUv(quad.u1, quad.v0).setColor(quad.color);
      }
   }

   @Nullable
   private static ScreenRectangle getBounds(List<GlyphBatchRenderState.GlyphQuad> quads, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
      if (quads.isEmpty()) {
         return null;
      } else {
         int minX = Integer.MAX_VALUE;
         int minY = Integer.MAX_VALUE;
         int maxX = Integer.MIN_VALUE;
         int maxY = Integer.MIN_VALUE;

         for (GlyphBatchRenderState.GlyphQuad quad : quads) {
            minX = Math.min(minX, quad.x0);
            minY = Math.min(minY, quad.y0);
            maxX = Math.max(maxX, quad.x1);
            maxY = Math.max(maxY, quad.y1);
         }

         ScreenRectangle bounds = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY).transformMaxBounds(pose);
         return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
      }
   }

   public record GlyphQuad(int x0, int y0, int x1, int y1, float u0, float v0, float u1, float v1, int color) {
   }
}
