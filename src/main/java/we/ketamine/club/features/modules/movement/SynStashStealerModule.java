package we.ketamine.club.features.modules.movement;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.network.DisconnectEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.player.ChatUtil;

public class SynStashStealerModule extends Module {
   private static final String ID = "SynStashStealer";
   private static final int ROTATION_PRIORITY = 110;
   private static final int ROCKET_SWAP_PRIORITY = 15;
   private static final double TARGET_X = -34134.0;
   private static final double TARGET_Y = 57.0;
   private static final double TARGET_Z = -1.5162593E7;
   private static final float DESCENT_PITCH = 37.72F;
   private static final float CLIMB_PITCH = -54.77F;
   private static final float ROTATE_UP_SPEED = 5.45F;
   private static final float ROTATE_DOWN_SPEED = 0.9F;
   private static final double ELYTRA_TEST_FRICTION = 0.0264444413;
   private static final long ETA_UPDATE_NANOS = 5000000000L;
   private final Setting<Double> lowerY = this.num("LowerY", 180.0, -128.0, 512.0);
   private final Setting<Double> upperY = this.num("UpperY", 220.0, -128.0, 512.0);
   private final Setting<Double> logoutY = this.num("LogoutY", 0.0, -128.0, 512.0);
   private final Setting<Integer> logoutDistance = this.num("LogoutDistance", 500, 200, 2000);
   private final Setting<Boolean> test = this.bool("Test", false);
   private final Setting<Boolean> elytraTestBoost = this.bool("ElytraTestBoost", false);
   private boolean pitchingDown;
   private float pitch;
   private SynStashStealerModule.AltitudeState altitudeState;
   private Vec3 previousPosition;
   private double distanceTravelled;
   private long trackingStartedNanos;
   private long nextEtaUpdateNanos;
   private int rocketCooldownTicks;
   private boolean climbAttemptActive;
   private boolean climbSweepComplete;
   private boolean rocketRecovery;
   private boolean lastTestMode;
   private String eta;

