package we.ketamine.club.features.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.world.SpeedMineController;
import we.ketamine.club.features.settings.Setting;

public class VelocityModule extends Module {
   public final Setting<VelocityModule.Mode> mode = this.mode("Mode", VelocityModule.Mode.NORMAL);
   public final Setting<Boolean> phaseOnly = this.bool("PhaseOnly", true);
   public final Setting<Boolean> knockback = this.bool("Knockback", true);
   public final Setting<Boolean> explosions = this.bool("Explosions", false);
   public final Setting<Boolean> entityPush = this.bool("EntityPush", true);
   public final Setting<Boolean> blockPush = this.bool("BlockPush", true);
   private static final Direction[] PUSH_DIRECTIONS = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};
   private static final double GRIM_PUSH_BUDGET = 0.145;
   private Vec3 counterDirection = Vec3.ZERO;
   private int counterTicks;
   private boolean counterFromMining;
   private boolean staticCancelVelocity;

   public VelocityModule() {
      super("Velocity", "Cancels knockback, explosions, and entity/block push.", Module.Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      this.clearCounter();
      this.staticCancelVelocity = false;
   }

   @Override
   public void onDisable() {
      this.clearCounter();
      this.staticCancelVelocity = false;
   }

   public boolean shouldCancelKnockback() {
      if (this.isEnabled() && this.knockback.getValue()) {
         return this.mode.getValue() == VelocityModule.Mode.NORMAL
            ? this.phaseConditionMet()
            : this.mode.getValue() == VelocityModule.Mode.STATIC && this.cancelStaticVelocity();
      } else {
         return false;
      }
   }

   public Vec3 adjustGrimKnockback(Vec3 movement) {
      if (this.isEnabled() && this.knockback.getValue()) {
         boolean miningCounter = this.shouldCounterWhileMining();
         if (this.mode.getValue() != VelocityModule.Mode.GRIM && !miningCounter) {
            return null;
         } else if (!this.phaseConditionMet()) {
            return null;
         } else {
            this.armCounter(movement, miningCounter);
            if (!miningCounter && this.blockPush.getValue()) {
               Vec3 budget = this.suffocationPushBudget();
               double x = reduceTowardZero(movement.x, budget.x);
               double z = reduceTowardZero(movement.z, budget.z);
               return x == movement.x && z == movement.z ? null : new Vec3(x, movement.y, z);
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public void counterExplosion(Vec3 impulse) {
      if (this.isEnabled() && this.explosions.getValue() && this.shouldCounterWhileMining()) {
         if (!(Math.hypot(impulse.x, impulse.z) <= 1.0E-6)) {
            this.armCounter(mc.player.getDeltaMovement().add(impulse), true);
         }
      }
   }

   private void armCounter(Vec3 movement, boolean fromMining) {
      double horizontal = Math.hypot(movement.x, movement.z);
      if (horizontal > 1.0E-6) {
         this.counterDirection = new Vec3(movement.x / horizontal, 0.0, movement.z / horizontal);
         this.counterTicks = 6;
         this.counterFromMining = fromMining;
      } else {
         this.clearCounter();
      }
   }

   public void applyGrimCounterInput(LocalPlayer player) {
      if (this.staticCancelVelocity) {
         this.sendStaticCorrection();
         this.staticCancelVelocity = false;
      }

      boolean miningCounter = this.counterFromMining && this.mode.getValue() == VelocityModule.Mode.STATIC;
      if (this.counterTicks > 0
         && this.isEnabled()
         && this.phaseConditionMet()
         && (this.mode.getValue() == VelocityModule.Mode.GRIM || miningCounter && this.staticConditionMet())) {
         Vec3 movement = player.getDeltaMovement();
         double horizontalSq = movement.x * movement.x + movement.z * movement.z;
         if (!(horizontalSq < 9.0E-6) && !(movement.x * this.counterDirection.x + movement.z * this.counterDirection.z <= 0.0)) {
            float yaw = player.getYRot();
            if (KetamineClub.rotationManager != null && KetamineClub.rotationManager.hasMovementRotation()) {
               yaw = KetamineClub.rotationManager.getMovementYaw();
            }

            double radians = Math.toRadians(yaw);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            int bestSideways = 0;
            int bestForward = 0;
            double bestDot = Double.MAX_VALUE;

            for (int sideways = -1; sideways <= 1; sideways++) {
               for (int forward = -1; forward <= 1; forward++) {
                  if (sideways != 0 || forward != 0) {
                     double length = Math.hypot(sideways, forward);
                     double worldX = (sideways * cos - forward * sin) / length;
                     double worldZ = (forward * cos + sideways * sin) / length;
                     double dot = worldX * movement.x + worldZ * movement.z;
                     if (dot < bestDot) {
                        bestDot = dot;
                        bestSideways = sideways;
                        bestForward = forward;
                     }
                  }
               }
            }

            Input current = player.input.keyPresses;
            player.input.keyPresses = new Input(
               bestForward > 0,
               bestForward < 0,
               bestSideways > 0,
               bestSideways < 0,
               miningCounter && current.jump(),
               current.shift(),
               current.sprint() && bestForward > 0
            );
            player.input.moveVector = new Vec2(bestSideways, bestForward).normalized();
            this.counterTicks--;
         } else {
            this.clearCounter();
         }
      } else {
         this.clearCounter();
      }
   }

   public boolean shouldCancelEntityPush() {
      return this.isEnabled() && this.entityPush.getValue();
   }

   public boolean shouldCancelExplosion() {
      if (this.isEnabled() && this.explosions.getValue()) {
         return this.mode.getValue() == VelocityModule.Mode.NORMAL
            ? this.phaseConditionMet()
            : this.mode.getValue() == VelocityModule.Mode.STATIC && this.cancelStaticVelocity();
      } else {
         return false;
      }
   }

   private boolean cancelStaticVelocity() {
      if (!this.staticConditionMet()) {
         return false;
      } else if (this.miningOwnsDigging()) {
         if (!this.canCancelWhileMining()) {
            return false;
         } else {
            this.clearCounter();
            return true;
         }
      } else {
         this.clearCounter();
         this.staticCancelVelocity = true;
         return true;
      }
   }

   private boolean shouldCounterWhileMining() {
      return this.mode.getValue() == VelocityModule.Mode.STATIC && this.staticConditionMet() && this.miningOwnsDigging() && !this.canCancelWhileMining();
   }

   private boolean canCancelWhileMining() {
      if (!mc.player.onGround()) {
         return false;
      } else {
         AABB interior = mc.player.getBoundingBox().deflate(1.0E-4);
         return mc.level.getBlockCollisions(mc.player, interior).iterator().hasNext();
      }
   }

   public boolean hasPendingStaticCorrection() {
      return this.staticCancelVelocity;
   }

   private boolean miningOwnsDigging() {
      SpeedMineController speedMine = KetamineClub.moduleManager.getSpeedMine();
      return speedMine != null && speedMine.hasActiveMining();
   }

   public boolean phaseConditionMet() {
      if (!this.phaseOnly.getValue()) {
         return true;
      } else {
         Minecraft mc = Minecraft.getInstance();
         return mc.player != null && mc.level != null ? mc.level.getBlockCollisions(mc.player, mc.player.getBoundingBox()).iterator().hasNext() : false;
      }
   }

   private boolean staticConditionMet() {
      return !nullCheck() && this.phaseConditionMet() && !mc.player.isFallFlying() && !mc.player.isPassenger()
         ? !mc.options.keyUp.isDown() && !mc.options.keyDown.isDown() && !mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()
         : false;
   }

   private void sendStaticCorrection() {
      if (mc.player != null && mc.getConnection() != null) {
         float yaw = mc.player.getYRot();
         float pitch = mc.player.getXRot();
         if (KetamineClub.rotationManager != null && KetamineClub.rotationManager.hasMovementRotation()) {
            yaw = KetamineClub.rotationManager.getMovementYaw();
            pitch = KetamineClub.rotationManager.getMovementPitch();
         }

         mc.getConnection()
            .send(new PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
         mc.getConnection()
            .send(
               new ServerboundPlayerActionPacket(
                  Action.STOP_DESTROY_BLOCK, mc.player.isVisuallyCrawling() ? mc.player.blockPosition() : mc.player.blockPosition().above(), Direction.DOWN
               )
            );
      }
   }

   private Vec3 suffocationPushBudget() {
      LocalPlayer player = mc.player;
      AABB bounds = player.getBoundingBox();
      double sample = player.getBbWidth() * 0.35;
      double xBudget = 0.0;
      double zBudget = 0.0;

      for (int xSign : new int[]{-1, 1}) {
         for (int zSign : new int[]{-1, 1}) {
            Direction direction = this.closestOpenDirection(player.getX() + sample * xSign, player.getZ() + sample * zSign, bounds);
            if (direction != null) {
               if (direction.getAxis() == Axis.X) {
                  xBudget += 0.145;
               } else {
                  zBudget += 0.145;
               }
            }
         }
      }

      return new Vec3(xBudget, 0.0, zBudget);
   }

   private Direction closestOpenDirection(double x, double z, AABB bounds) {
      BlockPos pos = BlockPos.containing(x, mc.player.getY(), z);
      if (!this.suffocatesAt(pos, bounds)) {
         return null;
      } else {
         double relativeX = x - pos.getX();
         double relativeZ = z - pos.getZ();
         Direction closest = null;
         double closestDistance = Double.MAX_VALUE;

         for (Direction direction : PUSH_DIRECTIONS) {
            double relative = direction.getAxis() == Axis.X ? relativeX : relativeZ;
            double distance = direction.getAxisDirection() == AxisDirection.POSITIVE ? 1.0 - relative : relative;
            if (!(distance >= closestDistance) && !this.suffocatesAt(pos.relative(direction), bounds)) {
               closestDistance = distance;
               closest = direction;
            }
         }

         return closest;
      }
   }

   private boolean suffocatesAt(BlockPos pos, AABB bounds) {
      AABB column = new AABB(pos.getX(), bounds.minY, pos.getZ(), pos.getX() + 1.0, bounds.maxY, pos.getZ() + 1.0).deflate(1.0E-7);
      return mc.level.collidesWithSuffocatingBlock(mc.player, column);
   }

   private static double reduceTowardZero(double value, double amount) {
      return Math.copySign(Math.max(0.0, Math.abs(value) - amount), value);
   }

   private void clearCounter() {
      this.counterDirection = Vec3.ZERO;
      this.counterTicks = 0;
      this.counterFromMining = false;
   }

   public static enum Mode {
      NORMAL,
      GRIM,
      STATIC;

      private Mode() {
      }
   }
}
