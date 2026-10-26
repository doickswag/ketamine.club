package we.ketamine.club.features.modules.movement;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.mixin.entity.FireworkRocketEntityAccessor;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class VerticalDesyncModule extends Module {
   public static final String ID = "VerticalDesync";
   private static final int ROTATION_PRIORITY = 110;
   private static final int ROCKET_SWAP_PRIORITY = 15;
   private final Setting<Double> verticalSpeed = this.num("VerticalSpeed", 1.65, 0.1, 2.5);
   private final Setting<Boolean> hover = this.bool("Hover", true);
   private final Setting<Double> hoverVelocity = this.num("HoverVelocity", 0.08, 0.0, 0.2);
   private final Setting<Boolean> autoRocket = this.bool("AutoRocket", true);
   private final Setting<Integer> refireLead = this.num("RefireLead", 3, 0, 10);
   private int alternatingTicks;
   private int ticksSinceRocket = 1073741823;
   private int refireTicks;
   private int rocketRemaining = -1;
   private boolean pitchFlip;
   private boolean controlling;

   public VerticalDesyncModule() {
      super("VerticalDesync", "Uses opposing rocket-look states for independent vertical movement.", Module.Category.MOVEMENT);
      this.hoverVelocity.setVisibility(v -> this.hover.getValue());
      this.refireLead.setVisibility(v -> this.autoRocket.getValue());
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      this.controlling = false;
      if (!nullCheck() && mc.player.isFallFlying()) {
         this.ticksSinceRocket++;
         this.rocketRemaining = this.bestRocketRemaining();
         if (this.autoRocket.getValue() && this.rocketRemaining <= this.refireLead.getValue()) {
            this.fireRocket();
         }

         if (this.rocketRemaining < 0) {
            this.alternatingTicks = 0;
            KetamineClub.rotationManager.cancel("VerticalDesync");
         } else {
            this.pitchFlip = !this.pitchFlip;
            float pitch = this.pitchFlip ? 90.0F : -90.0F;
            KetamineClub.rotationManager
               .submit(new RotationRequest("VerticalDesync", 110, mc.player.getYRot(), pitch, RotationRequest.Mode.MOTION, true, true));
            this.alternatingTicks++;
         }
      } else {
         this.resetState();
      }
   }

   public boolean applyVerticalBoost(FireworkRocketEntity rocket) {
      if (this.isEnabled() && !nullCheck() && mc.player.isFallFlying()) {
         if (((FireworkRocketEntityAccessor)rocket).ketamineclub$getAttachedToEntity() != mc.player) {
            return false;
         } else if (this.alternatingTicks < 2) {
            return false;
         } else {
            boolean up = mc.options.keyJump.isDown();
            boolean down = mc.options.keyShift.isDown();
            double targetY;
            if (up && !down) {
               targetY = this.verticalSpeed.getValue();
            } else if (down && !up) {
               targetY = -this.verticalSpeed.getValue();
            } else {
               if (!this.hover.getValue()) {
                  return false;
               }

               targetY = this.hoverVelocity.getValue();
            }

            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x, targetY, velocity.z);
            this.controlling = true;
            return true;
         }
      } else {
         return false;
      }
   }

   private int bestRocketRemaining() {
      int best = -1;

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity instanceof FireworkRocketEntity rocket) {
            FireworkRocketEntityAccessor accessor = (FireworkRocketEntityAccessor)rocket;
            if (accessor.ketamineclub$getAttachedToEntity() == mc.player) {
               best = Math.max(best, accessor.ketamineclub$getLifetime() - accessor.ketamineclub$getLife());
            }
         }
      }

      return best;
   }

   private void fireRocket() {
      if (this.ticksSinceRocket >= this.refireTicks) {
         Result rocket = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE);
         if (rocket.found()) {
            this.ticksSinceRocket = 0;
            this.refireTicks = InventoryUtil.fireworkRefireTicks(rocket.stack());
            KetamineClub.swapManager.submit(new SwapRequest("VerticalDesync", 15, rocket, () -> mc.gameMode.useItem(mc.player, rocket.hand())));
         }
      }
   }

   private void resetState() {
      if (KetamineClub.rotationManager != null) {
         KetamineClub.rotationManager.cancel("VerticalDesync");
      }

      this.alternatingTicks = 0;
      this.ticksSinceRocket = 1073741823;
      this.refireTicks = 0;
      this.rocketRemaining = -1;
      this.pitchFlip = false;
      this.controlling = false;
   }

   @Override
   public String getDisplayInfo() {
      if (this.controlling) {
         return "Vertical";
      } else {
         return this.rocketRemaining >= 0 ? "Warmup " + Math.min(this.alternatingTicks, 2) + "/2" : null;
      }
   }
}
