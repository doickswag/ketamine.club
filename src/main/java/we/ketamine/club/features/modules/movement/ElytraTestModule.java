package we.ketamine.club.features.modules.movement;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.mixin.entity.FireworkRocketEntityAccessor;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class ElytraTestModule extends Module {
   private static final String ROCKET_SWAP_ID = "ElytraTest.rocket";
   private static final int ROCKET_SWAP_PRIORITY = 15;
   private static final double GRIM_FIREWORK_RESCALE = 1.65;
   private static final double GRIM_FIREWORK_PROBE = 1.7;
   private static final double BOUNDARY_MARGIN = 0.002;
   private static final double TICKS_PER_SECOND = 20.0;
   private static final float YAW_JITTER = 0.01F;
   private final Setting<Double> speedLimit = this.num("SpeedLimit", 125.0, 20.0, 200.0);
   private final Setting<Boolean> requireJump = this.bool("RequireJump", true);
   private final Setting<Boolean> autoRocket = this.bool("AutoRocket", true);
   private final Setting<Boolean> inventoryRockets = this.bool("InventoryRockets", true);
   private final Setting<Integer> refireLead = this.num("RefireLead", 3, 0, 10);
   private boolean boosting;
   private int rocketTicks;
   private Vec3 previousSentPosition;
   private Vec3 previousVelocity = Vec3.ZERO;
   private boolean previousVelocityValid;
   private float previousSentYaw;
   private float previousSentPitch;
   private boolean previousRotationValid;
   private Vec3 overridingGlideVelocity;
   private float appliedYawJitter;
   private boolean positiveYawJitter;
   private int rocketRequestCooldown;
   private Vec3 diagnosticBase;
   private Vec3 diagnosticDesired;
   private Vec3 diagnosticProbe;
   private Vec3 diagnosticOverride;
   private double[] diagnosticBounds;
   private double diagnosticMaxScale;
   private String state;

   public ElytraTestModule() {
      super("ElytraTest", "Rocket-aware Grim elytra boost with ping-independent prediction tracking.", Module.Category.MOVEMENT);
      this.inventoryRockets.setVisibility(value -> this.autoRocket.getValue());
      this.refireLead.setVisibility(value -> this.autoRocket.getValue());
   }

   @Override
   public void onEnable() {
      this.resetState();
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   private void resetState() {
      this.clearYawJitter();
      this.boosting = false;
      this.rocketTicks = 0;
      this.previousSentPosition = null;
      this.previousVelocity = Vec3.ZERO;
      this.previousVelocityValid = false;
      this.previousRotationValid = false;
      this.overridingGlideVelocity = null;
      this.positiveYawJitter = false;
      this.rocketRequestCooldown = 0;
      this.clearDiagnostics();
      this.state = null;
   }

   @Subscribe(
      priority = 2
   )
   private void onPreTick(PreTickEvent event) {
      this.clearYawJitter();
      this.boosting = false;
      this.overridingGlideVelocity = null;
      this.state = null;
      if (this.rocketTicks > 0) {
         this.rocketTicks--;
      }

      if (this.rocketRequestCooldown > 0) {
         this.rocketRequestCooldown--;
      }

      if (nullCheck() || !mc.player.isFallFlying()) {
         this.rocketTicks = 0;
         this.previousSentPosition = null;
         this.previousVelocity = Vec3.ZERO;
         this.previousVelocityValid = false;
         this.previousRotationValid = false;
         this.clearDiagnostics();
      } else if (!mc.player.isInWater() && !mc.player.isInLava()) {
         if (!(mc.player.getFoodData().getFoodLevel() <= 6.0F)) {
            if (!this.requireJump.getValue() || mc.options.keyJump.isDown()) {
               if (this.previousSentPosition == null) {
                  this.previousSentPosition = mc.player.position();
               }

               FireworkRocketEntity attachedRocket = this.findNewestAttachedRocket();
               if (attachedRocket != null) {
                  this.rocketTicks = Math.max(this.rocketTicks, this.safeRocketEnd(attachedRocket) - this.rocketAge(attachedRocket));
               }

               if (this.autoRocket.getValue() && this.rocketRequestCooldown <= 0 && this.rocketTicks <= this.effectiveRefireLead()) {
                  this.fireRocket();
               }

               if (this.rocketTicks <= 0 && (attachedRocket == null || !this.isRocketSafe(attachedRocket))) {
                  if (this.state == null) {
                     this.state = attachedRocket == null ? "Rocket Sync" : "Rocket Refill";
                  }
               } else {
                  this.applyGrimFireworkPrediction();
                  this.applyYawJitter();
                  this.boosting = true;
               }
            }
         }
      }
   }

   private void fireRocket() {
      Result rocket = InventoryUtil.find(Items.FIREWORK_ROCKET, this.inventoryRockets.getValue() ? InventoryUtil.FULL_SCOPE : InventoryUtil.HOTBAR_SCOPE);
      if (!rocket.found()) {
         this.state = "No Rocket";
      } else {
         if (KetamineClub.swapManager.submit(new SwapRequest("ElytraTest.rocket", 15, rocket, result -> mc.gameMode.useItem(mc.player, result.hand())))) {
            this.rocketTicks = InventoryUtil.fireworkRefireTicks(rocket.stack());
            this.rocketRequestCooldown = Math.max(4, this.latencyTicks() + 2);
            this.state = "Rocket";
         }
      }
   }

   private FireworkRocketEntity findNewestAttachedRocket() {
      FireworkRocketEntity newest = null;

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity instanceof FireworkRocketEntity rocket
            && ((FireworkRocketEntityAccessor)rocket).ketamineclub$getAttachedToEntity() == mc.player
            && (newest == null || this.rocketAge(rocket) < this.rocketAge(newest))) {
            newest = rocket;
         }
      }

      return newest;
   }

   private boolean isRocketSafe(FireworkRocketEntity rocket) {
      return this.rocketAge(rocket) < this.safeRocketEnd(rocket);
   }

   private int safeRocketEnd(FireworkRocketEntity rocket) {
      Fireworks fireworks = (Fireworks)rocket.getItem().get(DataComponents.FIREWORKS);
      int flightDuration = fireworks == null ? 0 : fireworks.flightDuration();
      int minimumLifetime = 10 * (1 + flightDuration);
      return Math.max(1, minimumLifetime - this.latencyTicks() - 3);
   }

   private int rocketAge(FireworkRocketEntity rocket) {
      return ((FireworkRocketEntityAccessor)rocket).ketamineclub$getLife();
   }

   private int effectiveRefireLead() {
      return this.refireLead.getValue() + this.latencyTicks() + 2;
   }

   private int latencyTicks() {
      int latency = 0;
      if (mc.getConnection() != null) {
         PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
         if (info != null) {
            latency = Math.max(0, info.getLatency());
         }
      }

      return (latency + 49) / 50;
   }

   private void applyYawJitter() {
      this.positiveYawJitter = !this.positiveYawJitter;
      this.appliedYawJitter = this.positiveYawJitter ? 0.01F : -0.01F;
      mc.player.setYRot(mc.player.getYRot() + this.appliedYawJitter);
   }

   private void clearYawJitter() {
      if (this.appliedYawJitter != 0.0F) {
         if (mc.player != null) {
            mc.player.setYRot(mc.player.getYRot() - this.appliedYawJitter);
         }

         this.appliedYawJitter = 0.0F;
      }
   }

   private void applyGrimFireworkPrediction() {
      Vec3 aiming = this.getLookDirection();
      float lastYaw = this.previousRotationValid ? this.previousSentYaw : mc.player.getYRot();
      float lastPitch = this.previousRotationValid ? this.previousSentPitch : mc.player.getXRot();
      Vec3 predictionBase = this.previousVelocityValid ? this.previousVelocity : mc.player.getDeltaMovement();
      double[] bounds = ElytraTest2Module.GrimFireworkBox.getFireworkBounds(predictionBase, aiming, lastYaw, lastPitch, true, 1.65, mc.player.getGravity());
      Vec3 probeMotion = aiming.scale(1.7);
      double exceedX = this.axisExceed(probeMotion.x, bounds[0], bounds[3]);
      double exceedY = this.axisExceed(probeMotion.y, bounds[1], bounds[4]);
      double exceedZ = this.axisExceed(probeMotion.z, bounds[2], bounds[5]);
      boolean horizontalRescale = this.usesHorizontalRescale(mc.player.getXRot());
      double maxScale = horizontalRescale ? Math.max(exceedX, exceedZ) : Math.max(exceedX, Math.max(exceedY, exceedZ));
      if (Double.isFinite(maxScale) && !(maxScale < 1.0E-6)) {
         Vec3 predictedProbe = ElytraTest2Module.GrimFireworkBox.calcGlidingVelocity(probeMotion, aiming, mc.player.getGravity());
         double limitScale = probeMotion.length() / predictedProbe.length();
         Vec3 rescaledMotion = maxScale >= limitScale ? predictedProbe : probeMotion.scale(1.0 / maxScale);
         if (horizontalRescale) {
            if (rescaledMotion.y > 0.0) {
               rescaledMotion = new Vec3(rescaledMotion.x, bounds[4], rescaledMotion.z);
            } else if (rescaledMotion.y < 0.0) {
               rescaledMotion = new Vec3(rescaledMotion.x, bounds[1], rescaledMotion.z);
            }
         }

         Vec3 safeMotion = this.moveInsideBounds(rescaledMotion, bounds);
         safeMotion = this.applySpeedCeiling(safeMotion, bounds);
         this.diagnosticBase = predictionBase;
         this.diagnosticDesired = rescaledMotion;
         this.diagnosticProbe = probeMotion;
         this.diagnosticOverride = safeMotion;
         this.diagnosticBounds = bounds;
         this.diagnosticMaxScale = maxScale;
         this.overridingGlideVelocity = safeMotion;
         mc.player.setDeltaMovement(probeMotion);
         this.logPredictionTick(this.attachedRocketId(), horizontalRescale);
      }
   }

   private boolean usesHorizontalRescale(float pitch) {
      return pitch >= 6.0F && pitch <= 60.0F || pitch >= -70.0F && pitch <= -7.0F;
   }

   private Vec3 moveInsideBounds(Vec3 motion, double[] bounds) {
      return new Vec3(
         this.clampAxis(motion.x, bounds[0], bounds[3]), this.clampAxis(motion.y, bounds[1], bounds[4]), this.clampAxis(motion.z, bounds[2], bounds[5])
      );
   }

   private Vec3 applySpeedCeiling(Vec3 motion, double[] bounds) {
      double maximumSpeed = this.speedLimit.getValue() / 20.0;
      if (motion.lengthSqr() <= maximumSpeed * maximumSpeed) {
         return motion;
      } else {
         Vec3 minimumLegalMotion = this.moveInsideBounds(Vec3.ZERO, bounds);
         if (minimumLegalMotion.lengthSqr() >= maximumSpeed * maximumSpeed) {
            return minimumLegalMotion;
         } else {
            double minimumScale = 0.0;
            double maximumScale = 1.0;

            for (int iteration = 0; iteration < 24; iteration++) {
               double scale = (minimumScale + maximumScale) * 0.5;
               Vec3 scaled = this.moveInsideBounds(motion.scale(scale), bounds);
               if (scaled.lengthSqr() <= maximumSpeed * maximumSpeed) {
                  minimumScale = scale;
               } else {
                  maximumScale = scale;
               }
            }

            return this.moveInsideBounds(motion.scale(minimumScale), bounds);
         }
      }
   }

   private int attachedRocketId() {
      FireworkRocketEntity rocket = this.findNewestAttachedRocket();
      return rocket == null ? -1 : rocket.getId();
   }

   private void logPredictionTick(int rocketId, boolean horizontalRescale) {
      KetamineClub.LOGGER
         .info(
            "[ElytraTest] tick rocket={} timer={} mode={} speed={} previous={} base={} target={} probe={} bounds={} scale={} override={}",
            new Object[]{
               rocketId,
               this.rocketTicks,
               horizontalRescale ? "V3" : "V2",
               this.diagnosticOverride.length() * 20.0,
               this.formatVector(this.previousVelocity),
               this.formatVector(this.diagnosticBase),
               this.formatVector(this.diagnosticDesired),
               this.formatVector(this.diagnosticProbe),
               this.formatBounds(this.diagnosticBounds),
               this.diagnosticMaxScale,
               this.formatVector(this.diagnosticOverride)
            }
         );
   }

   private double clampAxis(double value, double minimum, double maximum) {
      double safeMinimum = minimum + 0.002;
      double safeMaximum = maximum - 0.002;
      return safeMinimum > safeMaximum ? (minimum + maximum) * 0.5 : Math.max(safeMinimum, Math.min(safeMaximum, value));
   }

   private double axisExceed(double motion, double minimum, double maximum) {
      if (motion > 0.0) {
         return motion / maximum;
      } else {
         return motion < 0.0 ? motion / minimum : 0.0;
      }
   }

   private void clearDiagnostics() {
      this.diagnosticBase = null;
      this.diagnosticDesired = null;
      this.diagnosticProbe = null;
      this.diagnosticOverride = null;
      this.diagnosticBounds = null;
      this.diagnosticMaxScale = 0.0;
   }

   private String formatVector(Vec3 vector) {
      return vector == null ? "null" : String.format("(%.4f,%.4f,%.4f)", vector.x, vector.y, vector.z);
   }

   private String formatBounds(double[] bounds) {
      return bounds == null ? "null" : String.format("[(%.4f,%.4f,%.4f),(%.4f,%.4f,%.4f)]", bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
   }

   private Vec3 getLookDirection() {
      boolean rotating = KetamineClub.rotationManager != null && KetamineClub.rotationManager.isRotating();
      float yaw = rotating ? KetamineClub.rotationManager.getRotationYaw() : mc.player.getYRot();
      float pitch = rotating ? KetamineClub.rotationManager.getRotationPitch() : mc.player.getXRot();
      return Vec3.directionFromRotation(pitch, yaw);
   }

   public Vec3 requestGlideVelocityOverride() {
      Vec3 velocity = this.overridingGlideVelocity;
      this.overridingGlideVelocity = null;
      return velocity;
   }

   @Subscribe(
      priority = -100
   )
   private void onPacketSend(PacketEvent.Send event) {
      if (!nullCheck()) {
         if (event.getPacket() instanceof ServerboundUseItemPacket packet && mc.player.getItemInHand(packet.getHand()).is(Items.FIREWORK_ROCKET)) {
            this.rocketTicks = Math.max(this.rocketTicks, InventoryUtil.fireworkRefireTicks(mc.player.getItemInHand(packet.getHand())));
         }

         if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            if (packet.hasRotation()) {
               this.previousSentYaw = packet.getYRot(mc.player.getYRot());
               this.previousSentPitch = packet.getXRot(mc.player.getXRot());
               this.previousRotationValid = true;
            }

            if (packet.hasPosition()) {
               boolean trackedPreviousPosition = this.previousSentPosition != null;
               Vec3 base = trackedPreviousPosition ? this.previousSentPosition : mc.player.position();
               Vec3 next = new Vec3(packet.getX(base.x), packet.getY(base.y), packet.getZ(base.z));
               this.previousVelocity = next.subtract(base);
               this.previousSentPosition = next;
               this.previousVelocityValid = trackedPreviousPosition;
            }
         }
      }
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
         if (mc.player != null && mc.player.isFallFlying()) {
            FireworkRocketEntity rocket = this.findNewestAttachedRocket();
            KetamineClub.LOGGER
               .info(
                  "[ElytraTest] correction rocket={} age={} safeEnd={} timer={} speed={} previous={} base={} desired={} probe={} bounds={} scale={} override={}",
                  new Object[]{
                     rocket == null ? -1 : rocket.getId(),
                     rocket == null ? -1 : this.rocketAge(rocket),
                     rocket == null ? -1 : this.safeRocketEnd(rocket),
                     this.rocketTicks,
                     mc.player.getDeltaMovement().length() * 20.0,
                     this.formatVector(this.previousVelocity),
                     this.formatVector(this.diagnosticBase),
                     this.formatVector(this.diagnosticDesired),
                     this.formatVector(this.diagnosticProbe),
                     this.formatBounds(this.diagnosticBounds),
                     this.diagnosticMaxScale,
                     this.formatVector(this.diagnosticOverride)
                  }
               );
            this.previousSentPosition = null;
            this.previousVelocity = Vec3.ZERO;
            this.previousVelocityValid = false;
            this.previousRotationValid = false;
            this.clearDiagnostics();
         }
      }
   }

   @Override
   public String getDisplayInfo() {
      if (this.state != null) {
         return this.state;
      } else {
         return this.boosting && mc.player != null ? Math.round(mc.player.getDeltaMovement().length() * 20.0) + " b/s" : null;
      }
   }
}
