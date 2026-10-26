package we.ketamine.club.mixin.input;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.input.MouseInputEvent;
import we.ketamine.club.features.modules.player.FreecamModule;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.traits.Util;

@Mixin({MouseHandler.class})
public class MixinMouse {
   public MixinMouse() {
   }

   @Redirect(
      method = {"turnPlayer"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
      )
   )
   private void ketamineclub$turnFreecam(LocalPlayer player, double yaw, double pitch) {
      FreecamModule freecam = KetamineClub.moduleManager.getModuleByClass(FreecamModule.class);
      if (freecam != null && freecam.isEnabled()) {
         freecam.turnCamera(yaw, pitch);
      } else {
         player.turn(yaw, pitch);
      }
   }

   @Inject(
      method = {"onButton"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
      PingBypassInput.mouse(input.button(), action);
      if (Util.EVENT_BUS.post(new MouseInputEvent(input.button(), action))) {
         ci.cancel();
      }
   }
}
