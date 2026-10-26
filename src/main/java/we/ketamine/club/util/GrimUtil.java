package we.ketamine.club.util;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class GrimUtil {
   private GrimUtil() {
   }

   public static double[] getPossibleEyeHeights(Player player) {
      float scale = (float)player.getAttributeValue(Attributes.SCALE);
      double standing = 1.62 * scale;
      double sneaking = 1.27 * scale;
      double swimming = 0.4 * scale;
      Pose pose = player.getPose();

      return switch (pose) {
         case FALL_FLYING, SPIN_ATTACK, SWIMMING -> new double[]{swimming, standing, sneaking};
         case CROUCHING -> new double[]{sneaking, standing, swimming};
         default -> new double[]{standing, sneaking, swimming};
      };
   }

   public static Vec3[] getPossibleEyePositions(Player player) {
      Vec3 position = player.position();
      double[] heights = getPossibleEyeHeights(player);
      Vec3[] result = new Vec3[heights.length];

      for (int i = 0; i < heights.length; i++) {
         result[i] = position.add(0.0, heights[i], 0.0);
      }

      return result;
   }
}
