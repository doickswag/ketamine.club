package we.ketamine.club.mixin.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.pingbypass.PingBypass;

@Mixin({PacketEncoder.class})
public class MixinPingBypassEncoder {
   @Shadow
   @Final
   private ProtocolInfo<?> protocolInfo;

   public MixinPingBypassEncoder() {
   }

   @Inject(
      method = {"encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V"},
      at = {@At("RETURN")}
   )
   private void relay(ChannelHandlerContext context, Packet<?> packet, ByteBuf output, CallbackInfo ci) {
      PingBypass.get().encoded(context, this.protocolInfo, packet, output);
   }
}
