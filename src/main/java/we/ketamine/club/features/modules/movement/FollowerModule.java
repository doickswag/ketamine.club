package we.ketamine.club.features.modules.movement;

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.inventory.ChestSwapUtil;

public class FollowerModule extends Module {
   public static final String ID = "Follower";
   public static final int PRIORITY = 100;
   private static final long RETURN_TIME_MS = 300L;
   private final Setting<Float> range = this.num("Range", 60.0F, 0.0F, 128.0F);
   private final Setting<FollowerModule.SortPriority> sortPriority = this.mode("SortPriority", FollowerModule.SortPriority.ClosestAngle);
   private final Setting<Boolean> friend = this.bool("Friend", false);
   private final Setting<Boolean> onlyFlying = this.bool("OnlyFlying", true);
   private final Setting<Boolean> autoSwap = this.bool("AutoSwap", true);
   private final Setting<Boolean> autoTakeoff = this.bool("AutoTakeoff", true);
   private final Setting<Boolean> swapBack = this.bool("SwapBack", true);
   private Entity target;
   private float yaw;
   private float pitch;
   private boolean canFollow;
   private long lastReturnMs;

   public FollowerModule() {
      super("Follower", "Automatically flies towards and dive-attacks the nearest enemy using ElytraFly.", Module.Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      this.lastReturnMs = System.currentTimeMillis() - 9L;
   }

   @Override
   public void onDisable() {
      this.target = null;
      this.stopFollowing();
      this.restoreChestplate();
   }

   public boolean canFollow() {
      return this.canFollow;
   }

   public Player getTarget() {
      return this.target instanceof Player player ? player : null;
   }

   @Subscribe(
      priority = 10
   )
   private void onPreTick(PreTickEvent event) {
      if (nullCheck()) {
         this.clearTarget();
      } else {
         this.target = this.findTarget();
         if (this.target == null) {
            this.stopFollowing();
            if (this.swapBack.getValue()) {
               this.restoreChestplate();
            }
         } else if (this.wantsManualMovement()) {
            this.stopFollowing();
         } else {
            if (this.autoSwap.getValue() && !this.fakeFlyActive()) {
               ChestSwapUtil.SwapResult swap = ChestSwapUtil.equipElytra();
               if (!swap.ready()) {
                  this.stopFollowing();
                  return;
               }
            }

            if (this.onlyFlying.getValue() && !this.flightControllerReady()) {
               this.stopFollowing();
            } else {
               this.canFollow = true;
               long now = System.currentTimeMillis();
               boolean shouldReturn = now - this.lastReturnMs <= 300L;
               if (mc.player.getY() <= this.target.getY()) {
                  this.lastReturnMs = now;
                  shouldReturn = true;
               }

               if (mc.player.onGround()) {
                  mc.player.jumpFromGround();
                  this.lastReturnMs = now;
                  shouldReturn = true;
               } else if (this.autoTakeoff.getValue()
                  && !this.fakeFlyActive()
                  && !mc.player.isFallFlying()
                  && ChestSwapUtil.isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST))) {
                  mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.START_FALL_FLYING));
                  mc.player.startFallFlying();
               }

               float[] rotations = MathUtil.calcAngle(mc.player.getEyePosition(), closestPointToBox(mc.player.getEyePosition(), this.target.getBoundingBox()));
               this.yaw = rotations[0];
               this.pitch = shouldReturn ? -90.0F : rotations[1];
               KetamineClub.rotationManager.submit(new RotationRequest("Follower", 100, this.yaw, this.pitch, RotationRequest.Mode.MOTION, true, true));
            }
         }
      }
   }

   private boolean flightControllerReady() {
      if (this.fakeFlyActive()) {
         return true;
      } else {
         ElytraFlyModule elytraFly = KetamineClub.moduleManager.getModuleByClass(ElytraFlyModule.class);
         return elytraFly != null && elytraFly.isEnabled() ? ChestSwapUtil.isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST)) : false;
      }
   }

   private boolean fakeFlyActive() {
      FakeFlyModule fakeFly = KetamineClub.moduleManager.getModuleByClass(FakeFlyModule.class);
      return fakeFly != null && fakeFly.isEnabled();
   }

   public void applyTravelVelocity() {
      ElytraFlyModule elytraFly = KetamineClub.moduleManager.getModuleByClass(ElytraFlyModule.class);
      FakeFlyModule fakeFly = KetamineClub.moduleManager.getModuleByClass(FakeFlyModule.class);
      boolean controlled = elytraFly != null && elytraFly.isEnabled() || fakeFly != null && fakeFly.isEnabled();
      if (this.isEnabled() && this.canFollow && !nullCheck() && controlled && mc.player.isFallFlying()) {
         Vec3 velocity = mc.player.getDeltaMovement();
         double speed = velocity.length();
         if (!(speed < 0.01)) {
            double yawRadians = Math.toRadians(this.yaw);
            double pitchRadians = Math.toRadians(this.pitch);
            double horizontal = Math.cos(pitchRadians) * speed;
            mc.player.setDeltaMovement(-Math.sin(yawRadians) * horizontal, -Math.sin(pitchRadians) * speed, Math.cos(yawRadians) * horizontal);
         }
      }
   }

   private boolean wantsManualMovement() {
      return mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
   }

   private Entity findTarget() {
      Entity best = null;

      for (Player entity : mc.level.players()) {
         if (entity != mc.player
            && entity.isAlive()
            && (this.friend.getValue() || !KetamineClub.friendManager.isFriend(entity.getName().getString()))
            && !(mc.player.distanceTo(entity) > this.range.getValue())
            && (best == null || this.isBetterTarget(entity, best))) {
            best = entity;
         }
      }

      return best;
   }

   private boolean isBetterTarget(Entity candidate, Entity current) {
      double candidateDistance = mc.player.distanceTo(candidate);
      double currentDistance = mc.player.distanceTo(current);

      return switch ((FollowerModule.SortPriority)this.sortPriority.getValue()) {
         case ClosestAngle -> this.yawDifference(candidate) < this.yawDifference(current);
         case LowestDistance -> candidateDistance < currentDistance;
         case LowestHealth -> {
            float candidateHealth = health(candidate);
            float currentHealth = health(current);
            yield candidateHealth < currentHealth || candidateHealth == currentHealth && candidateDistance < currentDistance;
         }
      };
   }

   private float yawDifference(Entity entity) {
      float targetYaw = MathUtil.calcAngle(mc.player.position(), entity.position())[0];
      return Math.abs(Mth.wrapDegrees(mc.player.getYRot() - targetYaw));
   }

   private static float health(Entity entity) {
      return entity instanceof LivingEntity living ? living.getHealth() + living.getAbsorptionAmount() : 0.0F;
   }

   private static Vec3 closestPointToBox(Vec3 point, AABB box) {
      return new Vec3(Mth.clamp(point.x, box.minX, box.maxX), Mth.clamp(point.y, box.minY, box.maxY), Mth.clamp(point.z, box.minZ, box.maxZ));
   }

   private void clearTarget() {
      this.target = null;
      this.stopFollowing();
   }

   private void stopFollowing() {
      this.canFollow = false;
      KetamineClub.rotationManager.cancel("Follower");
   }

   private void restoreChestplate() {
      if (!nullCheck() && ChestSwapUtil.isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST))) {
         ChestSwapUtil.equipChestplate();
      }
   }

   @Override
   public String getDisplayInfo() {
      return this.target != null ? this.target.getName().getString() : null;
   }

   public static enum SortPriority {
      ClosestAngle,
      LowestDistance,
      LowestHealth;

      private SortPriority() {
      }
   }
}
