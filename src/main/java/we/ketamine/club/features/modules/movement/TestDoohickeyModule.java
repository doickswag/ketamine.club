package we.ketamine.club.features.modules.movement;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;

public class TestDoohickeyModule extends Module {
   private static final long SECOND_NANOS = 1000000000L;
   private static final int MAX_CATCH_UP_SENDS = 4;
   private static final String MOTION_ROTATION_ID = "TestDoohickeyMotion";
   private static final int MOTION_ROTATION_PRIORITY = 60;
   private final Setting<Boolean> startFly = this.bool("StartFly", true);
   private final Setting<Boolean> stopFly = this.bool("StopFly", true);
   private final Setting<Integer> rate = this.num("PacketsPerSecond", 20, 1, 40);
   private final Setting<Boolean> async = this.bool("Async", true);
   private final Setting<Boolean> takeoffSpam = this.bool("TakeoffSpam", false);
   private final Setting<TestDoohickeyModule.TakeoffMode> takeoffMode = this.mode("TakeoffMode", TestDoohickeyModule.TakeoffMode.VELOCITY);
   private final Setting<Boolean> recast = this.bool("Recast", false);
   private final Setting<Boolean> groundSpoof = this.bool("GroundSpoof", false);
   private final Setting<TestDoohickeyModule.GroundMode> groundMode = this.mode("GroundMode", TestDoohickeyModule.GroundMode.ALTERNATE);
   private final Setting<Boolean> collisionSpoof = this.bool("CollisionSpoof", false);
   private final Setting<TestDoohickeyModule.CollisionMode> collisionMode = this.mode("CollisionMode", TestDoohickeyModule.CollisionMode.ALTERNATE);
   private final Setting<Boolean> positionJitter = this.bool("PositionJitter", false);
   private final Setting<Double> horizontalJitter = this.num("HorizontalJitter", 0.03, 0.0, 1.0);
   private final Setting<Double> verticalJitter = this.num("VerticalJitter", 0.03, 0.0, 1.0);
   private final Setting<Boolean> pitchFlip = this.bool("PitchFlip", false);
   private final Setting<TestDoohickeyModule.PitchMode> pitchMode = this.mode("PitchMode", TestDoohickeyModule.PitchMode.ALTERNATE);
   private final Setting<Boolean> yawSpin = this.bool("YawSpin", false);
   private final Setting<Double> yawStep = this.num("YawStep", 45.0, -180.0, 180.0);
   private final Setting<Boolean> sprintPulse = this.bool("SprintPulse", false);
   private final Setting<Boolean> sneakPulse = this.bool("SneakPulse", false);
   private final Setting<Boolean> glideFlip = this.bool("GlideFlip", false);
   private final Setting<Boolean> gravityFlip = this.bool("GravityFlip", false);
   private final Setting<Boolean> velocityPulse = this.bool("VelocityPulse", false);
   private final Setting<Double> horizontalImpulse = this.num("HorizontalImpulse", 0.1, -0.5, 0.5);
   private final Setting<Double> verticalImpulse = this.num("VerticalImpulse", 0.0, -0.5, 0.5);
   private final Setting<Boolean> strafePulse = this.bool("StrafePulse", false);
   private final Setting<Double> strafeImpulse = this.num("StrafeImpulse", 0.1, -0.5, 0.5);
   private final Setting<Boolean> motionScale = this.bool("MotionScale", false);
   private final Setting<Double> motionMultiplier = this.num("MotionMultiplier", 0.8, -2.0, 2.0);
   private final Setting<Boolean> motionRotations = this.bool("MotionRotations", false).setPage("MotionRotation");
   private final Setting<Boolean> motionOnlyGliding = this.bool("MotionOnlyGliding", true).setPage("MotionRotation");
   private final Setting<TestDoohickeyModule.MotionYawSource> motionYawSource = this.mode("MotionYawSource", TestDoohickeyModule.MotionYawSource.LOOK)
      .setPage("MotionRotation");
   private final Setting<TestDoohickeyModule.MotionPitchSource> motionPitchSource = this.mode("MotionPitchSource", TestDoohickeyModule.MotionPitchSource.LOOK)
      .setPage("MotionRotation");
   private final Setting<Double> motionFixedPitch = this.num("MotionFixedPitch", 0.0, -90.0, 90.0).setPage("MotionRotation");
   private final Setting<Boolean> motionManagerJitter = this.bool("MotionManagerJitter", false).setPage("MotionRotation");
   private final Setting<Boolean> motionReverseYaw = this.bool("MotionReverseYaw", false).setPage("MotionRotation");
   private final Setting<Boolean> motionInvertPitch = this.bool("MotionInvertPitch", false).setPage("MotionRotation");
   private final Setting<Boolean> motionYawOffset = this.bool("MotionYawOffset", false).setPage("MotionRotation");
   private final Setting<Double> motionYawOffsetAmount = this.num("MotionYawOffsetAmount", 45.0, -180.0, 180.0).setPage("MotionRotation");
   private final Setting<Boolean> motionPitchOffset = this.bool("MotionPitchOffset", false).setPage("MotionRotation");
   private final Setting<Double> motionPitchOffsetAmount = this.num("MotionPitchOffsetAmount", 15.0, -90.0, 90.0).setPage("MotionRotation");
   private final Setting<Boolean> motionYawSpin = this.bool("MotionYawSpin", false).setPage("MotionRotation");
   private final Setting<Double> motionYawSpinStep = this.num("MotionYawSpinStep", 30.0, -180.0, 180.0).setPage("MotionRotation");
   private final Setting<Boolean> motionYawFlip = this.bool("MotionYawFlip", false).setPage("MotionRotation");
   private final Setting<Double> motionYawFlipAngle = this.num("MotionYawFlipAngle", 180.0, -180.0, 180.0).setPage("MotionRotation");
   private final Setting<Boolean> motionPitchFlip = this.bool("MotionPitchFlip", false).setPage("MotionRotation");
   private final Setting<Double> motionPitchFlipA = this.num("MotionPitchFlipA", -90.0, -90.0, 90.0).setPage("MotionRotation");
   private final Setting<Double> motionPitchFlipB = this.num("MotionPitchFlipB", 90.0, -90.0, 90.0).setPage("MotionRotation");
   private final Setting<Boolean> motionYawWave = this.bool("MotionYawWave", false).setPage("MotionRotation");
   private final Setting<Double> motionYawWaveAmount = this.num("MotionYawWaveAmount", 45.0, 0.0, 180.0).setPage("MotionRotation");
   private final Setting<Integer> motionYawWavePeriod = this.num("MotionYawWavePeriod", 20, 2, 200).setPage("MotionRotation");
   private final Setting<Boolean> motionPitchWave = this.bool("MotionPitchWave", false).setPage("MotionRotation");
   private final Setting<Double> motionPitchWaveAmount = this.num("MotionPitchWaveAmount", 30.0, 0.0, 90.0).setPage("MotionRotation");
   private final Setting<Integer> motionPitchWavePeriod = this.num("MotionPitchWavePeriod", 20, 2, 200).setPage("MotionRotation");
   private final Setting<Boolean> motionYawJitter = this.bool("MotionYawJitter", false).setPage("MotionRotation");
   private final Setting<Double> motionYawJitterAmount = this.num("MotionYawJitterAmount", 10.0, 0.0, 180.0).setPage("MotionRotation");
   private final Setting<Boolean> motionPitchJitter = this.bool("MotionPitchJitter", false).setPage("MotionRotation");
   private final Setting<Double> motionPitchJitterAmount = this.num("MotionPitchJitterAmount", 5.0, 0.0, 90.0).setPage("MotionRotation");
   private final Setting<TestDoohickeyModule.MotionJitterMode> motionJitterMode = this.mode("MotionJitterMode", TestDoohickeyModule.MotionJitterMode.ALTERNATE)
      .setPage("MotionRotation");
   private long nextSendNanos;
   private boolean sendStartNext;
   private boolean recastArmed;
   private boolean spoofGroundNext;
   private boolean spoofCollisionNext;
   private boolean jitterPositiveNext;
   private boolean pitchUpNext;
   private float spoofYaw;
   private boolean startSprintNext;
   private boolean startSneakNext;
   private boolean startGlideNext;
   private boolean noGravityNext;
   private boolean strafeRightNext;
   private boolean gravityChanged;
   private boolean originalNoGravity;
   private boolean motionRotating;
   private int motionTicks;
   private float motionSpinYaw;

