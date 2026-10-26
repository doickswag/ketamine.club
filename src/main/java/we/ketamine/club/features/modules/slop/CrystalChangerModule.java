package we.ketamine.club.features.modules.slop;

import com.mojang.blaze3d.vertex.PoseStack;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class CrystalChangerModule extends Module {
   public final Setting<Double> scale = this.num("Scale", 1.0, 0.0, 5.0);
   public final Setting<Double> spin = this.num("Spin", 1.0, 0.0, 10.0);
   public final Setting<Double> bounce = this.num("Bounce", 1.0, 0.0, 5.0);

   public CrystalChangerModule() {
      super("CrystalChanger", "Changes crystal scale, spin, and bounce multipliers.", Module.Category.SLOP);
   }

   public static CrystalChangerModule active() {
      if (KetamineClub.moduleManager == null) {
         return null;
      } else {
         CrystalChangerModule module = KetamineClub.moduleManager.getModuleByClass(CrystalChangerModule.class);
         return module != null && module.isEnabled() ? module : null;
      }
   }

   public void applyScale(PoseStack poseStack) {
      float multiplier = Math.max(0.0F, this.scale.getValue().floatValue());
      poseStack.scale(multiplier, multiplier, multiplier);
   }
}
