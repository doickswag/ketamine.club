package we.ketamine.club.event.impl.network;

import net.minecraft.network.protocol.Packet;
import we.ketamine.club.event.Event;

public abstract class PacketEvent extends Event {
   private final Packet<?> packet;

   public PacketEvent(Packet<?> packet) {
      this.packet = packet;
   }

   public Packet<?> getPacket() {
      return this.packet;
   }

   public static class Receive extends PacketEvent {
      public Receive(Packet<?> packet) {
         super(packet);
      }
   }

   public static class ReceivePost extends PacketEvent {
      public ReceivePost(Packet<?> packet) {
         super(packet);
      }
   }

   public static class Send extends PacketEvent {
      public Send(Packet<?> packet) {
         super(packet);
      }
   }
}
