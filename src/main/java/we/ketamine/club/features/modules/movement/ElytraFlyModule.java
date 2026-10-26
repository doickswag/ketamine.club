package we.ketamine.club.features.modules.movement;

import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class ElytraFlyModule extends Module {
   public static final String ID = "ElytraFly";
   public static final int PRIORITY = 50;
   private static final int ROCKET_SWAP_PRIORITY = 15;
   private final Setting<Boolean> lockPitch = this.bool("LockPitch", true);
   private final Setting<Double> pitch = this.num("Pitch", 0.0, -45.0, 45.0);
   private final Setting<Double> climbPitch = this.num("ClimbPitch", 45.0, 15.0, 89.0);
   private final Setting<Boolean> hover = this.bool("Hover", false);
   private final Setting<Double> hoverPitch = this.num("HoverPitch", -1.3, -45.0, 0.0);
   private final Setting<Boolean> autoRocket = this.bool("AutoRocket", false);
   private boolean steering;
   private boolean hovering;
   private int idlePhase;
   private int ticksSinceRocket = 1073741823;
   private int refireTicks;

   public ElytraFlyModule() {
      super("ElytraFly", "Steer your elytra flight with WASD; auto-fires fireworks for thrust.", Module.Category.MOVEMENT);
      this.pitch.setVisibility(v -> this.lockPitch.getValue());
      this.hoverPitch.setVisibility(v -> this.hover.getValue());
   }

   public boolean isSteering() {
      return this.steering;
   }

   @Override
   public void onDisable() {
      this.stop();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && mc.player.isFallFlying()) {
         this.hovering = false;
         if (this.autoRocket.getValue()) {
            this.tryAutoRocket();
         }

         FollowerModule follower = KetamineClub.moduleManager.getModuleByClass(FollowerModule.class);
         if (follower != null && follower.isEnabled() && follower.canFollow()) {
            this.yieldSteering();
         } else {
            boolean forward = mc.options.keyUp.isDown();
            boolean back = mc.options.keyDown.isDown();
            boolean left = mc.options.keyLeft.isDown();
            boolean right = mc.options.keyRight.isDown();
            boolean up = mc.options.keyJump.isDown();
            boolean down = mc.options.keyShift.isDown();
            float realYaw = mc.player.getYRot();
            float strafe = (right ? 1.0F : 0.0F) - (left ? 1.0F : 0.0F);
            float fwd = (forward ? 1.0F : 0.0F) - (back ? 1.0F : 0.0F);
            boolean hasHorizontal = strafe != 0.0F || fwd != 0.0F;
            boolean wantUp = up && !down;
            boolean wantDown = down && !up;
            if (hasHorizontal || wantUp || wantDown) {
               this.idlePhase = 0;
            }

            if (hasHorizontal) {
               float yaw = this.headingFromInput(realYaw, strafe, fwd);
               float targetPitch;
               if (wantUp) {
                  targetPitch = -this.climbPitch.getValue().floatValue();
               } else if (wantDown) {
                  targetPitch = this.climbPitch.getValue().floatValue();
               } else {
                  targetPitch = this.lockPitch.getValue() ? this.pitch.getValue().floatValue() : mc.player.getXRot();
               }

               this.steerTo(yaw, targetPitch);
            } else if (wantUp) {
               this.steerTo(realYaw, -90.0F);
            } else if (wantDown) {
               this.steerTo(realYaw, 90.0F);
            } else {
               if (this.hover.getValue()) {
                  boolean lookBack = (this.idlePhase++ & 1) == 1;
                  this.steerTo(lookBack ? Mth.wrapDegrees(realYaw + 180.0F) : realYaw, this.hoverPitch.getValue().floatValue());
                  this.hovering = true;
               } else {
                  this.stop();
               }
            }
         }
      } else {
         this.stop();
      }
   }

   private void tryAutoRocket() {
      this.ticksSinceRocket++;
      if (this.ticksSinceRocket >= this.refireTicks) {
         Result rocket = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE);
         if (rocket.found()) {
            this.ticksSinceRocket = 0;
            this.refireTicks = InventoryUtil.fireworkRefireTicks(rocket.stack());
            KetamineClub.swapManager.submit(new SwapRequest("ElytraFly", 15, rocket, () -> mc.gameMode.useItem(mc.player, rocket.hand())));
         }
      }
   }

   private float headingFromInput(float cameraYaw, float strafe, float fwd) {
      double yawRad = Math.toRadians(cameraYaw);
      double fx = -Math.sin(yawRad);
      double fz = Math.cos(yawRad);
      double rx = -Math.sin(yawRad + (Math.PI / 2));
      double rz = Math.cos(yawRad + (Math.PI / 2));
      double wx = fx * fwd + rx * strafe;
      double wz = fz * fwd + rz * strafe;
      return (float)Math.toDegrees(Math.atan2(wz, wx)) - 90.0F;
   }

   private void steerTo(float yaw, float pitch) {
      KetamineClub.rotationManager.submit(new RotationRequest("ElytraFly", 50, yaw, Mth.clamp(pitch, -90.0F, 90.0F), RotationRequest.Mode.MOTION, true, true));
      this.steering = true;
   }

   private void stop() {
      this.hovering = false;
      this.idlePhase = 0;
      this.ticksSinceRocket = 1073741823;
      this.yieldSteering();
   }

   private void yieldSteering() {
      if (this.steering) {
         KetamineClub.rotationManager.cancel("ElytraFly");
         this.steering = false;
      }
   }

   @Override
   public String getDisplayInfo() {
      if (this.hovering) {
         return "Hover";
      } else {
         return this.steering ? "Steering" : null;
      }
   }
}
