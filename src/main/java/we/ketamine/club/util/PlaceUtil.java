package we.ketamine.club.util;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.util.traits.Util;

public final class PlaceUtil implements Util {
   private PlaceUtil() {
      throw new AssertionError();
   }

   public static int minCell(double boxMin) {
      return Mth.floor(boxMin);
   }

   public static int maxCell(double boxMax) {
      return Mth.ceil(boxMax) - 1;
   }

   public static boolean canPlace(BlockPos pos) {
      if (mc.level.isOutsideBuildHeight(pos)) {
         return false;
      } else if (!mc.level.getBlockState(pos).canBeReplaced()) {
         return false;
      } else if (Vec3.atCenterOf(pos).distanceTo(mc.player.getEyePosition()) > 5.154) {
         return false;
      } else {
         return mc.player.getBoundingBox().intersects(new AABB(pos)) ? false : noEntityCollision(pos);
      }
   }

   public static boolean noEntityCollision(BlockPos pos) {
      Iterator var1 = mc.level
         .getEntitiesOfClass(
            Entity.class,
            new AABB(pos),
            ex -> !(ex instanceof ItemEntity)
               && !(ex instanceof ExperienceOrb)
               && !(ex instanceof ThrownExperienceBottle)
               && !(ex instanceof FireworkRocketEntity)
               && !(ex instanceof EndCrystal)
               && ex != mc.player
         )
         .iterator();
      if (var1.hasNext()) {
         Entity e = (Entity)var1.next();
         return false;
      } else {
         return true;
      }
   }
}
