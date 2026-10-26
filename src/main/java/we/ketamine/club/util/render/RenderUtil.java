package we.ketamine.club.util.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.awt.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import we.ketamine.club.mixin.client.GuiGraphicsExtractorAccessor;
import we.ketamine.club.util.render.state.ColoredQuadBatchRenderState;
import we.ketamine.club.util.traits.Util;

public class RenderUtil implements Util {
   public RenderUtil() {
   }

   public static void rect(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, int color) {
      int ix1 = Math.round(x1);
      int iy1 = Math.round(y1);
      int ix2 = Math.round(x2);
      int iy2 = Math.round(y2);
      context.fill(ix1, iy1, ix2, iy2, GuiFade.apply(color));
   }

   public static void rect(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, int color, float width) {
      int w = Math.max(1, Math.round(width));
      int c = GuiFade.apply(color);
      ObjectList<ColoredQuadBatchRenderState.ColoredQuad> quads = new ObjectArrayList(4);
      int left = Math.round(x1);
      int top = Math.round(y1);
      int right = Math.round(x2);
      int bottom = Math.round(y2);
      addQuad(quads, left, top, right, top + w, c);
      addQuad(quads, right - w, top, right, bottom, c);
      addQuad(quads, left, bottom - w, right, bottom, c);
      addQuad(quads, left, top, left + w, bottom, c);
      submitColoredQuads(context, quads);
   }

   public static void chamferedRect(
      GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, int color, int clip, boolean cutTop, boolean cutBottom
   ) {
      int left = Math.round(x1);
      int top = Math.round(y1);
      int right = Math.round(x2);
      int bottom = Math.round(y2);
      int height = bottom - top;
      if (right > left && height > 0) {
         int cutCount = (cutTop ? 1 : 0) + (cutBottom ? 1 : 0);
         int corner = Math.max(0, Math.min(clip, Math.min((right - left) / 2, cutCount == 0 ? height : height / cutCount)));
         int fadedColor = GuiFade.apply(color);
         int edgeColor = GuiFade.apply(withAlpha(color, Math.round((color >>> 24 & 0xFF) * 0.45F)));
         int centerTop = top + (cutTop ? corner : 0);
         int centerBottom = bottom - (cutBottom ? corner : 0);
         ObjectList<ColoredQuadBatchRenderState.ColoredQuad> quads = new ObjectArrayList(1 + corner * 6);
         if (centerBottom > centerTop) {
            addQuad(quads, left, centerTop, right, centerBottom, fadedColor);
         }

         for (int row = 0; cutTop && row < corner; row++) {
            int inset = roundedInset(corner, row);
            addQuad(quads, left + inset, top + row, right - inset, top + row + 1, fadedColor);
            if (inset > 0) {
               addQuad(quads, left + inset - 1, top + row, left + inset, top + row + 1, edgeColor);
               addQuad(quads, right - inset, top + row, right - inset + 1, top + row + 1, edgeColor);
            }
         }

         for (int rowx = 0; cutBottom && rowx < corner; rowx++) {
            int y = bottom - corner + rowx;
            int inset = roundedInset(corner, corner - rowx - 1);
            addQuad(quads, left + inset, y, right - inset, y + 1, fadedColor);
            if (inset > 0) {
               addQuad(quads, left + inset - 1, y, left + inset, y + 1, edgeColor);
               addQuad(quads, right - inset, y, right - inset + 1, y + 1, edgeColor);
            }
         }

         submitColoredQuads(context, quads);
      }
   }

