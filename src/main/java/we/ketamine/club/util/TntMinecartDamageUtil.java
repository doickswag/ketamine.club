package we.ketamine.club.util;

import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import we.ketamine.club.util.traits.Util;

public final class TntMinecartDamageUtil implements Util {
   public static final double TNT_DIAMETER = 8.0;
   public static final double CRYSTAL_DIAMETER = 12.0;
   private static final float BEST_ARMOR = 20.0F;
   private static final float BEST_TOUGHNESS = 12.0F;
   private static final int BEST_PROTECTION_POINTS = 20;

   private TntMinecartDamageUtil() {
      throw new AssertionError();
   }

   public static float maxDamage(TntMinecartDamageUtil.Target target, Vec3 minecartPos, Vec3 crystalPos, int minecartCount) {
      return damageAt(target, minecartPos, 8.0, 1.0) * Math.max(minecartCount, 0) + damageAt(target, crystalPos, 12.0, 1.0);
   }

   public static float damage(TntMinecartDamageUtil.Target target, Vec3 minecartPos, Vec3 crystalPos, @Nullable BlockPos plannedObsidian, int minecartCount) {
      float minecartDamage = damageAt(target, minecartPos, 8.0, exposure(minecartPos, target.box(), plannedObsidian));
      float crystalDamage = damageAt(target, crystalPos, 12.0, exposure(crystalPos, target.box(), plannedObsidian));
      return minecartDamage * Math.max(minecartCount, 0) + crystalDamage;
   }

   public static float selfDamage(Vec3 minecartPos, Vec3 crystalPos, @Nullable BlockPos plannedObsidian, int minecartCount) {
      if (mc.player != null && mc.level != null) {
         TntMinecartDamageUtil.Target self = TntMinecartDamageUtil.Target.of(mc.player);
         return damage(self, minecartPos, crystalPos, plannedObsidian, minecartCount);
      } else {
         return 0.0F;
      }
   }

   private static float damageAt(TntMinecartDamageUtil.Target target, Vec3 explosionPos, double diameter, double exposure) {
      if (exposure <= 0.0) {
         return 0.0F;
      } else {
         double distance = target.pos().distanceTo(explosionPos);
         if (distance > diameter) {
            return 0.0F;
         } else {
            double impact = (1.0 - distance / diameter) * exposure;
            if (impact <= 0.0) {
               return 0.0F;
            } else {
               float damage = (float)((impact * impact + impact) / 2.0 * 7.0 * diameter + 1.0);
               switch (mc.level.getDifficulty()) {
                  case PEACEFUL:
                     damage = 0.0F;
                     break;
                  case EASY:
                     damage = Math.min(damage / 2.0F + 1.0F, damage);
                     break;
                  case HARD:
                     damage *= 1.5F;
               }

               float divisor = 2.0F + target.toughness() / 4.0F;
               float reducedArmor = Mth.clamp(target.armor() - damage / divisor, target.armor() * 0.2F, 20.0F);
               damage *= 1.0F - reducedArmor / 25.0F;
               damage *= target.resistanceMultiplier();
               return Math.max(CombatRules.getDamageAfterMagicAbsorb(damage, target.protectionPoints()), 0.0F);
            }
         }
      }
   }

   private static int protectionPoints(LivingEntity living) {
      int protectionPoints = 0;

      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = living.getItemBySlot(slot);
         if (!stack.isEmpty()) {
            protectionPoints += EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
            protectionPoints += 2 * EnchantmentUtil.getLevel(Enchantments.BLAST_PROTECTION, stack);
         }
      }

      return Math.min(protectionPoints, 20);
   }

   private static double exposure(Vec3 source, AABB box, @Nullable BlockPos plannedObsidian) {
      double stepX = 1.0 / (box.getXsize() * 2.0 + 1.0);
      double stepY = 1.0 / (box.getYsize() * 2.0 + 1.0);
      double stepZ = 1.0 / (box.getZsize() * 2.0 + 1.0);
      double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
      double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
      int visible = 0;
      int total = 0;
      BiFunction<Vec3[], BlockPos, BlockHitResult> tester = (contextx, pos) -> {
         BlockState state = plannedObsidian != null && pos.equals(plannedObsidian) ? Blocks.OBSIDIAN.defaultBlockState() : mc.level.getBlockState(pos);
         if (state.isAir()) {
            return null;
         } else {
            VoxelShape shape = state.getCollisionShape(mc.level, pos);
            return shape.isEmpty() ? null : shape.clip(contextx[0], contextx[1], pos);
         }
      };

      for (double x = 0.0; x <= 1.0; x += stepX) {
         for (double y = 0.0; y <= 1.0; y += stepY) {
            for (double z = 0.0; z <= 1.0; z += stepZ) {
               Vec3 point = new Vec3(Mth.lerp(x, box.minX, box.maxX) + offsetX, Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ) + offsetZ);
               Vec3[] context = new Vec3[]{point, source};
               if (BlockGetter.traverseBlocks(point, source, context, tester, ignored -> null) == null) {
                  visible++;
               }

               total++;
            }
         }
      }

      return total == 0 ? 0.0 : (double)visible / total;
   }

   public record Target(Vec3 pos, AABB box, float armor, float toughness, float resistanceMultiplier, int protectionPoints) {
      public static TntMinecartDamageUtil.Target of(LivingEntity living) {
         return of(
            living,
            (float)living.getAttributeValue(Attributes.ARMOR),
            (float)living.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
            TntMinecartDamageUtil.protectionPoints(living)
         );
      }

      public static TntMinecartDamageUtil.Target ofBestArmor(LivingEntity living) {
         return of(living, 20.0F, 12.0F, 20);
      }

      private static TntMinecartDamageUtil.Target of(LivingEntity living, float armor, float toughness, int protectionPoints) {
         MobEffectInstance resistance = living.getEffect(MobEffects.RESISTANCE);
         float resistanceMultiplier = resistance == null ? 1.0F : Math.max(0.0F, 1.0F - 0.2F * (resistance.getAmplifier() + 1));
         return new TntMinecartDamageUtil.Target(living.position(), living.getBoundingBox(), armor, toughness, resistanceMultiplier, protectionPoints);
      }
   }
}
