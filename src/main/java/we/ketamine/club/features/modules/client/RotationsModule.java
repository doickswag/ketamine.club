package we.ketamine.club.features.modules.client;

import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationManager;
import we.ketamine.club.manager.RotationRequest;

public class RotationsModule extends Module {
   public final Setting<RotationRequest.Mode> rotation = this.mode("Rotation", RotationRequest.Mode.SILENT);
   public final Setting<Float> speed = this.num("Speed", 360.0F, 25.0F, 360.0F);
   public final Setting<Float> ease = this.num("Ease", 1.0F, 0.5F, 1.0F);
   public final Setting<Float> threshold = this.num("Threshold", 5.0F, 3.0F, 15.0F);
   public final Setting<Integer> hold = this.num("Hold", 0, 0, 30);
   public final Setting<RotationManager.JitterMode> jitter = this.mode("Jitter", RotationManager.JitterMode.GRIM);
   public final Setting<Boolean> moveFix = this.bool("MoveFix", true);
   public final Setting<Boolean> render = this.bool("Render", false);

   public RotationsModule() {
      super("Rotations", "Configure client rotation behavior.", Module.Category.CLIENT);
   }

   @Override
   public void onLoad() {
      if (!this.isEnabled()) {
         this.enable();
      }
   }

   @Override
   public void onDisable() {
      this.enable();
   }
}
