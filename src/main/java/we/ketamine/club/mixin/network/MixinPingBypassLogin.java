package we.ketamine.club.mixin.network;

import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.pingbypass.PingBypass;

@Mixin({ClientHandshakePacketListenerImpl.class})
public class MixinPingBypassLogin {
   @Shadow
   @Final
   private Connection connection;

   public MixinPingBypassLogin() {
   }

   @Inject(
      method = {"authenticateServer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void localAuthorization(String digest, CallbackInfoReturnable<Component> cir) {
      if (PingBypass.isWorker()) {
         cir.setReturnValue(PingBypass.get().authenticateWorker(this.connection, digest));
      }
   }
}
