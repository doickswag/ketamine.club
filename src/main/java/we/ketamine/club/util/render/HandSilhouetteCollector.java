package we.ketamine.club.util.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import we.ketamine.club.util.traits.Util;

public final class HandSilhouetteCollector implements SubmitNodeCollector, Util {
   private final SubmitNodeStorage storage;

   HandSilhouetteCollector(SubmitNodeStorage storage) {
      this.storage = storage;
   }

   public OrderedSubmitNodeCollector order(int order) {
      return new HandSilhouetteCollector.Ordered(this.storage.order(order));
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
      submitMaskModel(this.storage, model, state, poseStack, renderType, tintColor, sprite);
   }

   public void submitItem(
      PoseStack poseStack, ItemDisplayContext context, int light, int overlay, int outlineColor, int[] tintLayers, List<BakedQuad> quads, FoilType foil
   ) {
      submitMaskItem(this.storage, poseStack, context, tintLayers, quads);
   }

   public void submitShadow(PoseStack poseStack, float radius, List<ShadowPiece> pieces) {
   }

   public void submitNameTag(PoseStack poseStack, Vec3 pos, int yOffset, Component text, boolean player, int color, CameraRenderState camera) {
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
   }

   public void submitFlame(PoseStack poseStack, EntityRenderState state, Quaternionf rotation) {
   }

   public void submitLeash(PoseStack poseStack, LeashState state) {
   }

   public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state, int outlineColor) {
   }

   public void submitBlockModel(
      PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tints, int light, int overlay, int outlineColor
   ) {
   }

   public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress) {
   }

   public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
   }

   public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
   }

   public void submitQuadParticleGroup(QuadParticleRenderState state) {
   }

   public void submitGizmoPrimitives(Group group, CameraRenderState camera, boolean alwaysOnTop) {
   }

   private static <S> void submitMaskModel(
      OrderedSubmitNodeCollector target, Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int tintColor, TextureAtlasSprite sprite
   ) {
      target.submitModel(model, state, poseStack, renderType, 15728880, OverlayTexture.NO_OVERLAY, tintColor, sprite, 0, null);
   }

   private static void submitMaskItem(
      OrderedSubmitNodeCollector target, PoseStack poseStack, ItemDisplayContext context, int[] tintLayers, List<BakedQuad> quads
   ) {
      target.submitItem(poseStack, context, 15728880, OverlayTexture.NO_OVERLAY, 0, tintLayers, quads, FoilType.NONE);
   }

   private record Ordered(OrderedSubmitNodeCollector target) implements OrderedSubmitNodeCollector {
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
         HandSilhouetteCollector.submitMaskModel(this.target, model, state, poseStack, renderType, tintColor, sprite);
      }

      public void submitItem(
         PoseStack poseStack, ItemDisplayContext context, int light, int overlay, int outlineColor, int[] tintLayers, List<BakedQuad> quads, FoilType foil
      ) {
         HandSilhouetteCollector.submitMaskItem(this.target, poseStack, context, tintLayers, quads);
      }

      public void submitShadow(PoseStack poseStack, float radius, List<ShadowPiece> pieces) {
      }

      public void submitNameTag(PoseStack poseStack, Vec3 pos, int yOffset, Component text, boolean player, int color, CameraRenderState camera) {
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
      }

      public void submitFlame(PoseStack poseStack, EntityRenderState state, Quaternionf rotation) {
      }

      public void submitLeash(PoseStack poseStack, LeashState state) {
      }

      public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state, int outlineColor) {
      }

      public void submitBlockModel(
         PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tints, int light, int overlay, int outlineColor
      ) {
      }

      public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress) {
      }

      public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
      }

      public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
      }

      public void submitQuadParticleGroup(QuadParticleRenderState state) {
      }

      public void submitGizmoPrimitives(Group group, CameraRenderState camera, boolean alwaysOnTop) {
      }
   }
}