   public TestDoohickeyModule() {
      super("TestDoohickey", "Spams experimental flight states, commands, and motion.", Module.Category.MOVEMENT);
      this.async.setVisibility(value -> this.startFly.getValue() && this.stopFly.getValue());
      this.takeoffMode.setVisibility(value -> this.takeoffSpam.getValue());
      this.groundMode.setVisibility(value -> this.groundSpoof.getValue());
      this.collisionMode.setVisibility(value -> this.collisionSpoof.getValue());
      this.horizontalJitter.setVisibility(value -> this.positionJitter.getValue());
      this.verticalJitter.setVisibility(value -> this.positionJitter.getValue());
      this.pitchMode.setVisibility(value -> this.pitchFlip.getValue());
      this.yawStep.setVisibility(value -> this.yawSpin.getValue());
      this.horizontalImpulse.setVisibility(value -> this.velocityPulse.getValue());
      this.verticalImpulse.setVisibility(value -> this.velocityPulse.getValue());
      this.strafeImpulse.setVisibility(value -> this.strafePulse.getValue());
      this.motionMultiplier.setVisibility(value -> this.motionScale.getValue());
      this.motionOnlyGliding.setVisibility(value -> this.motionRotations.getValue());
      this.motionYawSource.setVisibility(value -> this.motionRotations.getValue());
      this.motionPitchSource.setVisibility(value -> this.motionRotations.getValue());
      this.motionFixedPitch
         .setVisibility(value -> this.motionRotations.getValue() && this.motionPitchSource.getValue() == TestDoohickeyModule.MotionPitchSource.FIXED);
      this.motionManagerJitter.setVisibility(value -> this.motionRotations.getValue());
      this.motionReverseYaw.setVisibility(value -> this.motionRotations.getValue());
      this.motionInvertPitch.setVisibility(value -> this.motionRotations.getValue());
      this.motionYawOffset.setVisibility(value -> this.motionRotations.getValue());
      this.motionYawOffsetAmount.setVisibility(value -> this.motionRotations.getValue() && this.motionYawOffset.getValue());
      this.motionPitchOffset.setVisibility(value -> this.motionRotations.getValue());
      this.motionPitchOffsetAmount.setVisibility(value -> this.motionRotations.getValue() && this.motionPitchOffset.getValue());
      this.motionYawSpin.setVisibility(value -> this.motionRotations.getValue());
      this.motionYawSpinStep.setVisibility(value -> this.motionRotations.getValue() && this.motionYawSpin.getValue());
      this.motionYawFlip.setVisibility(value -> this.motionRotations.getValue());
      this.motionYawFlipAngle.setVisibility(value -> this.motionRotations.getValue() && this.motionYawFlip.getValue());
      this.motionPitchFlip.setVisibility(value -> this.motionRotations.getValue());
      this.motionPitchFlipA.setVisibility(value -> this.motionRotations.getValue() && this.motionPitchFlip.getValue());
      this.motionPitchFlipB.setVisibility(value -> this.motionRotations.getValue() && this.motionPitchFlip.getValue());
      this.motionYawWave.setVisibility(value -> this.motionRotations.getValue());
      this.motionYawWaveAmount.setVisibility(value -> this.motionRotations.getValue() && this.motionYawWave.getValue());
      this.motionYawWavePeriod.setVisibility(value -> this.motionRotations.getValue() && this.motionYawWave.getValue());
      this.motionPitchWave.setVisibility(value -> this.motionRotations.getValue());
      this.motionPitchWaveAmount.setVisibility(value -> this.motionRotations.getValue() && this.motionPitchWave.getValue());
      this.motionPitchWavePeriod.setVisibility(value -> this.motionRotations.getValue() && this.motionPitchWave.getValue());
      this.motionYawJitter.setVisibility(value -> this.motionRotations.getValue());
      this.motionYawJitterAmount.setVisibility(value -> this.motionRotations.getValue() && this.motionYawJitter.getValue());
      this.motionPitchJitter.setVisibility(value -> this.motionRotations.getValue());
      this.motionPitchJitterAmount.setVisibility(value -> this.motionRotations.getValue() && this.motionPitchJitter.getValue());
      this.motionJitterMode.setVisibility(value -> this.motionRotations.getValue() && (this.motionYawJitter.getValue() || this.motionPitchJitter.getValue()));
   }

