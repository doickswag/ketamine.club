package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.util.render.SeeThroughRender;

@Mixin({SubmitNodeStorage.class})
public abstract class MixinSubmitNodeCollection {
   public MixinSubmitNodeCollection() {
   }

   @Shadow
   public abstract SubmitNodeCollection order(int var1);

   @Inject(
      method = {"submitModel"},
      at = {@At("RETURN")}
   )
   private <S> void ketamineclub$cloneModelForSeeThrough(
      Model<? super S> model,
      S modelState,
      PoseStack poseStack,
      RenderType renderType,
      int packedLight,
      int overlay,
      int blockPosition,
      TextureAtlasSprite sprite,
      int flags,
      CrumblingOverlay crumbling,
      CallbackInfo ci
   ) {
      if (SeeThroughRender.active && !SeeThroughRender.cloning) {
         if (!SeeThroughRender.isGlintType(renderType)) {
            int sub = SeeThroughRender.cloneSubOrder++;
            SeeThroughRender.cloning = true;

            try {
               RenderType seed = SeeThroughRender.wrapDepthSeed(renderType);
               this.order(100000 + sub).submitModel(model, modelState, poseStack, seed, packedLight, overlay, blockPosition, sprite, flags, crumbling);
               RenderType color = SeeThroughRender.wrapColor(renderType);
               this.order(200000 + sub).submitModel(model, modelState, poseStack, color, packedLight, overlay, blockPosition, sprite, flags, crumbling);
               if (SeeThroughRender.shine) {
                  for (int pass = 0; pass < SeeThroughRender.shinePassCount(); pass++) {
                     this.order(300000 + sub * 8 + pass)
                        .submitModel(
                           model,
                           modelState,
                           poseStack,
                           SeeThroughRender.shineType(),
                           packedLight,
                           overlay,
                           SeeThroughRender.shineTint(pass),
                           sprite,
                           flags,
                           crumbling
                        );
                  }
               }
            } finally {
               SeeThroughRender.cloning = false;
            }
         }
      }
   }

   @Inject(
      method = {"submitItem"},
      at = {@At("RETURN")}
   )
   private void ketamineclub$cloneItemForSeeThrough(
      PoseStack poseStack,
      ItemDisplayContext context,
      int packedLight,
      int overlay,
      int data,
      int[] tints,
      List<BakedQuad> quads,
      FoilType foil,
      CallbackInfo ci
   ) {
      if (SeeThroughRender.active && !SeeThroughRender.cloning) {
         int sub = SeeThroughRender.cloneSubOrder++;
         SeeThroughRender.cloning = true;

         try {
            this.order(100000 + sub)
               .submitItem(
                  poseStack, context, packedLight, overlay, data, tints, ketamineclub$wrapItemQuads(quads, SeeThroughRender::wrapDepthSeed), FoilType.NONE
               );
            this.order(200000 + sub)
               .submitItem(poseStack, context, packedLight, overlay, data, tints, ketamineclub$wrapItemQuads(quads, SeeThroughRender::wrapColor), FoilType.NONE);
         } finally {
            SeeThroughRender.cloning = false;
         }
      }
   }

   private static List<BakedQuad> ketamineclub$wrapItemQuads(List<BakedQuad> quads, UnaryOperator<RenderType> wrapper) {
      return quads.stream()
         .map(
            quad -> {
               MaterialInfo material = quad.materialInfo();
               MaterialInfo wrappedMaterial = new MaterialInfo(
                  material.sprite(),
                  material.layer(),
                  wrapper.apply(material.itemRenderType()),
                  material.tintIndex(),
                  material.shade(),
                  material.lightEmission()
               );
               return new BakedQuad(
                  quad.position0(),
                  quad.position1(),
                  quad.position2(),
                  quad.position3(),
                  quad.packedUV0(),
                  quad.packedUV1(),
                  quad.packedUV2(),
                  quad.packedUV3(),
                  quad.direction(),
                  wrappedMaterial
               );
            }
         )
         .toList();
   }
}
