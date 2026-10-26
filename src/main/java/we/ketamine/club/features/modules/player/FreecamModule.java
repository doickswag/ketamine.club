package we.ketamine.club.features.modules.player;

import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class FreecamModule extends Module {
   public final Setting<Double> speed = this.num("Speed", 0.5, 0.1, 3.0);
   public final Setting<Boolean> smooth = this.bool("Smooth", true);
   public final Setting<Double> acceleration = this.num("Acceleration", 0.35, 0.05, 1.0);
   public final Setting<Double> friction = this.num("Friction", 0.2, 0.02, 1.0);
   private static final double STOP_EPSILON = 1.0E-4;
   private CameraType previousCameraType;
   private Vec3 position;
   private Vec3 previousPosition;
   private Vec3 velocity = Vec3.ZERO;
   private float yaw;
   private float previousYaw;
   private float pitch;
   private float previousPitch;

   public FreecamModule() {
      super("Freecam", "Detaches the camera from the player while leaving the player in place.", Module.Category.PLAYER);
      this.acceleration.setVisibility(v -> this.smooth.getValue());
      this.friction.setVisibility(v -> this.smooth.getValue());
   }

   @Override
   public void onEnable() {
      if (mc.player != null && mc.level != null) {
         this.previousCameraType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.FIRST_PERSON);
         this.position = mc.gameRenderer.mainCamera().position();
         this.previousPosition = this.position;
         this.velocity = Vec3.ZERO;
         this.yaw = this.previousYaw = mc.player.getYRot();
         this.pitch = this.previousPitch = mc.player.getXRot();
      } else {
         this.disable();
      }
   }

   @Override
   public void onTick() {
      if (this.position != null && mc.player != null && mc.level != null) {
         double forward = axis(mc.options.keyUp.isDown(), mc.options.keyDown.isDown());
         double strafe = axis(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown());
         double vertical = axis(mc.options.keyJump.isDown(), mc.options.keyShift.isDown());
         double yawRadians = Math.toRadians(this.yaw);
         Vec3 wish = new Vec3(
            strafe * Math.cos(yawRadians) - forward * Math.sin(yawRadians), vertical, forward * Math.cos(yawRadians) + strafe * Math.sin(yawRadians)
         );
         if (wish.lengthSqr() > 0.0) {
            wish = wish.normalize();
         }

         double maxSpeed = this.speed.getValue();
         if (this.smooth.getValue()) {
            this.velocity = this.applyFriction(this.velocity);
            this.velocity = this.accelerate(this.velocity, wish, maxSpeed);
         } else {
            this.velocity = wish.scale(maxSpeed);
         }

         this.previousPosition = this.position;
         this.previousYaw = this.yaw;
         this.previousPitch = this.pitch;
         this.position = this.position.add(this.velocity);
      } else {
         this.disable();
      }
   }

   private Vec3 applyFriction(Vec3 current) {
      double length = current.length();
      if (length < 1.0E-4) {
         return Vec3.ZERO;
      } else {
         double drop = Math.max(length, this.speed.getValue() * 0.25) * this.friction.getValue();
         double scaled = Math.max(0.0, length - drop) / length;
         return current.scale(scaled);
      }
   }

   private Vec3 accelerate(Vec3 current, Vec3 wish, double maxSpeed) {
      if (wish.lengthSqr() == 0.0) {
         return current;
      } else {
         double addSpeed = maxSpeed - current.dot(wish);
         if (addSpeed <= 0.0) {
            return current;
         } else {
            double accel = Math.min(this.acceleration.getValue() * maxSpeed, addSpeed);
            return current.add(wish.scale(accel));
         }
      }
   }

   @Override
   public void onDisable() {
      if (this.previousCameraType != null) {
         mc.options.setCameraType(this.previousCameraType);
      }

      this.previousCameraType = null;
      this.position = null;
      this.previousPosition = null;
      this.velocity = Vec3.ZERO;
   }

   public void turnCamera(double yaw, double pitch) {
      this.previousYaw = this.yaw;
      this.previousPitch = this.pitch;
      this.yaw += (float)yaw * 0.15F;
      this.pitch = Mth.clamp(this.pitch + (float)pitch * 0.15F, -90.0F, 90.0F);
   }

   public Vec3 getCameraPosition(float tickDelta) {
      return new Vec3(
         Mth.lerp(tickDelta, this.previousPosition.x, this.position.x),
         Mth.lerp(tickDelta, this.previousPosition.y, this.position.y),
         Mth.lerp(tickDelta, this.previousPosition.z, this.position.z)
      );
   }

   public float getCameraYaw(float tickDelta) {
      return Mth.lerp(tickDelta, this.previousYaw, this.yaw);
   }

   public float getCameraPitch(float tickDelta) {
      return Mth.lerp(tickDelta, this.previousPitch, this.pitch);
   }

   private static int axis(boolean positive, boolean negative) {
      return positive == negative ? 0 : (positive ? 1 : -1);
   }
}
