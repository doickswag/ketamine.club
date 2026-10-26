package we.ketamine.club.mixin.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.pingbypass.PingBypass;

@Mixin({PacketDecoder.class})
public class MixinPingBypassDecoder {
   @Shadow
   @Final
   private ProtocolInfo<?> protocolInfo;
   @Unique
   private byte[] ketamineclub$frame;

   public MixinPingBypassDecoder() {
   }

   @Inject(
      method = {"decode"},
      at = {@At("HEAD")}
   )
   private void capture(ChannelHandlerContext context, ByteBuf input, List<Object> output, CallbackInfo ci) {
      this.ketamineclub$frame = null;
      if (PingBypass.get().mirrors(context)) {
         this.ketamineclub$frame = new byte[input.readableBytes()];
         input.getBytes(input.readerIndex(), this.ketamineclub$frame);
      }
   }

   @Inject(
      method = {"decode"},
      at = {@At("RETURN")}
   )
   private void mirror(ChannelHandlerContext context, ByteBuf input, List<Object> output, CallbackInfo ci) {
      byte[] frame = this.ketamineclub$frame;
      this.ketamineclub$frame = null;
      if (frame != null && !output.isEmpty() && output.getLast() instanceof Packet<?> packet) {
         PingBypass.get().received(context, this.protocolInfo, packet, frame);
      }
   }
}
