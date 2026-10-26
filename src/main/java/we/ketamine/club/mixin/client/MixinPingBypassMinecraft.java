package we.ketamine.club.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.pingbypass.PingBypass;

@Mixin({Minecraft.class})
public class MixinPingBypassMinecraft {
   public MixinPingBypassMinecraft() {
   }

   @Inject(
      method = {"handleKeybinds"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void remoteInput(CallbackInfo ci) {
      if (PingBypass.get().isWorkerActive()) {
         ci.cancel();
      }
   }
}
