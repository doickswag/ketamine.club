package we.ketamine.club.mixin.network;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.util.traits.Util;

@Mixin({Connection.class})
public class MixinClientConnection {
   @Shadow
   @Final
   private PacketFlow receiving;

   public MixinClientConnection() {
   }

   @Inject(
      method = {"sendPacket"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void sendImmediately(Packet<?> packet, ChannelFutureListener callbacks, boolean flush, CallbackInfo ci) {
      if (this.receiving == PacketFlow.CLIENTBOUND && Minecraft.getInstance().isSameThread()) {
         if (Util.EVENT_BUS.post(new PacketEvent.Send(packet))) {
            ci.cancel();
         }
      }
   }
}
