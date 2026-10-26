package we.ketamine.club.mixin.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.pingbypass.PingBypassMotionSmoothing;

@Mixin({ClientPacketListener.class})
public class MixinPingBypassMotionReset {
   public MixinPingBypassMotionReset() {
   }

   @Inject(
      method = {"handleMovePlayer", "handleMoveVehicle", "handleRespawn", "handleLogin", "handleConfigurationStart"},
      at = {@At("TAIL")}
   )
   private void pingbypass$resetMotion(CallbackInfo ci) {
      PingBypassMotionSmoothing.reset();
   }
}
