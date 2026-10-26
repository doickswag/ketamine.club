package we.ketamine.club.mixin.network;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.pingbypass.PingBypass;

@Mixin({ClientCommonPacketListenerImpl.class})
public class MixinPingBypassCommonListener {
   public MixinPingBypassCommonListener() {
   }

   @Inject(
      method = {"handleResourcePackPush"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void resourcePack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
      if (PingBypass.isWorker()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleTransfer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void transfer(ClientboundTransferPacket packet, CallbackInfo ci) {
      if (PingBypass.isWorker()) {
         ci.cancel();
      }
   }
}
