package we.ketamine.club.mixin.network;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.util.traits.Util;

@Mixin({ClientCommonPacketListenerImpl.class})
public class MixinClientCommonPacketListenerImpl {
   public MixinClientCommonPacketListenerImpl() {
   }

   @Inject(
      method = {"onDisconnect"},
      at = {@At("HEAD")}
   )
   private void onDisconnectHook(DisconnectionDetails details, CallbackInfo ci) {
      Util.EVENT_BUS.post(new DisconnectEvent());
   }
}
