package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.player.FreecamModule;
import we.ketamine.club.features.modules.render.NoRenderModule;
import we.ketamine.club.features.modules.render.ShadersModule;
import we.ketamine.club.util.render.HandShaderChain;
import we.ketamine.club.util.render.HandShaderRender;

@Mixin({GameRenderer.class, Minecraft.class})
public class MixinGameRenderer {
   public MixinGameRenderer() {
   }

   @Inject(
      method = {"pick(F)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void ketamineclub$freecamPick(float tickDelta, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      FreecamModule freecam = KetamineClub.moduleManager.getModuleByClass(FreecamModule.class);
      if (freecam != null && freecam.isEnabled() && mc.level != null && mc.player != null) {
         Vec3 start = freecam.getCameraPosition(tickDelta);
         Vec3 direction = Vec3.directionFromRotation(freecam.getCameraPitch(tickDelta), freecam.getCameraYaw(tickDelta));
         Vec3 end = start.add(direction.scale(256.0));
         BlockHitResult result = mc.level.clip(new ClipContext(start, end, Block.OUTLINE, Fluid.NONE, mc.player));
         mc.hitResult = result;
         mc.crosshairPickEntity = null;
         ci.cancel();
      }
   }

   @Inject(
      method = {"displayItemActivation(Lnet/minecraft/world/item/ItemStack;)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void ketamineclub$noTotem(ItemStack floatingItem, CallbackInfo ci) {
      if (floatingItem.is(Items.TOTEM_OF_UNDYING) && NoRenderModule.isActive(m -> m.noTotem.getValue())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void ketamineclub$noBob(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noBob.getValue())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void ketamineclub$noTilt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noTilt.getValue())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V",
         shift = Shift.AFTER
      )},
      require = 0
   )
   private void handShader$composite(CameraRenderState cameraState, float partialTick, Matrix4fc cameraTransform, CallbackInfo ci) {
      ShadersModule mod = KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(ShadersModule.class);
      if (mod != null && mod.wantsHandShader()) {
         HandShaderRender.composite(
            Minecraft.getInstance().gameRenderer.featureRenderDispatcher(),
            HandShaderChain.get(
               mod.getHandRgb(),
               mod.getHandThickness(),
               mod.getHandOutlineAlpha(),
               mod.getHandFillAlpha(),
               mod.getHandGlowRadius(),
               mod.getHandGlowIntensity(),
               mod.getHandInnerGlowRadius(),
               mod.getHandInnerGlowIntensity()
            )
         );
      } else {
         HandShaderRender.cleanup();
         HandShaderChain.cleanup();
      }
   }
}
