package we.ketamine.club.mixin.network;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.util.traits.Util;

@Mixin(
   targets = {"net.minecraft.network.PacketProcessor$ListenerAndPacket"}
)
public abstract class MixinClientPacketProcessor {
   @Shadow
   @Final
   private PacketListener listener;
   @Shadow
   @Final
   private Packet<?> packet;

   public MixinClientPacketProcessor() {
   }

   @Inject(
      method = {"handle"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"
      )},
      cancellable = true
   )
   private void ketamineclub$beforeApply(CallbackInfo ci) {
      if (this.listener.flow() == PacketFlow.CLIENTBOUND && Util.EVENT_BUS.post(new PacketEvent.Receive(this.packet))) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handle"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V",
         shift = Shift.AFTER
      )}
   )
   private void ketamineclub$afterApply(CallbackInfo ci) {
      if (this.listener.flow() == PacketFlow.CLIENTBOUND) {
         Util.EVENT_BUS.post(new PacketEvent.ReceivePost(this.packet));
      }
   }
}
