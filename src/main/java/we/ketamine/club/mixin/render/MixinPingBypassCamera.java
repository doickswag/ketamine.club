package we.ketamine.club.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.pingbypass.PingBypassMotionSmoothing;

@Mixin({Camera.class})
public abstract class MixinPingBypassCamera {
   @Shadow
   private Entity entity;
   @Shadow
   private float eyeHeight;
   @Shadow
   private float eyeHeightOld;

   public MixinPingBypassCamera() {
   }

   @Shadow
   protected abstract void setPosition(Vec3 var1);

   @Inject(
      method = {"update"},
      at = {@At("HEAD")}
   )
   private void pingbypass$beginFrame(DeltaTracker deltaTracker, CallbackInfo ci) {
      PingBypassMotionSmoothing.beginFrame();
   }

   @Inject(
      method = {"alignWithEntity"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Options;getCameraType()Lnet/minecraft/client/CameraType;",
         ordinal = 0
      )}
   )
   private void pingbypass$smoothPosition(float partialTick, CallbackInfo ci) {
      Vec3 position = PingBypassMotionSmoothing.renderPosition(this.entity);
      if (position != null) {
         this.setPosition(position.add(0.0, Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight), 0.0));
      }
   }
}
