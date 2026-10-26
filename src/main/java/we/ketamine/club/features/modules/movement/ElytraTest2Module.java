package we.ketamine.club.features.modules.movement;

import io.netty.channel.Channel;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.mixin.network.ConnectionAccessor;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class ElytraTest2Module extends Module {
   public static final String ID = "ElytraTest2";
   private static final int ROTATION_PRIORITY = 60;
   private static final int ROCKET_PRIORITY = 15;
   private static final int CHEST_MENU_SLOT = 6;
   private static final double GRIM_RESCALE = 1.65;
   private final Setting<ElytraTest2Module.FlyMode> flyMode = this.mode("FlyMode", ElytraTest2Module.FlyMode.BOUNCE);
   private final Setting<ElytraTest2Module.RocketBoostMode> rocketBoostMode = this.mode("RocketBoostMode", ElytraTest2Module.RocketBoostMode.GRIM);
   private final Setting<Double> boostSpeed = this.num("Boost", 0.0, 0.0, 0.5);
   private final Setting<Double> rocketSpeed = this.num("RocketSpeed", 1.0, 0.0, 2.0);
   private final Setting<Double> maxGrimBoost = this.num("MaxGrimBoost", 3.0, 0.0, 3.0);
   private final Setting<Double> safetyMargin = this.num("SafetyMargin", 0.2, 0.0, 2.0);
   private final Setting<Boolean> fakeFly = this.bool("FakeFly", false);
   private final Setting<Boolean> takeoff = this.bool("Takeoff", true).setPage("Bounce");
   private final Setting<Boolean> autoPitch = this.bool("AutoPitch", true).setPage("Bounce");
   private final Setting<Double> bouncePitch = this.num("Pitch", 72.0, -90.0, 90.0).setPage("Bounce");
   private final Setting<Boolean> jump = this.bool("Jump", true).setPage("Bounce");
   private final Setting<Integer> flagPause = this.num("FlagPause", 5, 0, 100).setPage("Bounce");
   private final Setting<Boolean> minimizePackets = this.bool("MinimizePackets", true).setPage("Bounce");
   private final Setting<Boolean> fakeLag = this.bool("FakeLag", false).setPage("Bounce");
   private final Setting<Boolean> yMotion = this.bool("YMotion", false).setPage("Bounce");
   private final Setting<Boolean> onlyOnDiagonal = this.bool("OnlyOnDiagonal", true).setPage("Bounce");
   private final Setting<Double> minDiagonalAngle = this.num("MinDiagonalAngle", 15.0, 0.0, 90.0).setPage("Bounce");
   private final Setting<Double> yMotionStartSpeed = this.num("YMotionStartSpeed", 30.0, 0.0, 40.0).setPage("Bounce");
   private final Setting<Double> yMotionSpeedLimit = this.num("YMotionSpeedLimit", 120.0, 10.0, 400.0).setPage("Bounce");
   private final Setting<Boolean> passObstacles = this.bool("PassObstacles", false).setPage("ObstaclePasser");
   private final Setting<Boolean> walkWhenFlagged = this.bool("WalkWhenFlagged", true).setPage("ObstaclePasser");
   private final Setting<Double> obstacleLookAhead = this.num("ObstacleLookAhead", 8.0, 0.0, 50.0).setPage("ObstaclePasser");
   private final Setting<Double> upAngle = this.num("UpAngle", 45.0, 0.0, 90.0).setPage("GrimControl");
   private final Setting<Double> downAngle = this.num("DownAngle", 20.0, 0.0, 90.0).setPage("GrimControl");
   private final Setting<ElytraTest2Module.FlipFlopMode> flipFlopMode = this.mode("FlipFlopMode", ElytraTest2Module.FlipFlopMode.NONE).setPage("GrimControl");
   private final Setting<Integer> packetGap = this.num("PacketGap", 20, 0, 100).setPage("GrimControl");
   private final Setting<Boolean> inventoryRockets = this.bool("InventoryRockets", true).setPage("GrimControl");
   private boolean steering;
   private boolean hovering;
   private boolean still;
   private boolean moving;
   private boolean flip;
   private int stillTicks;
   private int rocketRemaining = -1;
   private int flagPauseTicks;
   private boolean lastMovementIncludedPosition = true;
   private Vec3 previousSentPosition;
   private Vec3 previousVelocity = Vec3.ZERO;
   private Vec3 targetVelocity;
   private long fireworkEndsNanos;
   private String state;
   private final Queue<Packet<?>> fakeLagQueue = new ArrayDeque<>();

   public ElytraTest2Module() {
      super("ElytraTest2", "Lambda-style elytra flight with General, Bounce, and Grim Control modes.", Module.Category.MOVEMENT);
      this.rocketSpeed.setVisibility(v -> this.rocketBoostMode.getValue() == ElytraTest2Module.RocketBoostMode.STANDARD);
      this.maxGrimBoost.setVisibility(v -> this.rocketBoostMode.getValue() == ElytraTest2Module.RocketBoostMode.GRIM);
      this.bouncePitch.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE && this.autoPitch.getValue());
      this.jump.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE);
      this.takeoff.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE);
      this.flagPause.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE);
      this.minimizePackets.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE);
      this.fakeLag.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE);
      this.yMotion.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE);
      this.onlyOnDiagonal.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE && this.yMotion.getValue());
      this.minDiagonalAngle
         .setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE && this.yMotion.getValue() && this.onlyOnDiagonal.getValue());
      this.yMotionStartSpeed.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE && this.yMotion.getValue());
      this.yMotionSpeedLimit.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE && this.yMotion.getValue());
      this.passObstacles.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE);
      this.walkWhenFlagged.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE && this.passObstacles.getValue());
      this.obstacleLookAhead.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE && this.passObstacles.getValue());
      this.upAngle.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.GRIM_CONTROL);
      this.downAngle.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.GRIM_CONTROL);
      this.flipFlopMode.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.GRIM_CONTROL);
      this.packetGap
         .setVisibility(
            v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.GRIM_CONTROL && this.flipFlopMode.getValue() != ElytraTest2Module.FlipFlopMode.NONE
         );
      this.inventoryRockets.setVisibility(v -> this.flyMode.getValue() == ElytraTest2Module.FlyMode.GRIM_CONTROL);
   }

   @Override
   public void onEnable() {
      this.disableConflict(ElytraFlyModule.class);
      this.disableConflict(FakeFlyModule.class);
      this.disableConflict(ElytraTestModule.class);
      this.resetState();
   }

   @Override
   public void onDisable() {
      this.stopSteering();
      this.resetState();
   }

   private void resetState() {
      this.steering = false;
      this.hovering = false;
      this.still = false;
      this.moving = false;
      this.flip = false;
      this.stillTicks = 0;
      this.rocketRemaining = -1;
      this.flagPauseTicks = 0;
      this.previousSentPosition = null;
      this.previousVelocity = Vec3.ZERO;
      this.targetVelocity = null;
      this.fireworkEndsNanos = 0L;
      this.state = null;
      this.flushFakeLag();
      if (KetamineClub.rotationManager != null) {
         KetamineClub.rotationManager.cancel("ElytraTest2");
      }
   }

   @Subscribe(
      priority = 2
   )
   private void onPreTick(PreTickEvent event) {
      this.state = null;
      this.targetVelocity = null;
      if (nullCheck()) {
         this.resetState();
      } else {
         if (!this.shouldQueueFakeLag()) {
            this.flushFakeLag();
         }

         this.updateFireworkState();
         if (!mc.player.isFallFlying()) {
            this.hovering = false;
            this.moving = false;
            if (this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE && this.takeoff.getValue()) {
               this.tryTakeoff();
            }

            if (this.fakeFly.getValue()) {
               this.pulseFakeFlight();
            }
         } else if (this.flagPauseTicks > 0) {
            this.flagPauseTicks--;
            this.stopSteering();
            this.state = "Flag Pause";
         } else {
            switch ((ElytraTest2Module.FlyMode)this.flyMode.getValue()) {
               case GENERAL:
                  this.tickGeneral();
                  break;
               case BOUNCE:
                  this.tickBounce();
                  break;
               case GRIM_CONTROL:
                  this.tickGrimControl();
            }

            if (this.rocketBoostMode.getValue() == ElytraTest2Module.RocketBoostMode.GRIM && this.hasFirework() && !this.pausingMovement()) {
               this.applyGrimFireworkPrediction();
            }

            if (this.boostSpeed.getValue() > 0.0) {
               this.applyBoost(this.boostSpeed.getValue());
            }

            if (this.autoRocketEnabled() && this.shouldFireRocket()) {
               this.fireRocket();
            }
         }
      }
   }

   private void tickGeneral() {
      this.hovering = false;
      this.moving = false;
      this.stopSteering();
      this.state = "Flying";
   }

   private void tickBounce() {
      if (!this.minimizePackets.getValue() || !mc.player.getAbilities().flying) {
         if (this.passObstacles.getValue() && this.handleObstaclePassing()) {
            this.state = "Obstacle";
         } else {
            if (this.autoPitch.getValue()) {
               this.steerTo(mc.player.getYRot(), this.bouncePitch.getValue().floatValue());
            }

            this.updateInputSteering(this.bouncePitch.getValue().floatValue(), true);
            if (this.yMotion.getValue() && this.shouldUseYMotion()) {
               Vec3 velocity = mc.player.getDeltaMovement();
               mc.player.setDeltaMovement(velocity.x, 0.0, velocity.z);
            }

            this.state = "Bounce";
         }
      }
   }

   private void tickGrimControl() {
      Vec3 input = this.inputVector();
      boolean previousStill = this.still;
      this.still = input.lengthSqr() < 1.0E-4;
      this.hovering = this.still;
      this.moving = !this.still;
      if (this.still) {
         this.stillTicks++;
         if (this.flipFlopMode.getValue() != ElytraTest2Module.FlipFlopMode.NONE && this.flipFlopMode.getValue().allows(this.hasFirework())) {
            this.steerTo(this.flip ? 180.0F : 0.0F, 0.0F);
            this.flip = !this.flip;
         } else {
            this.stopSteering();
         }

         this.state = "Still";
      } else {
         if (!this.hasFirework() && this.inventoryRockets.getValue()) {
            this.fireRocket(true);
         }

         float[] angles = this.yawPitch(input);
         if (mc.options.keyJump.isDown() && !mc.options.keyShift.isDown()) {
            angles[1] = (float)(-this.upAngle.getValue());
         } else if (mc.options.keyShift.isDown() && !mc.options.keyJump.isDown()) {
            angles[1] = (float)this.downAngle.getValue().doubleValue();
         }

         this.steerTo(angles[0], angles[1]);
         if (previousStill) {
            mc.player.setDeltaMovement(this.getRocketTargetVelocity(angles[1], angles[0]));
         }

         this.state = "Control";
      }
   }

   private void updateInputSteering(float requestedPitch, boolean allowVertical) {
      Vec3 input = this.inputVector();
      boolean up = mc.options.keyJump.isDown();
      boolean down = mc.options.keyShift.isDown();
      if (input.horizontalDistanceSqr() < 1.0E-5 && !up && !down) {
         this.hovering = false;
         this.moving = false;
         if (!this.autoPitch.getValue()) {
            this.stopSteering();
         }
      } else {
         float[] angles = this.yawPitch(input);
         float pitch = requestedPitch;
         if (allowVertical && up != down) {
            pitch = up ? -45.0F : 45.0F;
         }

         this.steerTo(angles[0], pitch);
         this.moving = true;
         this.hovering = false;
      }
   }

   private Vec3 inputVector() {
      float yaw = mc.player.getYRot();
      double rad = Math.toRadians(yaw);
      Vec3 forward = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
      Vec3 right = new Vec3(-Math.sin(rad + (Math.PI / 2)), 0.0, Math.cos(rad + (Math.PI / 2)));
      Vec3 result = Vec3.ZERO;
      if (mc.options.keyUp.isDown()) {
         result = result.add(forward);
      }

      if (mc.options.keyDown.isDown()) {
         result = result.subtract(forward);
      }

      if (mc.options.keyRight.isDown()) {
         result = result.add(right);
      }

      if (mc.options.keyLeft.isDown()) {
         result = result.subtract(right);
      }

      boolean horizontal = result.horizontalDistanceSqr() > 1.0E-5;
      if (mc.options.keyJump.isDown()) {
         result = horizontal ? result.add(0.0, 1.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
      }

      if (mc.options.keyShift.isDown()) {
         result = horizontal ? result.add(0.0, -1.0, 0.0) : new Vec3(0.0, -1.0, 0.0);
      }

      return result;
   }

   private float[] yawPitch(Vec3 vector) {
      Vec3 normalized = vector.normalize();
      float yaw = (float)Math.toDegrees(Math.atan2(-normalized.x, normalized.z));
      float pitch = (float)Math.toDegrees(Math.asin(Mth.clamp(-normalized.y, -1.0, 1.0)));
      return new float[]{yaw, pitch};
   }

   private void steerTo(float yaw, float pitch) {
      if (KetamineClub.rotationManager != null) {
         KetamineClub.rotationManager
            .submit(new RotationRequest("ElytraTest2", 60, yaw, Mth.clamp(pitch, -90.0F, 90.0F), RotationRequest.Mode.MOTION, true, true));
         this.steering = true;
      }
   }

   private void stopSteering() {
      if (KetamineClub.rotationManager != null) {
         KetamineClub.rotationManager.cancel("ElytraTest2");
      }

      this.steering = false;
   }

   private void tryTakeoff() {
      if ((this.jump.getValue() || mc.options.keyJump.isDown()) && !mc.player.onGround() && !mc.player.isInWater() && !mc.player.isInLava()) {
         if (mc.player.tryToStartFallFlying()) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.START_FALL_FLYING));
            this.state = "Takeoff";
         }
      }
   }

   private void pulseFakeFlight() {
      if (this.canClickPlayerInventory()) {
         if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            Result elytra = InventoryUtil.find(Items.ELYTRA, InventoryUtil.HOTBAR_SCOPE);
            if (elytra.found()) {
               InventoryUtil.click(6, elytra.slot(), ContainerInput.SWAP);

               try {
                  mc.player.startFallFlying();
                  mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, Action.START_FALL_FLYING));
               } finally {
                  InventoryUtil.click(6, elytra.slot(), ContainerInput.SWAP);
               }
            }
         }
      }
   }

   private boolean canClickPlayerInventory() {
      return mc.gameMode != null && mc.getConnection() != null && mc.player.containerMenu == mc.player.inventoryMenu && InventoryUtil.cursor().isEmpty();
   }

   private boolean shouldUseYMotion() {
      double speed = mc.player.getDeltaMovement().horizontalDistance() * 20.0;
      if (speed < this.yMotionStartSpeed.getValue() || speed > this.yMotionSpeedLimit.getValue()) {
         return false;
      } else if (!this.onlyOnDiagonal.getValue()) {
         return true;
      } else {
         double angle = Math.abs(Mth.wrapDegrees(mc.player.getYRot()) % 90.0);
         return angle > this.minDiagonalAngle.getValue() && angle < 90.0 - this.minDiagonalAngle.getValue();
      }
   }

   private boolean pausingMovement() {
      return this.flyMode.getValue() == ElytraTest2Module.FlyMode.GRIM_CONTROL && this.still;
   }

   private boolean autoRocketEnabled() {
      return this.flyMode.getValue() != ElytraTest2Module.FlyMode.GENERAL || this.rocketBoostMode.getValue() != ElytraTest2Module.RocketBoostMode.STANDARD;
   }

   private boolean shouldFireRocket() {
      return !this.pausingMovement() && !this.hovering ? this.rocketRemaining <= 3 || !this.hasFirework() : false;
   }

   private void fireRocket() {
      this.fireRocket(true);
   }

   private void fireRocket(boolean includeInventory) {
      Result rocket = InventoryUtil.find(Items.FIREWORK_ROCKET, includeInventory ? InventoryUtil.FULL_SCOPE : InventoryUtil.HOTBAR_SCOPE);
      if (!rocket.found()) {
         this.state = "No Rocket";
      } else {
         if (KetamineClub.swapManager.submit(new SwapRequest("ElytraTest2.rocket", 15, rocket, result -> mc.gameMode.useItem(mc.player, result.hand())))) {
            this.rocketRemaining = InventoryUtil.fireworkRefireTicks(rocket.stack());
            this.state = "Rocket";
         }
      }
   }

   private void updateFireworkState() {
      if (this.rocketRemaining > 0) {
         this.rocketRemaining--;
      }

      if (this.fireworkEndsNanos > 0L && System.nanoTime() >= this.fireworkEndsNanos) {
         this.fireworkEndsNanos = 0L;
      }
   }

   private boolean hasFirework() {
      return this.rocketRemaining > 0 || this.fireworkEndsNanos > System.nanoTime();
   }

   private void applyBoost(double acceleration) {
      float yaw = KetamineClub.rotationManager != null && KetamineClub.rotationManager.isRotating()
         ? KetamineClub.rotationManager.getRotationYaw()
         : mc.player.getYRot();
      float pitch = KetamineClub.rotationManager != null && KetamineClub.rotationManager.isRotating()
         ? KetamineClub.rotationManager.getRotationPitch()
         : mc.player.getXRot();
      Vec3 look = Vec3.directionFromRotation(pitch, yaw);
      Vec3 velocity = mc.player.getDeltaMovement();
      mc.player.setDeltaMovement(velocity.add(look.scale(acceleration)));
   }

   private Vec3 getRocketTargetVelocity(float pitch, float yaw) {
      Vec3 look = Vec3.directionFromRotation(pitch, yaw);
      double d = 1.5 * this.rocketSpeed.getValue();
      double e = 0.1 * this.rocketSpeed.getValue();
      Vec3 velocity = mc.player.getDeltaMovement();
      return velocity.add(
         look.x * e + (look.x * d - velocity.x) * 0.5, look.y * e + (look.y * d - velocity.y) * 0.5, look.z * e + (look.z * d - velocity.z) * 0.5
      );
   }

   private void applyGrimFireworkPrediction() {
      float yaw = KetamineClub.rotationManager != null && KetamineClub.rotationManager.isRotating()
         ? KetamineClub.rotationManager.getRotationYaw()
         : mc.player.getYRot();
      float pitch = KetamineClub.rotationManager != null && KetamineClub.rotationManager.isRotating()
         ? KetamineClub.rotationManager.getRotationPitch()
         : mc.player.getXRot();
      Vec3 aiming = Vec3.directionFromRotation(pitch, yaw);
      float lastYaw = KetamineClub.rotationManager != null ? KetamineClub.rotationManager.getServerYaw() : mc.player.getYRot();
      float lastPitch = KetamineClub.rotationManager != null ? KetamineClub.rotationManager.getServerPitch() : mc.player.getXRot();
      double[] bounds = ElytraTest2Module.GrimFireworkBox.getFireworkBounds(
         this.previousVelocity, aiming, lastYaw, lastPitch, this.lastMovementIncludedPosition, 1.65, mc.player.getGravity()
      );
      Vec3 claimed = ElytraTest2Module.GrimFireworkBox.farthestPointInBox(bounds, aiming);
      if (claimed != null) {
         double maxSpeed = 1.7 * this.rocketSpeed.getValue() + this.maxGrimBoost.getValue();
         double length = claimed.length();
         this.targetVelocity = length > maxSpeed ? claimed.scale(maxSpeed / length) : claimed;
         mc.player.setDeltaMovement(this.targetVelocity);
      }
   }

   @Subscribe(
      priority = 1
   )
   private void onPacketSend(PacketEvent.Send event) {
      if (!nullCheck()) {
         if (event.getPacket() instanceof ServerboundUseItemPacket packet && mc.player.getItemInHand(packet.getHand()).is(Items.FIREWORK_ROCKET)) {
            ElytraTest2Module.ItemStackFirework.fireworkUsed(this, mc.player.getItemInHand(packet.getHand()));
         }

         if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            if (this.shouldQueueFakeLag()) {
               this.fakeLagQueue.add(event.getPacket());
               event.cancel();
            } else {
               this.flushFakeLag();
               if (packet.hasPosition()) {
                  Vec3 base = this.previousSentPosition == null ? mc.player.position() : this.previousSentPosition;
                  Vec3 next = new Vec3(packet.getX(base.x), packet.getY(base.y), packet.getZ(base.z));
                  this.previousVelocity = next.subtract(base);
                  this.previousSentPosition = next;
               }

               this.lastMovementIncludedPosition = packet.hasPosition();
               if (this.flyMode.getValue() == ElytraTest2Module.FlyMode.GRIM_CONTROL
                  && mc.player.isFallFlying()
                  && this.still
                  && this.flipFlopMode.getValue() != ElytraTest2Module.FlipFlopMode.NONE
                  && this.stillTicks < this.packetGap.getValue()) {
                  event.cancel();
               }
            }
         }
      }
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundPlayerPositionPacket && mc.player.isFallFlying()) {
         this.flagPauseTicks = this.flagPause.getValue();
      }
   }

   private boolean shouldQueueFakeLag() {
      return this.flyMode.getValue() == ElytraTest2Module.FlyMode.BOUNCE
         && this.fakeLag.getValue()
         && mc.player.isFallFlying()
         && (!this.yMotion.getValue() || !this.shouldUseYMotion())
         && mc.player.getY() - mc.player.yOld < 0.163;
   }

   private void flushFakeLag() {
      if (!this.fakeLagQueue.isEmpty() && mc.getConnection() != null) {
         Channel channel = ((ConnectionAccessor)mc.getConnection().getConnection()).getChannel();

         while (!this.fakeLagQueue.isEmpty()) {
            channel.writeAndFlush(this.fakeLagQueue.poll());
         }
      }
   }

   private boolean handleObstaclePassing() {
      Vec3 look = mc.player.getViewVector(1.0F).normalize();
      Vec3 start = mc.player.getEyePosition();

      for (double distance = 1.0; distance <= this.obstacleLookAhead.getValue(); distance++) {
         BlockPos pos = BlockPos.containing(start.add(look.scale(distance)));
         if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
            this.steerTo(mc.player.getYRot(), -45.0F);
            return true;
         }
      }

      return false;
   }

   private void disableConflict(Class<? extends Module> type) {
      if (KetamineClub.moduleManager != null) {
         Module module = KetamineClub.moduleManager.getModuleByClass(type);
         if (module != null && module.isEnabled()) {
            module.disable();
         }
      }
   }

   @Override
   public String getDisplayInfo() {
      if (this.state != null) {
         return this.state;
      } else if (this.hovering) {
         return "Hover";
      } else {
         return this.steering ? "Steering" : null;
      }
   }

   public static enum FlipFlopMode {
      FULL,
      WITH_FIREWORK,
      NONE;

      private FlipFlopMode() {
      }

      private boolean allows(boolean firework) {
         return this == FULL || this == WITH_FIREWORK && firework;
      }
   }

   public static enum FlyMode {
      GENERAL,
      BOUNCE,
      GRIM_CONTROL;

      private FlyMode() {
      }
   }

   static final class GrimFireworkBox {
      GrimFireworkBox() {
      }

      static double[] getFireworkBounds(
         Vec3 oldVelocity, Vec3 rotation, float lastYaw, float lastPitch, boolean lastMovementIncluded, double rescale, double gravity
      ) {
         Vec3 simulated = calcGlidingVelocity(oldVelocity, rotation, gravity);
         Vec3 currentLook = rotation.normalize();
         Vec3 lastLook = Vec3.directionFromRotation(lastPitch, lastYaw).normalize();
         double anti = lastMovementIncluded ? 0.05 : 0.0;
         double minX = Math.min(-anti, currentLook.x) + Math.min(-anti, lastLook.x);
         double minY = Math.min(-anti, currentLook.y) + Math.min(-anti, lastLook.y);
         double minZ = Math.min(-anti, currentLook.z) + Math.min(-anti, lastLook.z);
         double maxX = Math.max(anti, currentLook.x) + Math.max(anti, lastLook.x);
         double maxY = Math.max(anti, currentLook.y) + Math.max(anti, lastLook.y);
         double maxZ = Math.max(anti, currentLook.z) + Math.max(anti, lastLook.z);
         minX = Math.max(-rescale, minX * rescale);
         minY = Math.max(-rescale, minY * rescale);
         minZ = Math.max(-rescale, minZ * rescale);
         maxX = Math.min(rescale, maxX * rescale);
         maxY = Math.min(rescale, maxY * rescale);
         maxZ = Math.min(rescale, maxZ * rescale);
         return new double[]{
            simulated.x + Math.min(0.0, minX - oldVelocity.x),
            simulated.y + Math.min(0.0, minY - oldVelocity.y),
            simulated.z + Math.min(0.0, minZ - oldVelocity.z),
            simulated.x + Math.max(0.0, maxX - oldVelocity.x),
            simulated.y + Math.max(0.0, maxY - oldVelocity.y),
            simulated.z + Math.max(0.0, maxZ - oldVelocity.z)
         };
      }

      static Vec3 calcGlidingVelocity(Vec3 old, Vec3 rotation, double gravity) {
         double horizontalLook = Math.sqrt(rotation.x * rotation.x + rotation.z * rotation.z);
         double horizontalSpeed = Math.sqrt(old.x * old.x + old.z * old.z);
         double pitch = Math.asin(Mth.clamp(-rotation.y, -1.0, 1.0));
         double pitchCosSquared = Math.cos(pitch) * Math.cos(pitch);
         Vec3 velocity = old.add(0.0, gravity * (pitchCosSquared * 0.75 - 1.0), 0.0);
         if (velocity.y < 0.0 && horizontalLook > 0.0) {
            double lift = velocity.y * -0.1 * pitchCosSquared;
            velocity = velocity.add(rotation.x * lift / horizontalLook, lift, rotation.z * lift / horizontalLook);
         }

         if (pitch < 0.0 && horizontalLook > 0.0) {
            double dive = horizontalSpeed * -Math.sin(pitch) * 0.04;
            velocity = velocity.add(-rotation.x * dive / horizontalLook, dive * 3.2, -rotation.z * dive / horizontalLook);
         }

         if (horizontalLook > 0.0) {
            double targetScale = horizontalSpeed / horizontalLook;
            velocity = velocity.add((rotation.x * targetScale - velocity.x) * 0.1, 0.0, (rotation.z * targetScale - velocity.z) * 0.1);
         }

         return velocity.multiply(0.99, 0.98, 0.99);
      }

      private static Vec3 farthestPointInBox(double[] bounds, Vec3 aim) {
         if (bounds == null) {
            return null;
         } else {
            Vec3 center = new Vec3((bounds[0] + bounds[3]) / 2.0, (bounds[1] + bounds[4]) / 2.0, (bounds[2] + bounds[5]) / 2.0);
            Vec3 direction = aim.normalize();
            double near = Double.NEGATIVE_INFINITY;
            double far = Double.POSITIVE_INFINITY;
            double[] origins = new double[]{center.x, center.y, center.z};
            double[] dirs = new double[]{direction.x, direction.y, direction.z};

            for (int axis = 0; axis < 3; axis++) {
               if (Math.abs(dirs[axis]) < 1.0E-12) {
                  if (origins[axis] < bounds[axis] || origins[axis] > bounds[axis + 3]) {
                     return null;
                  }
               } else {
                  double entry = (bounds[axis] - origins[axis]) / dirs[axis];
                  double exit = (bounds[axis + 3] - origins[axis]) / dirs[axis];
                  if (entry > exit) {
                     double swap = entry;
                     entry = exit;
                     exit = swap;
                  }

                  near = Math.max(near, entry);
                  far = Math.min(far, exit);
               }
            }

            return near > far ? null : center.add(direction.scale(far));
         }
      }
   }

   private static final class ItemStackFirework {
      private ItemStackFirework() {
      }

      private static void fireworkUsed(ElytraTest2Module module, ItemStack stack) {
         Fireworks fireworks = (Fireworks)stack.get(DataComponents.FIREWORKS);
         int duration = fireworks == null ? 1 : fireworks.flightDuration();
         module.fireworkEndsNanos = System.nanoTime() + Math.max(0L, (long)((duration * 0.5 + 0.5 - module.safetyMargin.getValue()) * 1.0E9));
      }
   }

   public static enum RocketBoostMode {
      STANDARD,
      GRIM;

      private RocketBoostMode() {
      }
   }
}