   public SynStashStealerModule() {
      super("SynStashStealer", "Pitch40 autopilot to Syn's fixed End stash coordinates.", Module.Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      this.disableConflict(ElytraFlyModule.class);
      this.disableConflict(ElytraTestModule.class);
      this.disableConflict(FakeFlyModule.class);
      this.disableConflict(BhopModule.class);
      this.disableConflict(ElytraDashModule.class);
      this.pitchingDown = true;
      this.pitch = 37.72F;
      this.altitudeState = null;
      this.rocketCooldownTicks = 0;
      this.climbAttemptActive = false;
      this.climbSweepComplete = false;
      this.rocketRecovery = false;
      this.lastTestMode = this.test.getValue();
      this.resetTracking();
   }

   @Override
   public void onDisable() {
      if (KetamineClub.rotationManager != null) {
         KetamineClub.rotationManager.cancel("SynStashStealer");
      }

      this.resetTracking();
      this.altitudeState = null;
      this.rocketCooldownTicks = 0;
      this.climbAttemptActive = false;
      this.climbSweepComplete = false;
      this.rocketRecovery = false;
   }

   @Subscribe
   private void onDisconnect(DisconnectEvent event) {
      this.resetTracking();
      this.altitudeState = null;
      this.rocketCooldownTicks = 0;
      this.climbAttemptActive = false;
      this.climbSweepComplete = false;
      this.rocketRecovery = false;
   }

   @Subscribe(
      priority = 20
   )
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (this.lastTestMode != this.test.getValue()) {
            this.lastTestMode = this.test.getValue();
            this.resetTracking();
         }

         if (!this.test.getValue() && !mc.level.dimension().equals(Level.END)) {
            ChatUtil.sendMessage(Component.literal("SynStashStealer only works in the End."));
            this.disable();
         } else if (this.lowerY.getValue() >= this.upperY.getValue()) {
            ChatUtil.sendMessage(Component.literal("SynStashStealer requires LowerY to be below UpperY."));
            this.disable();
         } else {
            this.updateTracking();
            double distance = this.distanceToTarget();
            if (distance <= this.logoutDistance.getValue().intValue()) {
               String destination = this.test.getValue() ? "test target" : "Syn stash";
               this.disconnect("Reached " + destination + " logout distance (" + formatWhole(distance) + " blocks).");
            } else if (mc.player.getY() <= this.logoutY.getValue()) {
               this.disconnect("Reached SynStashStealer logout Y (" + formatNumber(this.logoutY.getValue()) + ").");
            } else {
               this.updateAltitudeState();
               if (!mc.player.isFallFlying()) {
                  KetamineClub.rotationManager.cancel("SynStashStealer");
                  this.rocketCooldownTicks = 0;
               } else {
                  this.updatePitch();
                  this.tryAutoFirework();
                  float yaw = this.yawToTarget();
                  KetamineClub.rotationManager.submit(new RotationRequest("SynStashStealer", 110, yaw, this.pitch, RotationRequest.Mode.MOTION, true, true));
                  if (this.elytraTestBoost.getValue() && !mc.player.isInWater() && !mc.player.isInLava() && mc.player.getFoodData().getFoodLevel() > 6.0F) {
                     this.applyElytraTestBoost(yaw, this.pitch);
                  }
               }
            }
         }
      }
   }

   private void tryAutoFirework() {
      if (!this.rocketRecovery) {
         this.rocketCooldownTicks = 0;
      } else if (this.rocketCooldownTicks > 0) {
         this.rocketCooldownTicks--;
      } else {
         Result rocket = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE);
         if (rocket.found()) {
            if (KetamineClub.swapManager.submit(new SwapRequest("SynStashStealer.rocket", 15, rocket, result -> mc.gameMode.useItem(mc.player, result.hand())))
               )
             {
               this.rocketCooldownTicks = InventoryUtil.fireworkRefireTicks(rocket.stack());
            }
         }
      }
   }

   private void updatePitch() {
      double y = mc.player.getY();
      if (y >= this.upperY.getValue()) {
         this.climbAttemptActive = false;
         this.climbSweepComplete = false;
         this.rocketRecovery = false;
         this.rocketCooldownTicks = 0;
         this.pitchingDown = true;
      }

      if (this.rocketRecovery) {
         this.pitch = -54.77F;
      } else if (this.climbAttemptActive && this.climbSweepComplete && y < this.upperY.getValue() && mc.player.getDeltaMovement().y <= 0.0) {
         this.rocketRecovery = true;
         this.pitch = -54.77F;
      } else {
         if (this.pitchingDown && y <= this.lowerY.getValue()) {
            this.pitchingDown = false;
            if (!this.climbAttemptActive) {
               this.climbAttemptActive = true;
               this.climbSweepComplete = false;
            }
         } else if (!this.pitchingDown && y >= this.upperY.getValue()) {
            this.pitchingDown = true;
         }

         if (!this.pitchingDown) {
            this.pitch = this.pitch - randomAround(5.45F, 1.0F);
            if (this.pitch < -54.77F) {
               this.pitch = -54.77F;
               this.pitchingDown = true;
               if (this.climbAttemptActive) {
                  this.climbSweepComplete = true;
               }
            }
         } else if (this.pitch < 37.72F) {
            this.pitch = this.pitch + randomAround(0.9F, 0.5F);
            this.pitch = Math.min(this.pitch, 37.72F);
         }
      }
   }

   private void updateAltitudeState() {
      SynStashStealerModule.AltitudeState next;
      if (mc.player.getY() < this.lowerY.getValue()) {
         next = SynStashStealerModule.AltitudeState.BELOW;
      } else if (mc.player.getY() > this.upperY.getValue()) {
         next = SynStashStealerModule.AltitudeState.ABOVE;
      } else {
         next = SynStashStealerModule.AltitudeState.WITHIN;
      }

      if (next != this.altitudeState) {
         this.altitudeState = next;
         String top = formatNumber(this.upperY.getValue());
         if (next == SynStashStealerModule.AltitudeState.BELOW) {
            ChatUtil.sendSilentMessage(Component.literal("Move up to " + top));
         } else if (next == SynStashStealerModule.AltitudeState.ABOVE) {
            ChatUtil.sendSilentMessage(Component.literal("Moving down to " + top));
         }
      }
   }

   private void applyElytraTestBoost(float yaw, float boostPitch) {
      double yawRadians = Math.toRadians(yaw);
      double pitchRadians = Math.toRadians(boostPitch);
      double horizontal = Math.cos(pitchRadians);
      double lookX = -Math.sin(yawRadians) * horizontal;
      double lookY = -Math.sin(pitchRadians);
      double lookZ = Math.cos(yawRadians) * horizontal;
      Vec3 velocity = mc.player.getDeltaMovement();
      mc.player.setDeltaMovement(velocity.x + lookX * 0.0264444413, velocity.y + lookY * 0.0264444413, velocity.z + lookZ * 0.0264444413);
   }

   private void updateTracking() {
      long now = System.nanoTime();
      Vec3 current = mc.player.position();
      if (this.previousPosition == null) {
         this.previousPosition = current;
         this.trackingStartedNanos = now;
         this.nextEtaUpdateNanos = now + 5000000000L;
      } else {
         this.distanceTravelled = this.distanceTravelled + Math.hypot(current.x - this.previousPosition.x, current.z - this.previousPosition.z);
         this.previousPosition = current;
         if (now >= this.nextEtaUpdateNanos) {
            this.nextEtaUpdateNanos = now + 5000000000L;
            double elapsedSeconds = (now - this.trackingStartedNanos) / 1.0E9;
            double averageSpeed = elapsedSeconds <= 0.0 ? 0.0 : this.distanceTravelled / elapsedSeconds;
            double remaining = this.distanceToTarget();
            this.eta = averageSpeed < 0.05 ? "--:--" : formatMinutesSeconds(remaining / averageSpeed);
            ChatUtil.sendPersistent(
               "synstashstealer:eta",
               Component.literal(
                  String.format(
                     Locale.ROOT,
                     "%s: %s blocks | ETA %s | AVG %.1f blocks/sec",
                     this.test.getValue() ? "Test target" : "Stash",
                     formatWhole(remaining),
                     this.eta,
                     averageSpeed
                  )
               )
            );
         }
      }
   }

   private void resetTracking() {
      this.previousPosition = null;
      this.distanceTravelled = 0.0;
      this.trackingStartedNanos = 0L;
      this.nextEtaUpdateNanos = 0L;
      this.eta = null;
   }

   private float yawToTarget() {
      double deltaX = this.targetX() - mc.player.getX();
      double deltaZ = this.targetZ() - mc.player.getZ();
      return Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-deltaX, deltaZ)));
   }

   private double distanceToTarget() {
      double deltaX = this.targetX() - mc.player.getX();
      double deltaY = this.test.getValue() ? 0.0 : 57.0 - mc.player.getY();
      double deltaZ = this.targetZ() - mc.player.getZ();
      return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
   }

   private double targetX() {
      return this.test.getValue() ? 0.0 : -34134.0;
   }

   private double targetZ() {
      return this.test.getValue() ? 0.0 : -1.5162593E7;
   }

   private void disconnect(String reason) {
      if (mc.getConnection() != null) {
         mc.getConnection().getConnection().disconnect(Component.literal(reason));
      }

      this.disable();
   }

   private <T extends Module> void disableConflict(Class<T> type) {
      T module = KetamineClub.moduleManager.getModuleByClass(type);
      if (module != null && module.isEnabled()) {
         module.disable();
      }
   }

   private static float randomAround(float value, float variance) {
      return (float)(value + variance * (Math.random() - 0.5));
   }

   private static String formatMinutesSeconds(double seconds) {
      long totalSeconds = Math.max(0L, Math.round(seconds));
      return totalSeconds / 60L + ":" + String.format(Locale.ROOT, "%02d", totalSeconds % 60L);
   }

   private static String formatWhole(double value) {
      return String.format(Locale.ROOT, "%,.0f", value);
   }

   private static String formatNumber(double value) {
      return value == Math.rint(value) ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
   }

   @Override
   public String getDisplayInfo() {
      if (nullCheck()) {
         return null;
      } else {
         return this.eta == null ? formatWhole(this.distanceToTarget()) : this.eta;
      }
   }

   private static enum AltitudeState {
      BELOW,
      WITHIN,
      ABOVE;

      private AltitudeState() {
      }
   }
}
