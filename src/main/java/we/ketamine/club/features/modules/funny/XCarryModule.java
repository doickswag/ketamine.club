package we.ketamine.club.features.modules.funny;

import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;

public class XCarryModule extends Module {
   public XCarryModule() {
      super("XCarry", "Keeps items in the player crafting grid when the inventory is closed.", Module.Category.FUNNY);
   }

   public static boolean isActive() {
      if (KetamineClub.moduleManager == null) {
         return false;
      } else {
         XCarryModule module = KetamineClub.moduleManager.getModuleByClass(XCarryModule.class);
         return module != null && module.isEnabled();
      }
   }

   @Subscribe
   private void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundContainerClosePacket packet && packet.getContainerId() == 0) {
         event.cancel();
      }
   }
}
