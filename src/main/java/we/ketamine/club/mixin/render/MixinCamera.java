package we.ketamine.club.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.player.FreecamModule;

@Mixin({Camera.class})
public abstract class MixinCamera {
   public MixinCamera() {
   }

   @Shadow
   protected abstract void setPosition(Vec3 var1);

   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Inject(
      method = {"update"},
      at = {@At("TAIL")}
   )
   private void ketamineclub$setupFreecam(DeltaTracker deltaTracker, CallbackInfo ci) {
      FreecamModule freecam = getFreecam();
      if (freecam != null) {
         float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(false);
         this.setPosition(freecam.getCameraPosition(tickDelta));
         this.setRotation(freecam.getCameraYaw(tickDelta), freecam.getCameraPitch(tickDelta));
      }
   }

   @Inject(
      method = {"isDetached"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$freecamIsDetached(CallbackInfoReturnable<Boolean> cir) {
      if (getFreecam() != null) {
         cir.setReturnValue(true);
      }
   }

   private static FreecamModule getFreecam() {
      if (KetamineClub.moduleManager == null) {
         return null;
      } else {
         FreecamModule freecam = KetamineClub.moduleManager.getModuleByClass(FreecamModule.class);
         return freecam != null && freecam.isEnabled() ? freecam : null;
      }
   }
}
