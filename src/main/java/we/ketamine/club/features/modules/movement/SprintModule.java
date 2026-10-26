package we.ketamine.club.features.modules.movement;

import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;

public class SprintModule extends Module {
   private final Setting<SprintModule.Mode> mode = this.mode("Mode", SprintModule.Mode.NORMAL);
   private static final String ROTATION_ID = "Sprint";

   public SprintModule() {
      super("Sprint", "Automatically sprints whenever you move. Omni sprints in any direction.", Module.Category.MOVEMENT);
   }

   @Override
   public void onDisable() {
      KetamineClub.rotationManager.cancel("Sprint");
   }

   public boolean wantsSprint() {
      if (!this.isEnabled() || nullCheck()) {
         return false;
      } else if (mc.player.isUsingItem()) {
         return false;
      } else {
         return this.mode.getValue() == SprintModule.Mode.OMNI && !mc.player.isFallFlying()
            ? mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()
            : mc.player.input.getMoveVector().y > 0.0F;
      }
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         if (this.mode.getValue() == SprintModule.Mode.OMNI && !mc.player.isFallFlying()) {
            this.omni();
         } else {
            KetamineClub.rotationManager.cancel("Sprint");
            if (mc.player.input.getMoveVector().y > 0.0F) {
               mc.player.setSprinting(true);
            }
         }
      }
   }

   private void omni() {
      int inputX = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
      int inputZ = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
      if (inputX == 0 && inputZ == 0) {
         KetamineClub.rotationManager.cancel("Sprint");
      } else {
         float moveAngle = (float)Math.toDegrees(Math.atan2(inputX, inputZ));
         float targetYaw = mc.player.getYRot() + moveAngle;
         KetamineClub.rotationManager
            .submit(new RotationRequest("Sprint", Integer.MIN_VALUE, targetYaw, mc.player.getXRot(), RotationRequest.Mode.MOTION, true, true));
         mc.player.setSprinting(true);
      }
   }

   public static enum Mode {
      NORMAL,
      OMNI;

      private Mode() {
      }
   }
}
