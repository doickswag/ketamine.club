package we.ketamine.club.features.modules.player;

import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;

public class NoRotateModule extends Module {
   public NoRotateModule() {
      super("NoRotate", "Ignores server-forced rotations.", Module.Category.PLAYER);
   }

   public static boolean isActive() {
      if (KetamineClub.moduleManager == null) {
         return false;
      } else {
         NoRotateModule module = KetamineClub.moduleManager.getModuleByClass(NoRotateModule.class);
         return module != null && module.isEnabled();
      }
   }
}
