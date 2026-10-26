package we.ketamine.club.mixin.entity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.manager.RotationManager;
import we.ketamine.club.pingbypass.PingBypass;
import we.ketamine.club.util.traits.Util;

@Mixin(
   value = {LocalPlayer.class},
   priority = Integer.MAX_VALUE
)
public class MixinLocalPlayerRotation {
   @Shadow
   private float xRotLast;
   @Unique
   private float ketamineclub$savedYaw;
   @Unique
   private float ketamineclub$savedPitch;
   @Unique
   private boolean ketamineclub$spoofed;

   public MixinLocalPlayerRotation() {
   }

   @Inject(
      method = {"sendPosition"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$spoofRotationHead(CallbackInfo ci) {
      this.ketamineclub$spoofed = false;
      RotationManager rm = KetamineClub.rotationManager;
      if (PingBypass.get().beforeVanillaMovement()) {
         ci.cancel();
      } else if (rm != null) {
         boolean motion = rm.isRotating();
         boolean silent = rm.isSilentSyncRequired();
         if (motion || silent) {
            this.ketamineclub$savedYaw = Util.mc.player.getYRot();
            this.ketamineclub$savedPitch = Util.mc.player.getXRot();
            float outYaw = this.ketamineclub$savedYaw;
            float outPitch = this.ketamineclub$savedPitch;
            if (motion) {
               outYaw = rm.getRotationYaw();
               outPitch = rm.getRotationPitch();
               rm.setServerDeltaYaw(outYaw - rm.getServerYaw());
               rm.setServerYaw(outYaw);
               rm.setServerPitch(outPitch);
            } else {
               this.xRotLast -= 4.0F;
               float f = (float)((Math.random() * 2.0 - 1.0) * 0.001F);
               outPitch = Mth.clamp(outPitch + f, -90.0F, 90.0F);
               rm.setServerDeltaYaw(outYaw - rm.getServerYaw());
               rm.setServerYaw(outYaw);
               rm.setServerPitch(outPitch);
            }

            Util.mc.player.setYRot(outYaw);
            Util.mc.player.setXRot(outPitch);
            this.ketamineclub$spoofed = true;
         }
      }
   }

   @Inject(
      method = {"sendPosition"},
      at = {@At("TAIL")}
   )
   private void ketamineclub$spoofRotationTail(CallbackInfo ci) {
      RotationManager rm = KetamineClub.rotationManager;
      if (rm != null) {
         if (rm.isRotating()) {
            rm.setServerYaw(Util.mc.player.getYRot());
            rm.setServerPitch(Util.mc.player.getXRot());
         }

         if (this.ketamineclub$spoofed) {
            Util.mc.player.setYRot(this.ketamineclub$savedYaw);
            Util.mc.player.setXRot(this.ketamineclub$savedPitch);
            this.ketamineclub$spoofed = false;
         }

         rm.setSilentSyncRequired(false);
      }
   }
}
