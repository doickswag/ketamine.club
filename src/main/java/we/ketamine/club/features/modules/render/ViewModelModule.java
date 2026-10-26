package we.ketamine.club.features.modules.render;

import java.util.function.Predicate;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class ViewModelModule extends Module {
   public final Setting<Double> scale = this.num("Scale", 1.0, 0.0, 3.0);
   public final Setting<Double> posX = this.num("PosX", 0.0, -1.0, 1.0);
   public final Setting<Double> posY = this.num("PosY", 0.0, -1.0, 1.0);
   public final Setting<Double> posZ = this.num("PosZ", 0.0, -1.0, 1.0);
   public final Setting<Boolean> noSway = this.bool("NoSway", false);
   public final Setting<Boolean> noSwapAnimation = this.bool("NoSwapAnimation", false);
   public final Setting<Boolean> oldAnimation = this.bool("OldAnimation", false);
   public final Setting<Double> swingSpeed = this.num("SwingSpeed", 1.0, 0.1, 5.0).setPage("Swing");
   public final Setting<Integer> swingDelay = this.num("SwingDelay", 3, 0, 20).setPage("Swing");

   public ViewModelModule() {
      super("ViewModel", "change scale position and stuff", Module.Category.RENDER);
   }

   public static ViewModelModule getInstance() {
      return KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(ViewModelModule.class);
   }

   public static boolean isActive(Predicate<ViewModelModule> predicate) {
      ViewModelModule module = getInstance();
      return module != null && module.isEnabled() && predicate.test(module);
   }
}
