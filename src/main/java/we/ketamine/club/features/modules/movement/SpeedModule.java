package we.ketamine.club.features.modules.movement;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.player.ChatUtil;

public class SpeedModule extends Module {
   public final Setting<Float> collisionDistance = this.num("CollisionDistance", 1.5F, 0.5F, 2.0F);
   public final Setting<Float> boostPerCollision = this.num("BoostPerCollision", 0.08F, 0.01F, 0.2F);
   private int debugTicks = 0;

   public SpeedModule() {
      super("Speed", "Boosts your movement off entity collisions", Module.Category.MOVEMENT);
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         boolean moving = this.isInputtingMovement();
         int collisions = this.countCollidingEntities();
         int nearest = this.nearestLivingDistance();
         if (++this.debugTicks % 20 == 0) {
            ChatUtil.sendMessage(
               Component.literal(
                  "[speed] moving="
                     + moving
                     + " collisions="
                     + collisions
                     + " nearest="
                     + (nearest < 0 ? "none" : nearest / 100.0)
                     + " vel="
                     + mc.player.getDeltaMovement().horizontalDistance()
               )
            );
         }

         if (moving && collisions > 0) {
            double[] motion = MathUtil.directionSpeed(this.boostPerCollision.getValue() * collisions);
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x + motion[0], velocity.y, velocity.z + motion[1]);
         }
      }
   }

   private int nearestLivingDistance() {
      double best = Double.MAX_VALUE;

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (this.isCollidable(entity)) {
            best = Math.min(best, Math.sqrt(mc.player.distanceToSqr(entity)));
         }
      }

      return best == Double.MAX_VALUE ? -1 : (int)(best * 100.0);
   }

   private int countCollidingEntities() {
      double range = this.collisionDistance.getValue().floatValue();
      int collisions = 0;

      for (Entity entity : mc.level.entitiesForRendering()) {
         if (this.isCollidable(entity) && mc.player.distanceToSqr(entity) <= range * range) {
            collisions++;
         }
      }

      return collisions;
   }

   private boolean isCollidable(Entity entity) {
      return entity != null && entity != mc.player ? entity instanceof LivingEntity && !(entity instanceof ArmorStand) : false;
   }

   private boolean isInputtingMovement() {
      return mc.player.input.getMoveVector().x != 0.0F || mc.player.input.getMoveVector().y != 0.0F;
   }
}