   public static void chamferedOutline(
      GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, int color, int clip, boolean cutTop, boolean cutBottom
   ) {
      int left = Math.round(x1);
      int top = Math.round(y1);
      int right = Math.round(x2);
      int bottom = Math.round(y2);
      int height = bottom - top;
      int cutCount = (cutTop ? 1 : 0) + (cutBottom ? 1 : 0);
      int corner = Math.max(0, Math.min(clip, Math.min((right - left) / 2, cutCount == 0 ? height : height / cutCount)));
      int fadedColor = GuiFade.apply(color);
      int edgeColor = GuiFade.apply(withAlpha(color, Math.round((color >>> 24 & 0xFF) * 0.45F)));
      int topInset = cutTop ? roundedInset(corner, 0) : 0;
      int bottomInset = cutBottom ? roundedInset(corner, 0) : 0;
      ObjectList<ColoredQuadBatchRenderState.ColoredQuad> quads = new ObjectArrayList(4 + corner * 8);
      addQuad(quads, left + topInset, top, right - topInset, top + 1, fadedColor);
      addQuad(quads, left + bottomInset, bottom - 1, right - bottomInset, bottom, fadedColor);

      for (int row = 0; row < corner; row++) {
         if (cutTop && row > 0) {
            int inset = roundedInset(corner, row);
            addQuad(quads, left + inset, top + row, left + inset + 1, top + row + 1, fadedColor);
            addQuad(quads, right - inset - 1, top + row, right - inset, top + row + 1, fadedColor);
         }

         if (cutTop) {
            int inset = roundedInset(corner, row);
            if (inset > 0) {
               addQuad(quads, left + inset - 1, top + row, left + inset, top + row + 1, edgeColor);
               addQuad(quads, right - inset, top + row, right - inset + 1, top + row + 1, edgeColor);
            }
         }

         if (cutBottom && row < corner - 1) {
            int y = bottom - corner + row;
            int inset = roundedInset(corner, corner - row - 1);
            addQuad(quads, left + inset, y, left + inset + 1, y + 1, fadedColor);
            addQuad(quads, right - inset - 1, y, right - inset, y + 1, fadedColor);
         }

         if (cutBottom) {
            int y = bottom - corner + row;
            int inset = roundedInset(corner, corner - row - 1);
            if (inset > 0) {
               addQuad(quads, left + inset - 1, y, left + inset, y + 1, edgeColor);
               addQuad(quads, right - inset, y, right - inset + 1, y + 1, edgeColor);
            }
         }
      }

      addQuad(quads, left, top + (cutTop ? corner : 0), left + 1, bottom - (cutBottom ? corner : 0), fadedColor);
      addQuad(quads, right - 1, top + (cutTop ? corner : 0), right, bottom - (cutBottom ? corner : 0), fadedColor);
      submitColoredQuads(context, quads);
   }

   private static void addQuad(ObjectList<ColoredQuadBatchRenderState.ColoredQuad> quads, int x1, int y1, int x2, int y2, int color) {
      if (x2 > x1 && y2 > y1) {
         quads.add(new ColoredQuadBatchRenderState.ColoredQuad(x1, y1, x2, y2, color));
      }
   }

   private static void submitColoredQuads(GuiGraphicsExtractor context, ObjectList<ColoredQuadBatchRenderState.ColoredQuad> quads) {
      if (!quads.isEmpty()) {
         context.guiRenderState
            .addGuiElement(
               new ColoredQuadBatchRenderState(
                  RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(context.pose()), quads, context.scissorStack.peek()
               )
            );
      }
   }

   private static int roundedInset(int radius, int row) {
      if (radius <= 0) {
         return 0;
      } else {
         double distance = radius - row - 0.5;
         double inside = Math.max(0.0, radius * radius - distance * distance);
         return Math.max(0, (int)Math.ceil(radius - Math.sqrt(inside)));
      }
   }

   private static int withAlpha(int color, int alpha) {
      return Math.max(0, Math.min(255, alpha)) << 24 | color & 16777215;
   }

   public static void horizontalGradient(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, Color left, Color right) {
      int ix1 = Math.round(x1);
      int iy1 = Math.round(y1);
      int ix2 = Math.round(x2);
      int iy2 = Math.round(y2);
      ((GuiGraphicsExtractorAccessor)context)
         .ketamineclub$innerFill(
            Pipelines.GUI_HORIZONTAL_GRADIENT_PIPELINE,
            TextureSetup.noTexture(),
            ix1,
            iy1,
            ix2,
            iy2,
            GuiFade.apply(left.getRGB()),
            GuiFade.apply(right.getRGB())
         );
   }

   public static void verticalGradient(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, Color top, Color bottom) {
      int ix1 = Math.round(x1);
      int iy1 = Math.round(y1);
      int ix2 = Math.round(x2);
      int iy2 = Math.round(y2);
      context.fillGradient(ix1, iy1, ix2, iy2, GuiFade.apply(top.getRGB()), GuiFade.apply(bottom.getRGB()));
   }

   public static boolean isHorizontalGradientPipeline(RenderPipeline pipeline) {
      return pipeline == Pipelines.GUI_HORIZONTAL_GRADIENT_PIPELINE;
   }

