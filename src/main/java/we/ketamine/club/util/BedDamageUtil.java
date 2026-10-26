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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import we.ketamine.club.util.traits.Util;

public final class BedDamageUtil implements Util {
   public static final double DIAMETER = 10.0;

   private BedDamageUtil() {
      throw new AssertionError();
   }

   public static float maxDamage(BedDamageUtil.Target target, Vec3 explosionPos) {
      double distance = target.pos().distanceTo(explosionPos);
      return distance > 10.0 ? 0.0F : targetDamage(target, distance, 1.0);
   }

   public static float damage(BedDamageUtil.Target target, Vec3 explosionPos, BlockPos foot, BlockPos head) {
      double distance = target.pos().distanceTo(explosionPos);
      return distance > 10.0 ? 0.0F : targetDamage(target, distance, exposure(explosionPos, target.box(), foot, head));
   }

   public static float selfDamage(Vec3 explosionPos, BlockPos foot, BlockPos head) {
      if (mc.player != null && mc.level != null) {
         double distance = mc.player.position().distanceTo(explosionPos);
         if (distance > 10.0) {
            return 0.0F;
         } else {
            double exposure = exposure(explosionPos, mc.player.getBoundingBox(), foot, head);
            if (exposure <= 0.0) {
               return 0.0F;
            } else {
               double impact = (1.0 - distance / 10.0) * exposure;
               if (impact <= 0.0) {
                  return 0.0F;
               } else {
                  float damage = baseDamage(impact);
                  damage = armorAbsorb(
                     damage, (float)mc.player.getAttributeValue(Attributes.ARMOR), (float)mc.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
                  );
                  MobEffectInstance resistance = mc.player.getEffect(MobEffects.RESISTANCE);
                  if (resistance != null) {
                     damage *= Math.max(0.0F, 1.0F - 0.2F * (resistance.getAmplifier() + 1));
                  }

                  return Math.max(CombatRules.getDamageAfterMagicAbsorb(damage, selfProtectionPoints()), 0.0F);
               }
            }
         }
      } else {
         return 0.0F;
      }
   }

   private static float targetDamage(BedDamageUtil.Target target, double distance, double exposure) {
      if (exposure <= 0.0) {
         return 0.0F;
      } else {
         double impact = (1.0 - distance / 10.0) * exposure;
         if (impact <= 0.0) {
            return 0.0F;
         } else {
            float damage = baseDamage(impact);
            damage = armorAbsorb(damage, target.armor(), target.toughness());
            damage *= target.resistanceMultiplier();
            damage = CombatRules.getDamageAfterMagicAbsorb(damage, target.protectionPoints());
            return Math.max(damage, 0.0F);
         }
      }
   }

   private static float baseDamage(double impact) {
      float damage = (float)((impact * impact + impact) / 2.0 * 7.0 * 10.0 + 1.0);
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

      return damage;
   }

   private static float armorAbsorb(float damage, float armor, float toughness) {
      float divisor = 2.0F + toughness / 4.0F;
      float reducedArmor = Mth.clamp(armor - damage / divisor, armor * 0.2F, 20.0F);
      return damage * (1.0F - reducedArmor / 25.0F);
   }

   private static int selfProtectionPoints() {
      int protectionPoints = 0;

      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = mc.player.getItemBySlot(slot);
         if (!stack.isEmpty()) {
            protectionPoints += EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
            protectionPoints += 2 * EnchantmentUtil.getLevel(Enchantments.BLAST_PROTECTION, stack);
         }
      }

      return Math.min(protectionPoints, 20);
   }

   private static double exposure(Vec3 source, AABB box, BlockPos foot, BlockPos head) {
      double stepX = 1.0 / (box.getXsize() * 2.0 + 1.0);
      double stepY = 1.0 / (box.getYsize() * 2.0 + 1.0);
      double stepZ = 1.0 / (box.getZsize() * 2.0 + 1.0);
      double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
      double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
      int visible = 0;
      int total = 0;
      BiFunction<Vec3[], BlockPos, BlockHitResult> tester = (contextx, pos) -> {
         if (!pos.equals(foot) && !pos.equals(head)) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) {
               return null;
            } else {
               VoxelShape shape = state.getCollisionShape(mc.level, pos);
               return shape.isEmpty() ? null : shape.clip(contextx[0], contextx[1], pos);
            }
         } else {
            return null;
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
      public static BedDamageUtil.Target of(LivingEntity living) {
         float armor = (float)living.getAttributeValue(Attributes.ARMOR);
         float toughness = (float)living.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
         int protectionPoints = 0;
         if (!living.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            protectionPoints += 4;
         }

         if (!living.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            protectionPoints += 4;
         }

         if (!living.getItemBySlot(EquipmentSlot.LEGS).isEmpty()) {
            protectionPoints += 8;
         }

         if (!living.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
            protectionPoints += 8;
         }

         MobEffectInstance resistance = living.getEffect(MobEffects.RESISTANCE);
         float resistanceMultiplier = resistance == null ? 1.0F : Math.max(0.0F, 1.0F - 0.2F * (resistance.getAmplifier() + 1));
         return new BedDamageUtil.Target(living.position(), living.getBoundingBox(), armor, toughness, resistanceMultiplier, Math.min(protectionPoints, 20));
      }
   }
}
