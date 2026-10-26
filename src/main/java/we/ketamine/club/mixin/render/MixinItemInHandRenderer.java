package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.render.CrystalHandModule;
import we.ketamine.club.features.modules.render.ShadersModule;
import we.ketamine.club.features.modules.render.ViewModelModule;
import we.ketamine.club.mixin.entity.EntityRotationAccessor;
import we.ketamine.club.util.render.HandShaderRender;
import we.ketamine.club.util.render.HandSilhouetteCollector;
import we.ketamine.club.util.render.TeeSubmitCollector;

@Mixin({ItemInHandRenderer.class})
public class MixinItemInHandRenderer {
   @Unique
   private boolean handShader$capturing;
   @Unique
   private boolean viewModel$scaled;
   @Shadow
   private float mainHandHeight;
   @Shadow
   private float oMainHandHeight;
   @Shadow
   private float offHandHeight;
   @Shadow
   private float oOffHandHeight;
   private float noSway$savedXBob;
   private float noSway$savedXBobO;
   private float noSway$savedYBob;
   private float noSway$savedYBobO;

   public MixinItemInHandRenderer() {
   }

   @Inject(
      method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"},
      at = {@At("HEAD")}
   )
   private void noSway$pre(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer renderedPlayer, int light, CallbackInfo ci) {
      if (ViewModelModule.isActive(m -> m.noSway.getValue())) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            EntityRotationAccessor acc = (EntityRotationAccessor)player;
            this.noSway$savedXBob = acc.ketamineclub$getXBob();
            this.noSway$savedXBobO = acc.ketamineclub$getXBobO();
            this.noSway$savedYBob = acc.ketamineclub$getYBob();
            this.noSway$savedYBobO = acc.ketamineclub$getYBobO();
            float xRot = player.getXRot();
            float yRot = player.getYRot();
            acc.ketamineclub$setXBob(xRot);
            acc.ketamineclub$setXBobO(xRot);
            acc.ketamineclub$setYBob(yRot);
            acc.ketamineclub$setYBobO(yRot);
         }
      }
   }

   @Inject(
      method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"},
      at = {@At("RETURN")}
   )
   private void noSway$post(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer renderedPlayer, int light, CallbackInfo ci) {
      if (ViewModelModule.isActive(m -> m.noSway.getValue())) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            EntityRotationAccessor acc = (EntityRotationAccessor)player;
            acc.ketamineclub$setXBob(this.noSway$savedXBob);
            acc.ketamineclub$setXBobO(this.noSway$savedXBobO);
            acc.ketamineclub$setYBob(this.noSway$savedYBob);
            acc.ketamineclub$setYBobO(this.noSway$savedYBobO);
         }
      }
   }

   @Inject(
      method = {"shouldInstantlyReplaceVisibleItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void noSwap$instant(ItemStack visibleStack, ItemStack currentStack, CallbackInfoReturnable<Boolean> cir) {
      if (ViewModelModule.isActive(m -> m.noSwapAnimation.getValue())) {
         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"tick()V"},
      at = {@At("TAIL")}
   )
   private void oldAnimation$tick(CallbackInfo ci) {
      if (ViewModelModule.isActive(m -> m.oldAnimation.getValue())) {
         this.mainHandHeight = 1.0F;
         this.oMainHandHeight = 1.0F;
         this.offHandHeight = 1.0F;
         this.oOffHandHeight = 1.0F;
      }
   }

   @Inject(
      method = {"submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"},
      at = {@At("HEAD")}
   )
   private void viewModel$push(
      AbstractClientPlayer player,
      float partialTick,
      float interpolatedPitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack stack,
      float equipProgress,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      int light,
      CallbackInfo ci
   ) {
      this.viewModel$scaled = false;
      ViewModelModule mod = ViewModelModule.getInstance();
      if (mod != null && mod.isEnabled()) {
         this.viewModel$scaled = true;
         HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
         float sign = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
         poseStack.pushPose();
         poseStack.translate(mod.posX.getValue() * sign, mod.posY.getValue(), mod.posZ.getValue());
         float s = mod.scale.getValue().floatValue();
         poseStack.translate(0.56 * sign, -0.52, -0.72);
         poseStack.scale(s, s, s);
         poseStack.translate(-0.56 * sign, 0.52, 0.72);
      }
   }

   @Inject(
      method = {"submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"},
      at = {@At("RETURN")}
   )
   private void viewModel$pop(
      AbstractClientPlayer player,
      float partialTick,
      float interpolatedPitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack stack,
      float equipProgress,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      int light,
      CallbackInfo ci
   ) {
      if (this.viewModel$scaled) {
         this.viewModel$scaled = false;
         poseStack.popPose();
      }
   }

   @ModifyArg(
      method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
      ),
      index = 5
   )
   private ItemStack crystalHand$modifyRenderedItem(ItemStack original) {
      CrystalHandModule mod = KetamineClub.moduleManager.getModuleByClass(CrystalHandModule.class);
      return mod != null && mod.isEnabled() ? mod.getDisplayStack(original) : original;
   }

   @ModifyVariable(
      method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private SubmitNodeCollector handShader$wrapCollector(SubmitNodeCollector original) {
      this.handShader$capturing = false;
      ShadersModule mod = KetamineClub.moduleManager.getModuleByClass(ShadersModule.class);
      if (mod != null && mod.wantsHandShader()) {
         HandSilhouetteCollector secondary = HandShaderRender.beginCapture();
         if (secondary == null) {
            return original;
         } else {
            this.handShader$capturing = true;
            return new TeeSubmitCollector(original, secondary);
         }
      } else {
         return original;
      }
   }

   @Inject(
      method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"},
      at = {@At("TAIL")}
   )
   private void handShader$finishCapture(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
      if (this.handShader$capturing) {
         this.handShader$capturing = false;
         HandShaderRender.endCapture();
      }
   }
}
