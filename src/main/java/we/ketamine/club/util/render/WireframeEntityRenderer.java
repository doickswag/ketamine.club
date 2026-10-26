package we.ketamine.club.util.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState;
import net.minecraft.client.renderer.entity.state.EntityRenderState.ShadowPiece;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives.Group;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import we.ketamine.club.util.traits.Util;

public final class WireframeEntityRenderer implements Util {
   private WireframeEntityRenderer() {
   }

   public static List<float[][]> render(
      PoseStack eventStack, Entity entity, Vec3 pos, List<float[][]> quads, float partialTick, Color sideColor, Color lineColor, float lineWidth
   ) {
      if (quads == null) {
         quads = captureGeometry(entity, partialTick);
      }

      if (quads.isEmpty()) {
         return quads;
      } else {
         Vec3 cam = mc.gameRenderer.mainCamera().position();
         float dx = (float)(pos.x - cam.x());
         float dy = (float)(pos.y - cam.y());
         float dz = (float)(pos.z - cam.z());
         eventStack.pushPose();

         try {
            eventStack.translate(dx, dy, dz);
            Pose pose = eventStack.last();
            if (sideColor.getAlpha() > 0) {
               int capacity = DefaultVertexFormat.POSITION_COLOR.getVertexSize() * quads.size() * 4;
               ByteBufferBuilder byteBuffer = new ByteBufferBuilder(capacity);

               try {
                  BufferBuilder fill = new BufferBuilder(byteBuffer, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);

                  for (float[][] q : quads) {
                     for (float[] v : q) {
                        fill.addVertex(pose, v[0], v[1], v[2]).setColor(sideColor.getRGB());
                     }
                  }

                  Layers.quads().draw(fill.buildOrThrow());
               } catch (Throwable var33) {
                  try {
                     byteBuffer.close();
                  } catch (Throwable var31) {
                     var33.addSuppressed(var31);
                  }

                  throw var33;
               }

               byteBuffer.close();
            }

            int capacity = DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH.getVertexSize() * quads.size() * 8;
            ByteBufferBuilder byteBuffer = new ByteBufferBuilder(capacity);

            try {
               BufferBuilder edges = new BufferBuilder(byteBuffer, PrimitiveTopology.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH);

               for (float[][] q : quads) {
                  for (int i = 0; i < 4; i++) {
                     float[] v1 = q[i];
                     float[] v2 = q[(i + 1) % 4];
                     float normalX = v2[0] - v1[0];
                     float normalY = v2[1] - v1[1];
                     float normalZ = v2[2] - v1[2];
                     edges.addVertex(pose, v1[0], v1[1], v1[2]).setColor(lineColor.getRGB()).setNormal(pose, normalX, normalY, normalZ).setLineWidth(lineWidth);
                     edges.addVertex(pose, v2[0], v2[1], v2[2]).setColor(lineColor.getRGB()).setNormal(pose, normalX, normalY, normalZ).setLineWidth(lineWidth);
                  }
               }

               Layers.lines().draw(edges.buildOrThrow());
            } catch (Throwable var32) {
               try {
                  byteBuffer.close();
               } catch (Throwable var30) {
                  var32.addSuppressed(var30);
               }

               throw var32;
            }

            byteBuffer.close();
         } finally {
            eventStack.popPose();
         }

         return quads;
      }
   }

   private static List<float[][]> captureGeometry(Entity entity, float partialTick) {
      try {
         CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
         if (camera != null && camera.initialized) {
            EntityRenderState state = mc.getEntityRenderDispatcher().extractEntity(entity, partialTick);
            WireframeEntityRenderer.CapturingCollector collector = new WireframeEntityRenderer.CapturingCollector();
            mc.getEntityRenderDispatcher().submit(state, camera, 0.0, 0.0, 0.0, new PoseStack(), collector);
            return collector.consumer.quads;
         } else {
            return new ArrayList<>();
         }
      } catch (Exception var5) {
         return new ArrayList<>();
      }
   }

   private static final class CapturingCollector implements SubmitNodeCollector {
      final WireframeEntityRenderer.CapturingConsumer consumer = new WireframeEntityRenderer.CapturingConsumer();

      private CapturingCollector() {
      }

      public OrderedSubmitNodeCollector order(int order) {
         return this;
      }

      public <S> void submitModel(
         Model<? super S> model,
         S modelState,
         PoseStack poseStack,
         RenderType renderType,
         int packedLight,
         int overlay,
         int tintColor,
         TextureAtlasSprite sprite,
         int outlineColor,
         CrumblingOverlay crumbling
      ) {
         model.setupAnim(modelState);
         if (model instanceof HumanoidModel<?> humanoid) {
            this.renderFirstLayerOnly(humanoid, poseStack, packedLight, overlay, tintColor);
         } else {
            model.renderToBuffer(poseStack, this.consumer, packedLight, overlay, tintColor);
         }
      }

