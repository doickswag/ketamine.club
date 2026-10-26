package we.ketamine.club.pingbypass;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record PingBypassMotionPayload(String state) implements CustomPacketPayload {
   public static final Type<PingBypassMotionPayload> TYPE = new Type(Identifier.fromNamespaceAndPath("ketamineclub", "pingbypass_motion"));
   public static final StreamCodec<FriendlyByteBuf, PingBypassMotionPayload> CODEC = StreamCodec.of(
      (buffer, payload) -> buffer.writeUtf(payload.state, 8192), buffer -> new PingBypassMotionPayload(buffer.readUtf(8192))
   );

   public Type<PingBypassMotionPayload> type() {
      return TYPE;
   }
}
