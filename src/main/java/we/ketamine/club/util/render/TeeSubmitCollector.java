package we.ketamine.club.util.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.model.Model;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

public final class TeeSubmitCollector implements SubmitNodeCollector {
   private final SubmitNodeCollector primary;
   private final SubmitNodeCollector secondary;
   private final OrderedSubmitNodeCollector tee;

   public TeeSubmitCollector(SubmitNodeCollector primary, SubmitNodeCollector secondary) {
      this.primary = primary;
      this.secondary = secondary;
      this.tee = new TeeSubmitCollector.Tee(primary, secondary);
   }

   public OrderedSubmitNodeCollector order(int order) {
      return new TeeSubmitCollector.Tee(this.primary.order(order), this.secondary.order(order));
   }

   public <S> void submitModel(
      Model<? super S> model,
      S state,
      PoseStack poseStack,
      RenderType renderType,
      int light,
      int overlay,
      int tintColor,
      TextureAtlasSprite sprite,
      int outlineColor,
      CrumblingOverlay crumbling
   ) {
      this.tee.submitModel(model, state, poseStack, renderType, light, overlay, tintColor, sprite, outlineColor, crumbling);
   }

   public void submitItem(
      PoseStack poseStack, ItemDisplayContext context, int light, int overlay, int outlineColor, int[] tintLayers, List<BakedQuad> quads, FoilType foil
   ) {
      this.tee.submitItem(poseStack, context, light, overlay, outlineColor, tintLayers, quads, foil);
   }

   public void submitShadow(PoseStack poseStack, float radius, List<ShadowPiece> pieces) {
      this.primary.submitShadow(poseStack, radius, pieces);
   }

   public void submitNameTag(PoseStack poseStack, Vec3 pos, int yOffset, Component text, boolean player, int color, CameraRenderState camera) {
      this.primary.submitNameTag(poseStack, pos, yOffset, text, player, color, camera);
   }

   public void submitText(
      PoseStack poseStack,
      float x,
      float y,
      FormattedCharSequence text,
      boolean shadow,
      DisplayMode mode,
      int background,
      int foreground,
      int light,
      int lineHeight
   ) {
      this.primary.submitText(poseStack, x, y, text, shadow, mode, background, foreground, light, lineHeight);
   }

   public void submitFlame(PoseStack poseStack, EntityRenderState state, Quaternionf rotation) {
      this.primary.submitFlame(poseStack, state, rotation);
   }

   public void submitLeash(PoseStack poseStack, LeashState state) {
      this.primary.submitLeash(poseStack, state);
   }

   public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state, int outlineColor) {
      this.primary.submitMovingBlock(poseStack, state, outlineColor);
   }

   public void submitBlockModel(
      PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tints, int light, int overlay, int outlineColor
   ) {
      this.primary.submitBlockModel(poseStack, renderType, parts, tints, light, overlay, outlineColor);
   }

   public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress) {
      this.primary.submitBreakingBlockModel(poseStack, parts, progress);
   }

   public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
      this.primary.submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
   }

   public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
      this.primary.submitCustomGeometry(poseStack, renderType, renderer);
   }

   public void submitQuadParticleGroup(QuadParticleRenderState state) {
      this.primary.submitQuadParticleGroup(state);
   }

   public void submitGizmoPrimitives(Group group, CameraRenderState camera, boolean alwaysOnTop) {
      this.primary.submitGizmoPrimitives(group, camera, alwaysOnTop);
   }

   private record Tee(OrderedSubmitNodeCollector primary, OrderedSubmitNodeCollector secondary) implements OrderedSubmitNodeCollector {
      public <S> void submitModel(
         Model<? super S> model,
         S state,
         PoseStack poseStack,
         RenderType renderType,
         int light,
         int overlay,
         int tintColor,
         TextureAtlasSprite sprite,
         int outlineColor,
         CrumblingOverlay crumbling
      ) {
         this.primary.submitModel(model, state, poseStack, renderType, light, overlay, tintColor, sprite, outlineColor, crumbling);
         this.secondary.submitModel(model, state, poseStack, renderType, light, overlay, tintColor, sprite, outlineColor, crumbling);
      }

      public void submitItem(
         PoseStack poseStack, ItemDisplayContext context, int light, int overlay, int outlineColor, int[] tintLayers, List<BakedQuad> quads, FoilType foil
      ) {
         this.primary.submitItem(poseStack, context, light, overlay, outlineColor, tintLayers, quads, foil);
         this.secondary.submitItem(poseStack, context, light, overlay, outlineColor, tintLayers, quads, foil);
      }

      public void submitShadow(PoseStack poseStack, float radius, List<ShadowPiece> pieces) {
         this.primary.submitShadow(poseStack, radius, pieces);
      }

      public void submitNameTag(PoseStack poseStack, Vec3 pos, int yOffset, Component text, boolean player, int color, CameraRenderState camera) {
         this.primary.submitNameTag(poseStack, pos, yOffset, text, player, color, camera);
      }

      public void submitText(
         PoseStack poseStack,
         float x,
         float y,
         FormattedCharSequence text,
         boolean shadow,
         DisplayMode mode,
         int background,
         int foreground,
         int light,
         int lineHeight
      ) {
         this.primary.submitText(poseStack, x, y, text, shadow, mode, background, foreground, light, lineHeight);
      }

      public void submitFlame(PoseStack poseStack, EntityRenderState state, Quaternionf rotation) {
         this.primary.submitFlame(poseStack, state, rotation);
      }

      public void submitLeash(PoseStack poseStack, LeashState state) {
         this.primary.submitLeash(poseStack, state);
      }

      public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state, int outlineColor) {
         this.primary.submitMovingBlock(poseStack, state, outlineColor);
      }

      public void submitBlockModel(
         PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tints, int light, int overlay, int outlineColor
      ) {
         this.primary.submitBlockModel(poseStack, renderType, parts, tints, light, overlay, outlineColor);
      }

      public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress) {
         this.primary.submitBreakingBlockModel(poseStack, parts, progress);
      }

      public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
         this.primary.submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
      }

      public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
         this.primary.submitCustomGeometry(poseStack, renderType, renderer);
      }

      public void submitQuadParticleGroup(QuadParticleRenderState state) {
         this.primary.submitQuadParticleGroup(state);
      }

      public void submitGizmoPrimitives(Group group, CameraRenderState camera, boolean alwaysOnTop) {
         this.primary.submitGizmoPrimitives(group, camera, alwaysOnTop);
      }
   }
}