      public void submitModelPart(
         ModelPart part,
         PoseStack poseStack,
         RenderType renderType,
         int packedLight,
         int overlay,
         TextureAtlasSprite sprite,
         int tintColor,
         CrumblingOverlay crumbling,
         int outlineColor
      ) {
         part.render(poseStack, this.consumer, packedLight, overlay, tintColor);
      }

      private void renderFirstLayerOnly(HumanoidModel<?> model, PoseStack poseStack, int light, int overlay, int tintColor) {
         boolean hatVisible = model.hat.visible;
         model.hat.visible = false;
         boolean jacketVisible = false;
         boolean leftSleeveVisible = false;
         boolean rightSleeveVisible = false;
         boolean leftPantsVisible = false;
         boolean rightPantsVisible = false;
         if (model instanceof PlayerModel playerModel) {
            jacketVisible = playerModel.jacket.visible;
            leftSleeveVisible = playerModel.leftSleeve.visible;
            rightSleeveVisible = playerModel.rightSleeve.visible;
            leftPantsVisible = playerModel.leftPants.visible;
            rightPantsVisible = playerModel.rightPants.visible;
            playerModel.jacket.visible = false;
            playerModel.leftSleeve.visible = false;
            playerModel.rightSleeve.visible = false;
            playerModel.leftPants.visible = false;
            playerModel.rightPants.visible = false;
         }

         try {
            model.renderToBuffer(poseStack, this.consumer, light, overlay, tintColor);
         } finally {
            model.hat.visible = hatVisible;
            if (model instanceof PlayerModel playerModel) {
               playerModel.jacket.visible = jacketVisible;
               playerModel.leftSleeve.visible = leftSleeveVisible;
               playerModel.rightSleeve.visible = rightSleeveVisible;
               playerModel.leftPants.visible = leftPantsVisible;
               playerModel.rightPants.visible = rightPantsVisible;
            }
         }
      }

      public void submitShadow(PoseStack p, float r, List<ShadowPiece> s) {
      }

      public void submitNameTag(PoseStack p, Vec3 pos, int bg, Component text, boolean player, int color, CameraRenderState cam) {
      }

      public void submitText(
         PoseStack p, float x, float y, FormattedCharSequence text, boolean shadow, DisplayMode mode, int bg, int fg, int light, int lineHeight
      ) {
      }

      public void submitFlame(PoseStack p, EntityRenderState s, Quaternionf q) {
      }

      public void submitLeash(PoseStack p, LeashState l) {
      }

      public void submitMovingBlock(PoseStack p, MovingBlockRenderState state, int flags) {
      }

      public void submitBlockModel(PoseStack p, RenderType r, List<BlockStateModelPart> parts, int[] tints, int light, int overlay, int outlineColor) {
      }

      public void submitBreakingBlockModel(PoseStack p, List<BlockStateModelPart> parts, int progress) {
      }

      public void submitShapeOutline(PoseStack p, VoxelShape shape, RenderType r, int color, float width, boolean shaded) {
      }

      public void submitItem(
         PoseStack p, ItemDisplayContext context, int light, int overlay, int outlineColor, int[] tints, List<BakedQuad> quads, FoilType foil
      ) {
      }

      public void submitCustomGeometry(PoseStack p, RenderType r, CustomGeometryRenderer geometry) {
         if (r.primitiveTopology() == PrimitiveTopology.QUADS) {
            geometry.render(p.last(), this.consumer);
         }
      }

      public void submitQuadParticleGroup(QuadParticleRenderState state) {
      }

      public void submitGizmoPrimitives(Group group, CameraRenderState camera, boolean alwaysOnTop) {
      }
   }

   private static final class CapturingConsumer implements VertexConsumer {
      final List<float[][]> quads = new ArrayList<>();
      private final float[][] pending = new float[4][];
      private int count = 0;

      private CapturingConsumer() {
      }

      private void pushVertex(float x, float y, float z) {
         this.pending[this.count++] = new float[]{x, y, z};
         if (this.count == 4) {
            this.quads
               .add(
                  new float[][]{
                     (float[])this.pending[0].clone(), (float[])this.pending[1].clone(), (float[])this.pending[2].clone(), (float[])this.pending[3].clone()
                  }
               );
            this.count = 0;
         }
      }

      public VertexConsumer addVertex(float x, float y, float z) {
         this.pushVertex(x, y, z);
         return this;
      }

      public VertexConsumer setColor(int r, int g, int b, int a) {
         return this;
      }

      public VertexConsumer setColor(int argb) {
         return this;
      }

      public VertexConsumer setUv(float u, float v) {
         return this;
      }

      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      public VertexConsumer setNormal(float nx, float ny, float nz) {
         return this;
      }

      public VertexConsumer setLineWidth(float w) {
         return this;
      }
   }
}
