package we.ketamine.club.mixin.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.manager.RotationManager;

@Mixin({LivingEntityRenderer.class})
public class MixinLivingEntityRenderer {
   public MixinLivingEntityRenderer() {
   }

   @Inject(
      method = {"extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V"},
      at = {@At("TAIL")}
   )
   private void ketamineclub$renderMotionRotation(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
      if (entity == Minecraft.getInstance().player) {
         RotationManager rm = KetamineClub.rotationManager;
         if (rm != null && rm.shouldRenderRotation()) {
            state.bodyRot = rm.getRenderYaw(partialTicks);
            state.yRot = 0.0F;
            state.xRot = rm.getRenderPitch(partialTicks);
         }
      }
   }
}