   public static void shadow(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, int radius, int alpha) {
      if (radius > 0 && alpha > 0) {
         int ix1 = Math.round(x1);
         int iy1 = Math.round(y1);
         int ix2 = Math.round(x2);
         int iy2 = Math.round(y2);
         int solid = Math.min(255, alpha) << 24;
         int clear = 0;
         gradient(context, ix1, iy1 - radius, ix2, iy1, clear, solid, solid, clear);
         gradient(context, ix1, iy2, ix2, iy2 + radius, solid, clear, clear, solid);
         gradient(context, ix1 - radius, iy1, ix1, iy2, clear, clear, solid, solid);
         gradient(context, ix2, iy1, ix2 + radius, iy2, solid, solid, clear, clear);
         int cornerAlpha = (int)(alpha * 0.7F) << 24;
         gradient(context, ix1 - radius, iy1 - radius, ix1, iy1, clear, clear, cornerAlpha, clear);
         gradient(context, ix2, iy1 - radius, ix2 + radius, iy1, clear, clear, clear, cornerAlpha);
         gradient(context, ix1 - radius, iy2, ix1, iy2 + radius, clear, cornerAlpha, clear, clear);
         gradient(context, ix2, iy2, ix2 + radius, iy2 + radius, cornerAlpha, clear, clear, clear);
      }
   }

   public static void gradient(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int topLeft, int bottomLeft, int bottomRight, int topRight) {
      int width = x2 - x1;
      if (width > 0 && y2 > y1) {
         for (int x = x1; x < x2; x++) {
            float delta = width == 1 ? 0.0F : (float)(x - x1) / (width - 1);
            graphics.fillGradient(
               x, y1, x + 1, y2, GuiFade.apply(lerpColor(topLeft, topRight, delta)), GuiFade.apply(lerpColor(bottomLeft, bottomRight, delta))
            );
         }
      }
   }

