package we.ketamine.club.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.util.traits.Util;

public class MathUtil implements Util {
   private static final Random random = new Random();

   public MathUtil() {
   }

   public static int getRandom(int min, int max) {
      return min + random.nextInt(max - min + 1);
   }

   public static double getRandom(double min, double max) {
      return Mth.clamp(min + random.nextDouble() * max, min, max);
   }

   public static float getRandom(float min, float max) {
      return Mth.clamp(min + random.nextFloat() * max, min, max);
   }

   public static int clamp(int num, int min, int max) {
      return num < min ? min : Math.min(num, max);
   }

   public static float clamp(float num, float min, float max) {
      return num < min ? min : Math.min(num, max);
   }

   public static double clamp(double num, double min, double max) {
      return num < min ? min : Math.min(num, max);
   }

   public static float sin(float value) {
      return Mth.sin(value);
   }

   public static float cos(float value) {
      return Mth.cos(value);
   }

   public static float wrapDegrees(float value) {
      return Mth.wrapDegrees(value);
   }

   public static double wrapDegrees(double value) {
      return Mth.wrapDegrees(value);
   }

   public static Vec3 roundVec(Vec3 vec3d, int places) {
      return new Vec3(round(vec3d.x, places), round(vec3d.y, places), round(vec3d.z, places));
   }

   public static double square(double input) {
      return input * input;
   }

   public static double round(double value, int places) {
      if (places < 0) {
         throw new IllegalArgumentException();
      } else {
         BigDecimal bd = BigDecimal.valueOf(value);
         bd = bd.setScale(places, RoundingMode.FLOOR);
         return bd.doubleValue();
      }
   }

   public static float wrap(float valI) {
      float val = valI % 360.0F;
      if (val >= 180.0F) {
         val -= 360.0F;
      }

      if (val < -180.0F) {
         val += 360.0F;
      }

      return val;
   }

   public static Vec3 direction(float yaw) {
      return new Vec3(Math.cos(degToRad(yaw + 90.0F)), 0.0, Math.sin(degToRad(yaw + 90.0F)));
   }

   public static float round(float value, int places) {
      if (places < 0) {
         throw new IllegalArgumentException();
      } else {
         BigDecimal bd = BigDecimal.valueOf((double)value);
         bd = bd.setScale(places, RoundingMode.FLOOR);
         return bd.floatValue();
      }
   }

   public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map, boolean descending) {
      LinkedList<Entry<K, V>> list = new LinkedList<>(map.entrySet());
      if (descending) {
         list.sort(Entry.comparingByValue(Comparator.reverseOrder()));
      } else {
         list.sort(Entry.comparingByValue());
      }

      LinkedHashMap<K, V> result = new LinkedHashMap<>();

      for (Entry<K, V> entry : list) {
         result.put(entry.getKey(), entry.getValue());
      }

      return result;
   }

   public static String getTimeOfDay() {
      Calendar c = Calendar.getInstance();
      int timeOfDay = c.get(11);
      if (timeOfDay < 12) {
         return "Good Morning ";
      } else if (timeOfDay < 16) {
         return "Good Afternoon ";
      } else {
         return timeOfDay < 21 ? "Good Evening " : "Good Night ";
      }
   }

   public static double radToDeg(double rad) {
      return rad * (float) (180.0 / Math.PI);
   }

   public static double degToRad(double deg) {
      return deg * (float) (Math.PI / 180.0);
   }

   public static double getIncremental(double val, double inc) {
      double one = 1.0 / inc;
      return Math.round(val * one) / one;
   }

   public static double[] directionSpeed(double speed) {
      float forward = mc.player.input.getMoveVector().y;
      float side = mc.player.input.getMoveVector().x;
      float yaw = mc.player.yRotO + (mc.player.getYRot() - mc.player.yRotO) * mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
      if (forward != 0.0F) {
         if (side > 0.0F) {
            yaw += forward > 0.0F ? -45 : 45;
         } else if (side < 0.0F) {
            yaw += forward > 0.0F ? 45 : -45;
         }

         side = 0.0F;
         if (forward > 0.0F) {
            forward = 1.0F;
         } else if (forward < 0.0F) {
            forward = -1.0F;
         }
      }

      double sin = Math.sin(Math.toRadians(yaw + 90.0F));
      double cos = Math.cos(Math.toRadians(yaw + 90.0F));
      double posX = forward * speed * cos + side * speed * sin;
      double posZ = forward * speed * sin - side * speed * cos;
      return new double[]{posX, posZ};
   }

   public static List<Vec3> getBlockBlocks(Entity entity) {
      ArrayList<Vec3> vec3ds = new ArrayList<>();
      AABB bb = entity.getBoundingBox();
      double y = entity.getY();
      double minX = round(bb.minX, 0);
      double minZ = round(bb.minZ, 0);
      double maxX = round(bb.maxX, 0);
      double maxZ = round(bb.maxZ, 0);
      if (minX != maxX) {
         vec3ds.add(new Vec3(minX, y, minZ));
         vec3ds.add(new Vec3(maxX, y, minZ));
         if (minZ != maxZ) {
            vec3ds.add(new Vec3(minX, y, maxZ));
            vec3ds.add(new Vec3(maxX, y, maxZ));
            return vec3ds;
         }
      } else if (minZ != maxZ) {
         vec3ds.add(new Vec3(minX, y, minZ));
         vec3ds.add(new Vec3(minX, y, maxZ));
         return vec3ds;
      }

      vec3ds.add(entity.position());
      return vec3ds;
   }

   public static boolean areVec3dsAligned(Vec3 vec3d1, Vec3 vec3d2) {
      return areVec3dsAlignedRetarded(vec3d1, vec3d2);
   }

   public static boolean areVec3dsAlignedRetarded(Vec3 vec3d1, Vec3 vec3d2) {
      BlockPos pos1 = BlockPos.containing(vec3d1);
      BlockPos pos2 = BlockPos.containing(vec3d2.x, vec3d1.y, vec3d2.z);
      return pos1.equals(pos2);
   }

   public static float[] calcAngle(Vec3 from, Vec3 to) {
      double difX = to.x - from.x;
      double difY = (to.y - from.y) * -1.0;
      double difZ = to.z - from.z;
      double dist = Math.sqrt(difX * difX + difZ * difZ);
      return new float[]{(float)Mth.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0), (float)Mth.wrapDegrees(Math.toDegrees(Math.atan2(difY, dist)))};
   }
}
