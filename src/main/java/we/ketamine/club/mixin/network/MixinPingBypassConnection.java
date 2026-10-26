package we.ketamine.club.mixin.network;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.pingbypass.PingBypass;

@Mixin({Connection.class})
public class MixinPingBypassConnection {
   public MixinPingBypassConnection() {
   }

   @Inject(
      method = {"channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void processRemote(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
      if (PingBypass.get().handleWorkerPacket((Connection)(Object)this, packet)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"setupInboundProtocol"},
      at = {@At("HEAD")}
   )
   private void inbound(ProtocolInfo<?> protocol, PacketListener listener, CallbackInfo ci) {
      PingBypass.get().protocol((Connection)(Object)this, protocol, true);
   }

   @Inject(
      method = {"setupOutboundProtocol"},
      at = {@At("HEAD")}
   )
   private void outbound(ProtocolInfo<?> protocol, CallbackInfo ci) {
      PingBypass.get().protocol((Connection)(Object)this, protocol, false);
   }

   @Inject(
      method = {"sendPacket"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ownership(Packet<?> packet, ChannelFutureListener callback, boolean flush, CallbackInfo ci) {
      if (PingBypass.get().suppressWorkerPacket((Connection)(Object)this, packet)) {
         ci.cancel();
      }
   }
}