   private static int lerpColor(int from, int to, float delta) {
      int a = Math.round((from >>> 24 & 0xFF) + ((to >>> 24 & 0xFF) - (from >>> 24 & 0xFF)) * delta);
      int r = Math.round((from >>> 16 & 0xFF) + ((to >>> 16 & 0xFF) - (from >>> 16 & 0xFF)) * delta);
      int g = Math.round((from >>> 8 & 0xFF) + ((to >>> 8 & 0xFF) - (from >>> 8 & 0xFF)) * delta);
      int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * delta);
      return a << 24 | r << 16 | g << 8 | b;
   }

   public static void rect(PoseStack stack, float x1, float y1, float x2, float y2, int color) {
      rectFilled(stack, x1, y1, x2, y2, color);
   }

   public static void rect(PoseStack stack, float x1, float y1, float x2, float y2, int color, float width) {
      drawHorizontalLine(stack, x1, x2, y1, color, width);
      drawVerticalLine(stack, x2, y1, y2, color, width);
      drawHorizontalLine(stack, x1, x2, y2, color, width);
      drawVerticalLine(stack, x1, y1, y2, color, width);
   }

   protected static void drawHorizontalLine(PoseStack matrices, float x1, float x2, float y, int color) {
      if (x2 < x1) {
         float i = x1;
         x1 = x2;
         x2 = i;
      }

      rectFilled(matrices, x1, y, x2 + 1.0F, y + 1.0F, color);
   }

   protected static void drawVerticalLine(PoseStack matrices, float x, float y1, float y2, int color) {
      if (y2 < y1) {
         float i = y1;
         y1 = y2;
         y2 = i;
      }

      rectFilled(matrices, x, y1 + 1.0F, x + 1.0F, y2, color);
   }

   protected static void drawHorizontalLine(PoseStack matrices, float x1, float x2, float y, int color, float width) {
      if (x2 < x1) {
         float i = x1;
         x1 = x2;
         x2 = i;
      }

      rectFilled(matrices, x1, y, x2 + width, y + width, color);
   }

   protected static void drawVerticalLine(PoseStack matrices, float x, float y1, float y2, int color, float width) {
      if (y2 < y1) {
         float i = y1;
         y1 = y2;
         y2 = i;
      }

      rectFilled(matrices, x, y1 + width, x + width, y2, color);
   }

   public static void rectFilled(PoseStack matrix, float x1, float y1, float x2, float y2, int color) {
      if (x1 < x2) {
         float i = x1;
         x1 = x2;
         x2 = i;
      }

      if (y1 < y2) {
         float i = y1;
         y1 = y2;
         y2 = i;
      }

      float f = (color >> 24 & 0xFF) / 255.0F;
      float g = (color >> 16 & 0xFF) / 255.0F;
      float h = (color >> 8 & 0xFF) / 255.0F;
      float j = (color & 0xFF) / 255.0F;
      ByteBufferBuilder byteBuffer = new ByteBufferBuilder(DefaultVertexFormat.POSITION_COLOR.getVertexSize() * 4);

      try {
         BufferBuilder bufferBuilder = new BufferBuilder(byteBuffer, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
         bufferBuilder.addVertex(matrix.last().pose(), x1, y2, 0.0F).setColor(g, h, j, f);
         bufferBuilder.addVertex(matrix.last().pose(), x2, y2, 0.0F).setColor(g, h, j, f);
         bufferBuilder.addVertex(matrix.last().pose(), x2, y1, 0.0F).setColor(g, h, j, f);
         bufferBuilder.addVertex(matrix.last().pose(), x1, y1, 0.0F).setColor(g, h, j, f);
         Layers.quads().draw(bufferBuilder.buildOrThrow());
      } catch (Throwable var15) {
         try {
            byteBuffer.close();
         } catch (Throwable var14) {
            var15.addSuppressed(var14);
         }

         throw var15;
      }

      byteBuffer.close();
   }

   public static void horizontalGradient(PoseStack matrix, float x1, float y1, float x2, float y2, Color left, Color right) {
      ByteBufferBuilder byteBuffer = new ByteBufferBuilder(DefaultVertexFormat.POSITION_COLOR.getVertexSize() * 4);

      try {
         BufferBuilder bufferBuilder = new BufferBuilder(byteBuffer, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
         bufferBuilder.addVertex(matrix.last().pose(), x1, y1, 0.0F)
            .setColor(left.getRed() / 255.0F, left.getGreen() / 255.0F, left.getBlue() / 255.0F, left.getAlpha() / 255.0F);
         bufferBuilder.addVertex(matrix.last().pose(), x1, y2, 0.0F)
            .setColor(left.getRed() / 255.0F, left.getGreen() / 255.0F, left.getBlue() / 255.0F, left.getAlpha() / 255.0F);
         bufferBuilder.addVertex(matrix.last().pose(), x2, y2, 0.0F)
            .setColor(right.getRed() / 255.0F, right.getGreen() / 255.0F, right.getBlue() / 255.0F, right.getAlpha() / 255.0F);
         bufferBuilder.addVertex(matrix.last().pose(), x2, y1, 0.0F)
            .setColor(right.getRed() / 255.0F, right.getGreen() / 255.0F, right.getBlue() / 255.0F, right.getAlpha() / 255.0F);
         Layers.quads().draw(bufferBuilder.buildOrThrow());
      } catch (Throwable var11) {
         try {
            byteBuffer.close();
         } catch (Throwable var10) {
            var11.addSuppressed(var10);
         }

         throw var11;
      }

      byteBuffer.close();
   }

   public static void verticalGradient(PoseStack matrix, float x1, float y1, float x2, float y2, Color top, Color bottom) {
      ByteBufferBuilder byteBuffer = new ByteBufferBuilder(DefaultVertexFormat.POSITION_COLOR.getVertexSize() * 4);

      try {
         BufferBuilder bufferBuilder = new BufferBuilder(byteBuffer, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
         bufferBuilder.addVertex(matrix.last().pose(), x1, y1, 0.0F)
            .setColor(top.getRed() / 255.0F, top.getGreen() / 255.0F, top.getBlue() / 255.0F, top.getAlpha() / 255.0F);
         bufferBuilder.addVertex(matrix.last().pose(), x1, y2, 0.0F)
            .setColor(bottom.getRed() / 255.0F, bottom.getGreen() / 255.0F, bottom.getBlue() / 255.0F, bottom.getAlpha() / 255.0F);
         bufferBuilder.addVertex(matrix.last().pose(), x2, y2, 0.0F)
            .setColor(bottom.getRed() / 255.0F, bottom.getGreen() / 255.0F, bottom.getBlue() / 255.0F, bottom.getAlpha() / 255.0F);
         bufferBuilder.addVertex(matrix.last().pose(), x2, y1, 0.0F)
            .setColor(top.getRed() / 255.0F, top.getGreen() / 255.0F, top.getBlue() / 255.0F, top.getAlpha() / 255.0F);
         Layers.quads().draw(bufferBuilder.buildOrThrow());
      } catch (Throwable var11) {
         try {
            byteBuffer.close();
         } catch (Throwable var10) {
            var11.addSuppressed(var10);
         }

         throw var11;
      }

      byteBuffer.close();
   }

   public static void drawBoxFilled(PoseStack stack, AABB box, Color c) {
      Gizmos.cuboid(box, GizmoStyle.fill(c.getRGB())).setAlwaysOnTop();
   }

   public static void drawBoxFilled(PoseStack stack, Vec3 vec, Color c) {
      drawBoxFilled(stack, AABB.unitCubeFromLowerCorner(vec), c);
   }

   public static void drawBoxFilled(PoseStack stack, BlockPos bp, Color c) {
      drawBoxFilled(stack, new AABB(bp), c);
   }

   public static void drawBox(PoseStack stack, AABB box, Color c, float lineWidth) {
      Gizmos.cuboid(box, GizmoStyle.stroke(c.getRGB(), lineWidth)).setAlwaysOnTop();
   }

   public static void drawBox(PoseStack stack, Vec3 vec, Color c, float lineWidth) {
      drawBox(stack, AABB.unitCubeFromLowerCorner(vec), c, lineWidth);
   }

   public static void drawBox(PoseStack stack, BlockPos bp, Color c, float lineWidth) {
      drawBox(stack, new AABB(bp), c, lineWidth);
   }

   public static void drawLine(Vec3 from, Vec3 to, Color c, float lineWidth) {
      Gizmos.line(from, to, c.getRGB(), lineWidth).setAlwaysOnTop();
   }

   private static void worldVertex(BufferBuilder bb, Matrix4f m, Vec3 cam, double x, double y, double z, Color c) {
      bb.addVertex(m, (float)(x - cam.x), (float)(y - cam.y), (float)(z - cam.z))
         .setColor(c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F, c.getAlpha() / 255.0F);
   }

   public static void quadHorizontal(PoseStack stack, double x1, double z1, double x2, double z2, double y, Color c) {
      Vec3 cam = mc.gameRenderer.mainCamera().position();
      Matrix4f m = stack.last().pose();
      ByteBufferBuilder byteBuffer = new ByteBufferBuilder(DefaultVertexFormat.POSITION_COLOR.getVertexSize() * 4);

      try {
         BufferBuilder bufferBuilder = new BufferBuilder(byteBuffer, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
         worldVertex(bufferBuilder, m, cam, x1, y, z1, c);
         worldVertex(bufferBuilder, m, cam, x2, y, z1, c);
         worldVertex(bufferBuilder, m, cam, x2, y, z2, c);
         worldVertex(bufferBuilder, m, cam, x1, y, z2, c);
         Layers.quads().draw(bufferBuilder.buildOrThrow());
      } catch (Throwable var18) {
         try {
            byteBuffer.close();
         } catch (Throwable var17) {
            var18.addSuppressed(var17);
         }

         throw var18;
      }

      byteBuffer.close();
   }

   public static void gradientQuadVertical(PoseStack stack, double x1, double z1, double x2, double z2, double yTop, double yBottom, Color top, Color bottom) {
      Vec3 cam = mc.gameRenderer.mainCamera().position();
      Matrix4f m = stack.last().pose();
      ByteBufferBuilder byteBuffer = new ByteBufferBuilder(DefaultVertexFormat.POSITION_COLOR.getVertexSize() * 4);

      try {
         BufferBuilder bufferBuilder = new BufferBuilder(byteBuffer, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
         worldVertex(bufferBuilder, m, cam, x1, yTop, z1, top);
         worldVertex(bufferBuilder, m, cam, x2, yTop, z2, top);
         worldVertex(bufferBuilder, m, cam, x2, yBottom, z2, bottom);
         worldVertex(bufferBuilder, m, cam, x1, yBottom, z1, bottom);
         Layers.quads().draw(bufferBuilder.buildOrThrow());
      } catch (Throwable var21) {
         try {
            byteBuffer.close();
         } catch (Throwable var20) {
            var21.addSuppressed(var20);
         }

         throw var21;
      }

      byteBuffer.close();
   }

   public static PoseStack matrixFrom(Vec3 pos) {
      PoseStack matrices = new PoseStack();
      Camera camera = mc.gameRenderer.mainCamera();
      matrices.mulPose(Axis.XP.rotationDegrees(camera.xRot()));
      matrices.mulPose(Axis.YP.rotationDegrees(camera.yRot() + 180.0F));
      matrices.translate(pos.x() - camera.position().x, pos.y() - camera.position().y, pos.z() - camera.position().z);
      return matrices;
   }
}