   @Override
   public void onEnable() {
      this.nextSendNanos = System.nanoTime();
      this.sendStartNext = true;
      this.recastArmed = mc.player != null && mc.player.isFallFlying();
      this.resetPulseStates();
      this.resetMotionRotationState();
   }

   @Override
   public void onDisable() {
      this.recastArmed = false;
      this.restoreGravity();
      this.stopMotionRotation();
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()
         && KetamineClub.rotationManager != null
         && this.motionRotations.getValue()
         && (!this.motionOnlyGliding.getValue() || mc.player.isFallFlying())) {
         float yaw = this.getMotionBaseYaw();
         float pitch = this.getMotionBasePitch();
         if (this.motionReverseYaw.getValue()) {
            yaw += 180.0F;
         }

         if (this.motionInvertPitch.getValue()) {
            pitch = -pitch;
         }

         if (this.motionYawOffset.getValue()) {
            yaw += this.motionYawOffsetAmount.getValue().floatValue();
         }

         if (this.motionPitchOffset.getValue()) {
            pitch += this.motionPitchOffsetAmount.getValue().floatValue();
         }

         if (this.motionYawSpin.getValue()) {
            this.motionSpinYaw = Mth.wrapDegrees(this.motionSpinYaw + this.motionYawSpinStep.getValue().floatValue());
            yaw += this.motionSpinYaw;
         } else {
            this.motionSpinYaw = 0.0F;
         }

         boolean alternate = (this.motionTicks & 1) == 0;
         if (this.motionYawFlip.getValue() && !alternate) {
            yaw += this.motionYawFlipAngle.getValue().floatValue();
         }

         if (this.motionPitchFlip.getValue()) {
            pitch = alternate ? this.motionPitchFlipA.getValue().floatValue() : this.motionPitchFlipB.getValue().floatValue();
         }

         if (this.motionYawWave.getValue()) {
            yaw += this.wave(this.motionYawWaveAmount.getValue(), this.motionYawWavePeriod.getValue(), 0.0);
         }

         if (this.motionPitchWave.getValue()) {
            pitch += this.wave(this.motionPitchWaveAmount.getValue(), this.motionPitchWavePeriod.getValue(), Math.PI / 2);
         }

         if (this.motionYawJitter.getValue()) {
            yaw += this.jitter(this.motionYawJitterAmount.getValue(), 0.0);
         }

         if (this.motionPitchJitter.getValue()) {
            pitch += this.jitter(this.motionPitchJitterAmount.getValue(), Math.PI / 2);
         }

         yaw = Mth.wrapDegrees(yaw);
         pitch = Mth.clamp(pitch, -90.0F, 90.0F);
         KetamineClub.rotationManager
            .submit(new RotationRequest("TestDoohickeyMotion", 60, yaw, pitch, RotationRequest.Mode.MOTION, true, !this.motionManagerJitter.getValue()));
         this.motionRotating = true;
         this.motionTicks++;
      } else {
         this.stopMotionRotation();
      }
   }

   @Override
   public void onTick() {
      long now = System.nanoTime();
      if (nullCheck()) {
         this.nextSendNanos = now;
         this.sendStartNext = true;
         this.recastArmed = false;
         this.gravityChanged = false;
         this.resetPulseStates();
      } else {
         if (!this.gravityFlip.getValue()) {
            this.restoreGravity();
         }

         if (this.recast.getValue()) {
            if (mc.player.isFallFlying()) {
               this.recastArmed = true;
            }

            if (this.recastArmed) {
               mc.player.startFallFlying();
            }
         } else {
            this.recastArmed = false;
         }

         if (!this.hasEnabledExperiment()) {
            this.nextSendNanos = now;
            this.sendStartNext = true;
         } else {
            long intervalNanos = 1000000000L / this.rate.getValue().intValue();

            for (int sends = 0; now >= this.nextSendNanos && sends++ < 4; this.nextSendNanos += intervalNanos) {
               this.runPulse();
            }

            if (now - this.nextSendNanos > 1000000000L) {
               this.nextSendNanos = now + intervalNanos;
            }
         }
      }
   }

   private boolean hasEnabledExperiment() {
      return this.startFly.getValue()
         || this.stopFly.getValue()
         || this.takeoffSpam.getValue()
         || this.recastArmed
         || this.groundSpoof.getValue()
         || this.collisionSpoof.getValue()
         || this.positionJitter.getValue()
         || this.pitchFlip.getValue()
         || this.yawSpin.getValue()
         || this.sprintPulse.getValue()
         || this.sneakPulse.getValue()
         || this.glideFlip.getValue()
         || this.gravityFlip.getValue()
         || this.velocityPulse.getValue()
         || this.strafePulse.getValue()
         || this.motionScale.getValue();
   }

   private void runPulse() {
      this.sendConfiguredStates();
      boolean sentTakeoff = this.spamTakeoff();
      if (this.recastArmed && !sentTakeoff) {
         this.sendStartFallFlying();
      }

      this.jitterPosition();
      this.spoofGroundState();
      this.spoofCollisionState();
      this.spoofPitch();
      this.spinYaw();
      this.pulseSprint();
      this.pulseSneak();
      this.flipGlideState();
      this.flipGravity();
      this.scaleMotion();
      this.pulseVelocity();
      this.pulseStrafe();
   }

   private void resetPulseStates() {
      this.spoofGroundNext = true;
      this.spoofCollisionNext = true;
      this.jitterPositiveNext = true;
      this.pitchUpNext = true;
      this.spoofYaw = mc.player == null ? 0.0F : mc.player.getYRot();
      this.startSprintNext = true;
      this.startSneakNext = true;
      this.startGlideNext = true;
      this.noGravityNext = true;
      this.strafeRightNext = true;
   }

   private void sendConfiguredStates() {
      if (this.async.getValue() && this.startFly.getValue() && this.stopFly.getValue()) {
         this.sendFlying(this.sendStartNext);
         this.sendStartNext = !this.sendStartNext;
      } else {
         if (this.startFly.getValue()) {
            this.sendFlying(true);
         }

         if (this.stopFly.getValue()) {
            this.sendFlying(false);
         }
      }
   }

   private void sendFlying(boolean flying) {
      Abilities abilities = new Abilities();
      abilities.flying = flying;
      mc.getConnection().send(new ServerboundPlayerAbilitiesPacket(abilities));
   }

   private boolean spamTakeoff() {
      if (!this.takeoffSpam.getValue()) {
         return false;
      } else if (mc.player.onGround()) {
         if (this.takeoffMode.getValue() == TestDoohickeyModule.TakeoffMode.VELOCITY) {
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x, 0.42, velocity.z);
            mc.player.setSprinting(true);
         } else {
            mc.player.jumpFromGround();
         }

         return false;
      } else {
         this.sendStartFallFlying();
         return true;
      }
   }

   private void sendStartFallFlying() {
      mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.START_FALL_FLYING));
      mc.player.startFallFlying();
   }

   private void spoofGroundState() {
      if (this.groundSpoof.getValue()) {
         boolean onGround = switch ((TestDoohickeyModule.GroundMode)this.groundMode.getValue()) {
            case ALTERNATE -> this.spoofGroundNext;
            case AIR -> false;
            case GROUND -> true;
         };
         mc.getConnection().send(new StatusOnly(onGround, mc.player.horizontalCollision));
         if (this.groundMode.getValue() == TestDoohickeyModule.GroundMode.ALTERNATE) {
            this.spoofGroundNext = !this.spoofGroundNext;
         }
      }
   }

   private void spoofCollisionState() {
      if (this.collisionSpoof.getValue()) {
         boolean horizontalCollision = switch ((TestDoohickeyModule.CollisionMode)this.collisionMode.getValue()) {
            case ALTERNATE -> this.spoofCollisionNext;
            case CLEAR -> false;
            case SET -> true;
         };
         mc.getConnection().send(new StatusOnly(mc.player.onGround(), horizontalCollision));
         if (this.collisionMode.getValue() == TestDoohickeyModule.CollisionMode.ALTERNATE) {
            this.spoofCollisionNext = !this.spoofCollisionNext;
         }
      }
   }

   private void jitterPosition() {
      if (this.positionJitter.getValue()) {
         double direction = this.jitterPositiveNext ? 1.0 : -1.0;
         mc.getConnection()
            .send(
               new Pos(
                  mc.player.getX() + this.horizontalJitter.getValue() * direction,
                  mc.player.getY() + this.verticalJitter.getValue() * direction,
                  mc.player.getZ() - this.horizontalJitter.getValue() * direction,
                  mc.player.onGround(),
                  mc.player.horizontalCollision
               )
            );
         this.jitterPositiveNext = !this.jitterPositiveNext;
      }
   }

   private void spoofPitch() {
      if (this.pitchFlip.getValue()) {
         float pitch = switch ((TestDoohickeyModule.PitchMode)this.pitchMode.getValue()) {
            case ALTERNATE -> this.pitchUpNext ? -90.0F : 90.0F;
            case UP -> -90.0F;
            case DOWN -> 90.0F;
         };
         mc.getConnection().send(new Rot(mc.player.getYRot(), pitch, mc.player.onGround(), mc.player.horizontalCollision));
         if (this.pitchMode.getValue() == TestDoohickeyModule.PitchMode.ALTERNATE) {
            this.pitchUpNext = !this.pitchUpNext;
         }
      }
   }

   private void spinYaw() {
      if (this.yawSpin.getValue()) {
         this.spoofYaw = this.spoofYaw + this.yawStep.getValue().floatValue();
         mc.getConnection().send(new Rot(this.spoofYaw, mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
      }
   }

   private void pulseSprint() {
      if (this.sprintPulse.getValue()) {
         Action action = this.startSprintNext ? Action.START_SPRINTING : Action.STOP_SPRINTING;
         mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, action));
         this.startSprintNext = !this.startSprintNext;
      }
   }

   private void pulseSneak() {
      if (this.sneakPulse.getValue()) {
         Input input = mc.player.input.keyPresses;
         mc.getConnection()
            .send(
               new ServerboundPlayerInputPacket(
                  new Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), this.startSneakNext, input.sprint())
               )
            );
         this.startSneakNext = !this.startSneakNext;
      }
   }

   private void flipGlideState() {
      if (this.glideFlip.getValue()) {
         if (this.startGlideNext) {
            this.sendStartFallFlying();
         } else {
            mc.player.stopFallFlying();
         }

         this.startGlideNext = !this.startGlideNext;
      }
   }

   private void flipGravity() {
      if (this.gravityFlip.getValue()) {
         if (!this.gravityChanged) {
            this.originalNoGravity = mc.player.isNoGravity();
            this.gravityChanged = true;
         }

         mc.player.setNoGravity(this.noGravityNext);
         this.noGravityNext = !this.noGravityNext;
      }
   }

   private void restoreGravity() {
      if (this.gravityChanged && mc.player != null) {
         mc.player.setNoGravity(this.originalNoGravity);
         this.gravityChanged = false;
      }
   }

   private void scaleMotion() {
      if (this.motionScale.getValue()) {
         mc.player.setDeltaMovement(mc.player.getDeltaMovement().scale(this.motionMultiplier.getValue()));
      }
   }

   private void pulseVelocity() {
      if (this.velocityPulse.getValue()) {
         double yaw = Math.toRadians(mc.player.getYRot());
         double horizontal = this.horizontalImpulse.getValue();
         Vec3 velocity = mc.player.getDeltaMovement();
         mc.player
            .setDeltaMovement(velocity.x - Math.sin(yaw) * horizontal, velocity.y + this.verticalImpulse.getValue(), velocity.z + Math.cos(yaw) * horizontal);
      }
   }

   private void pulseStrafe() {
      if (this.strafePulse.getValue()) {
         double yaw = Math.toRadians(mc.player.getYRot());
         double impulse = this.strafeImpulse.getValue() * (this.strafeRightNext ? 1.0 : -1.0);
         Vec3 velocity = mc.player.getDeltaMovement();
         mc.player.setDeltaMovement(velocity.x + Math.cos(yaw) * impulse, velocity.y, velocity.z + Math.sin(yaw) * impulse);
         this.strafeRightNext = !this.strafeRightNext;
      }
   }

   private float getMotionBaseYaw() {
      return switch ((TestDoohickeyModule.MotionYawSource)this.motionYawSource.getValue()) {
         case LOOK -> mc.player.getYRot();
         case INPUT -> this.getInputHeading();
         case VELOCITY -> this.getVelocityHeading();
      };
   }

   private float getMotionBasePitch() {
      return switch ((TestDoohickeyModule.MotionPitchSource)this.motionPitchSource.getValue()) {
         case LOOK -> mc.player.getXRot();
         case FIXED -> this.motionFixedPitch.getValue().floatValue();
         case VELOCITY -> this.getVelocityPitch();
      };
   }

   private float getInputHeading() {
      float strafe = (mc.options.keyRight.isDown() ? 1.0F : 0.0F) - (mc.options.keyLeft.isDown() ? 1.0F : 0.0F);
      float forward = (mc.options.keyUp.isDown() ? 1.0F : 0.0F) - (mc.options.keyDown.isDown() ? 1.0F : 0.0F);
      if (strafe == 0.0F && forward == 0.0F) {
         return mc.player.getYRot();
      } else {
         double yaw = Math.toRadians(mc.player.getYRot());
         double forwardX = -Math.sin(yaw);
         double forwardZ = Math.cos(yaw);
         double rightX = -Math.sin(yaw + (Math.PI / 2));
         double rightZ = Math.cos(yaw + (Math.PI / 2));
         double motionX = forwardX * forward + rightX * strafe;
         double motionZ = forwardZ * forward + rightZ * strafe;
         return (float)Math.toDegrees(Math.atan2(motionZ, motionX)) - 90.0F;
      }
   }

   private float getVelocityHeading() {
      Vec3 velocity = mc.player.getDeltaMovement();
      return velocity.horizontalDistanceSqr() < 1.0E-6 ? mc.player.getYRot() : (float)Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - 90.0F;
   }

   private float getVelocityPitch() {
      Vec3 velocity = mc.player.getDeltaMovement();
      double horizontal = Math.sqrt(velocity.horizontalDistanceSqr());
      return horizontal < 0.001 && Math.abs(velocity.y) < 0.001 ? mc.player.getXRot() : (float)Math.toDegrees(Math.atan2(-velocity.y, horizontal));
   }

   private float wave(double amount, int period, double phase) {
      double angle = (Math.PI * 2) * this.motionTicks / period + phase;
      return (float)(Math.sin(angle) * amount);
   }

   private float jitter(double amount, double phase) {
      double direction = switch ((TestDoohickeyModule.MotionJitterMode)this.motionJitterMode.getValue()) {
         case ALTERNATE -> (this.motionTicks & 1) == 0 ? 1.0 : -1.0;
         case RANDOM -> ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
         case SINE -> Math.sin(this.motionTicks * Math.PI / 2.0 + phase);
      };
      return (float)(direction * amount);
   }

   private void stopMotionRotation() {
      if (KetamineClub.rotationManager != null) {
         KetamineClub.rotationManager.cancel("TestDoohickeyMotion");
      }

      this.resetMotionRotationState();
   }

   private void resetMotionRotationState() {
      this.motionRotating = false;
      this.motionTicks = 0;
      this.motionSpinYaw = 0.0F;
   }

   public static enum CollisionMode {
      ALTERNATE,
      CLEAR,
      SET;

      private CollisionMode() {
      }
   }

   public static enum GroundMode {
      ALTERNATE,
      AIR,
      GROUND;

      private GroundMode() {
      }
   }

   public static enum MotionJitterMode {
      ALTERNATE,
      RANDOM,
      SINE;

      private MotionJitterMode() {
      }
   }

   public static enum MotionPitchSource {
      LOOK,
      FIXED,
      VELOCITY;

      private MotionPitchSource() {
      }
   }

   public static enum MotionYawSource {
      LOOK,
      INPUT,
      VELOCITY;

      private MotionYawSource() {
      }
   }

   public static enum PitchMode {
      ALTERNATE,
      UP,
      DOWN;

      private PitchMode() {
      }
   }

   public static enum TakeoffMode {
      VELOCITY,
      VANILLA;

      private TakeoffMode() {
      }
   }
}
