package we.ketamine.club.mixin.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.movement.BhopModule;
import we.ketamine.club.features.modules.movement.FakeFlyModule;
import we.ketamine.club.features.modules.render.NametagsModule;

@Mixin({AvatarRenderer.class})
public class MixinAvatarRenderer {
   private static final float FULL_FALL_FLYING_POSE_TICKS = 10.0F;

   public MixinAvatarRenderer() {
   }

   @Inject(
      method = {"submitNameDisplay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cancelVanillaNameTag(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
      NametagsModule nametags = KetamineClub.moduleManager.getModuleByClass(NametagsModule.class);
      if (nametags != null && nametags.isEnabled()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V"},
      at = {@At("TAIL")}
   )
   private void ketamineclub$stabilizeFakeFlyPose(Avatar avatar, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
      if (avatar == Minecraft.getInstance().player && avatar instanceof Player player) {
         BhopModule bhop = KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(BhopModule.class);
         if (bhop != null && bhop.isApplying()) {
            state.isFallFlying = false;
            state.fallFlyingTimeInTicks = 0.0F;
            state.pose = Pose.STANDING;
            state.swimAmount = 0.0F;
            state.isVisuallySwimming = false;
            state.isCrouching = false;
            state.speedValue = 1.0F;
         } else {
            FakeFlyModule fakeFly = KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(FakeFlyModule.class);
            boolean fakeFlyPose = !avatar.onGround() && fakeFly != null && fakeFly.isEnabled();
            if (fakeFlyPose) {
               state.isFallFlying = true;
               state.fallFlyingTimeInTicks = Math.max(state.fallFlyingTimeInTicks, 10.0F);
               state.pose = Pose.FALL_FLYING;
            }
         }
      }
   }
}
