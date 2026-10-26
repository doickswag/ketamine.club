package we.ketamine.club.pingbypass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PingBypassMotionSmoothing {
   private static final MotionInterpolator playerMotion = new MotionInterpolator();
   private static final MotionInterpolator vehicleMotion = new MotionInterpolator();
   private static LocalPlayer player;
   private static Level level;
   private static Entity vehicle;
   private static Entity rootVehicle;
   private static Vec3 framePlayer;
   private static Vec3 frameVehicle;

   private PingBypassMotionSmoothing() {
   }

   static void accept(LocalPlayer localPlayer, Vec3 position, Entity mount, Vec3 mountPosition) {
      if (player != localPlayer || level != localPlayer.level() || rootVehicle != localPlayer.getRootVehicle()) {
         reset();
         player = localPlayer;
         level = localPlayer.level();
         rootVehicle = localPlayer.getRootVehicle();
      }

      long now = System.nanoTime();
      playerMotion.update(localPlayer.position(), position, now);
      if (vehicle != mount) {
         vehicleMotion.reset();
      }

      vehicle = mount;
      if (mount != null) {
         vehicleMotion.update(mount.position(), mountPosition, now);
      }
   }

   public static void beginFrame() {
      Minecraft mc = Minecraft.getInstance();
      if (PingBypass.get().suppressViewerMovement()
         && player == mc.player
         && level == mc.level
         && player != null
         && !player.isDeadOrDying()
         && rootVehicle == player.getRootVehicle()) {
         long now = System.nanoTime();
         framePlayer = playerMotion.sample(now);
         frameVehicle = vehicleMotion.sample(now);
      } else {
         reset();
      }
   }

   public static Vec3 renderPosition(Entity entity) {
      if (!PingBypass.get().suppressViewerMovement()) {
         return null;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (entity != null && mc.player != null && mc.level != null) {
            boolean current = player == mc.player && level == mc.level && rootVehicle == player.getRootVehicle();
            if (entity == mc.player) {
               return current && framePlayer != null ? framePlayer : entity.position();
            } else if (current && entity == vehicle) {
               return frameVehicle == null ? entity.position() : frameVehicle;
            } else {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   public static void reset() {
      playerMotion.reset();
      vehicleMotion.reset();
      player = null;
      level = null;
      rootVehicle = null;
      vehicle = null;
      frameVehicle = null;
      framePlayer = null;
   }
}
