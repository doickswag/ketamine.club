package we.ketamine.club.features.modules.funny;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.util.MathUtil;

public class EndermanLookModule extends Module {
   private static final String ROTATION_ID = "EndermanLook";
   private static final int ROTATION_PRIORITY = 1;
   private final Setting<EndermanLookModule.Mode> lookMode = this.mode("LookMode", EndermanLookModule.Mode.AWAY);
   private final Setting<Boolean> stunHostiles = this.bool("StunHostiles", true).setVisibility(v -> this.lookMode.getValue() == EndermanLookModule.Mode.AWAY);

   public EndermanLookModule() {
      super("EndermanLook", "Either stares at every Enderman or stops you from staring at them.", Module.Category.FUNNY);
   }

   @Override
   public void onDisable() {
      if (KetamineClub.rotationManager != null) {
         KetamineClub.rotationManager.cancel("EndermanLook");
      }
   }

   @Override
   public void onTick() {
      if (nullCheck()) {
         this.cancel();
      } else {
         LocalPlayer player = mc.player;
         if (!player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN) && !player.getAbilities().instabuild) {
            EnderMan stareTarget = null;
            EnderMan awayThreat = null;
            double bestStare = Double.MAX_VALUE;
            double bestAway = Double.MAX_VALUE;

            for (Entity entity : mc.level.entitiesForRendering()) {
               if (entity instanceof EnderMan enderman && enderman.isAlive() && player.hasLineOfSight(enderman)) {
                  double dist = player.distanceToSqr(enderman);
                  switch ((EndermanLookModule.Mode)this.lookMode.getValue()) {
                     case AT:
                        if (!enderman.isCreepy() && dist < bestStare) {
                           bestStare = dist;
                           stareTarget = enderman;
                        }
                        break;
                     case AWAY:
                        if (enderman.isCreepy() && this.stunHostiles.getValue()) {
                           if (dist < bestStare) {
                              bestStare = dist;
                              stareTarget = enderman;
                           }
                        } else if (this.angleCheck(enderman) && dist < bestAway) {
                           bestAway = dist;
                           awayThreat = enderman;
                        }
                  }
               }
            }

            if (stareTarget != null) {
               float[] angles = MathUtil.calcAngle(player.getEyePosition(), stareTarget.getEyePosition());
               this.submit(angles[0], angles[1]);
            } else if (awayThreat != null) {
               this.submit(player.getYRot(), 90.0F);
            } else {
               this.cancel();
            }
         } else {
            this.cancel();
         }
      }
   }

   private void submit(float yaw, float pitch) {
      KetamineClub.rotationManager.submit(new RotationRequest("EndermanLook", 1, yaw, pitch, RotationRequest.Mode.MOTION, true));
   }

   private void cancel() {
      if (KetamineClub.rotationManager != null) {
         KetamineClub.rotationManager.cancel("EndermanLook");
      }
   }

   private boolean angleCheck(EnderMan enderman) {
      Vec3 view = mc.player.getViewVector(1.0F).normalize();
      Vec3 diff = new Vec3(enderman.getX() - mc.player.getX(), enderman.getEyeY() - mc.player.getEyeY(), enderman.getZ() - mc.player.getZ());
      double d = diff.length();
      double dot = view.dot(diff.normalize());
      return dot > 1.0 - 0.025 / d;
   }

   public static enum Mode {
      AT,
      AWAY;

      private Mode() {
      }
   }
}
