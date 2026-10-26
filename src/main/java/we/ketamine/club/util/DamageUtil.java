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

public final class DamageUtil implements Util {
   private DamageUtil() {
      throw new AssertionError();
   }

   public static float crystalMaxSelfDamage(Vec3 explosionPos) {
      if (mc.player != null && mc.level != null) {
         double dist = mc.player.position().distanceTo(explosionPos);
         if (dist > 12.0) {
            return 0.0F;
         } else {
            double impact = 1.0 - dist / 12.0;
            if (impact <= 0.0) {
               return 0.0F;
            } else {
               float dmg = baseExplosionDamage(impact);
               float armor = (float)mc.player.getAttributeValue(Attributes.ARMOR);
               float toughness = (float)mc.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
               dmg = armorAbsorb(dmg, armor, toughness);
               MobEffectInstance resistance = mc.player.getEffect(MobEffects.RESISTANCE);
               if (resistance != null) {
                  dmg *= 1.0F - 0.2F * (resistance.getAmplifier() + 1);
               }

               dmg = protectionAbsorb(dmg);
               return Math.max(dmg, 0.0F);
            }
         }
      } else {
         return 0.0F;
      }
   }

   public static float crystalDamage(LivingEntity target, Vec3 explosionPos, BlockPos ignoredBlock) {
      if (target != null && mc.level != null) {
         double distance = target.position().distanceTo(explosionPos);
         if (distance > 12.0) {
            return 0.0F;
         } else {
            double exposure = exposure(explosionPos, target.getBoundingBox(), ignoredBlock);
            double impact = (1.0 - distance / 12.0) * exposure;
            if (impact <= 0.0) {
               return 0.0F;
            } else {
               float damage = baseExplosionDamage(impact);
               float armor = (float)target.getAttributeValue(Attributes.ARMOR);
               float toughness = (float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
               damage = armorAbsorb(damage, armor, toughness);
               MobEffectInstance resistance = target.getEffect(MobEffects.RESISTANCE);
               if (resistance != null) {
                  damage *= 1.0F - 0.2F * (resistance.getAmplifier() + 1);
               }

               int protection = 0;

               for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                  ItemStack stack = target.getItemBySlot(slot);
                  if (!stack.isEmpty()) {
                     protection += EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
                     protection += 2 * EnchantmentUtil.getLevel(Enchantments.BLAST_PROTECTION, stack);
                  }
               }

               return Math.max(CombatRules.getDamageAfterMagicAbsorb(damage, Math.min(protection, 20)), 0.0F);
            }
         }
      } else {
         return 0.0F;
      }
   }

   public static float crystalBaseDamage(LivingEntity target, Vec3 explosionPos, boolean assumeBestArmor) {
      if (target != null && mc.level != null) {
         double distance = target.position().distanceTo(explosionPos);
         if (distance > 12.0) {
            return 0.0F;
         } else {
            float exposure = (float)exposure(explosionPos, target.getBoundingBox(), null, true);
            double impact = (1.0 - distance / 12.0) * exposure;
            float damage = baseExplosionDamage(impact);
            damage = CombatRules.getDamageAfterAbsorb(
               target,
               damage,
               mc.level.damageSources().explosion(null),
               (float)Math.floor(target.getAttributeValue(Attributes.ARMOR)),
               (float)target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
            );
            MobEffectInstance resistance = target.getEffect(MobEffects.RESISTANCE);
            if (resistance != null) {
               damage = Math.max(0.0F, damage * (1.0F - 0.2F * (resistance.getAmplifier() + 1)));
            }

            int protection = 0;

            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
               ItemStack stack = target.getItemBySlot(slot);
               if (!stack.isEmpty()) {
                  if (assumeBestArmor) {
                     protection += slot == EquipmentSlot.LEGS ? 8 : 4;
                  } else {
                     protection += EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
                     protection += 2 * EnchantmentUtil.getLevel(Enchantments.BLAST_PROTECTION, stack);
                  }
               }
            }

            return Math.max(CombatRules.getDamageAfterMagicAbsorb(damage, protection), 0.0F);
         }
      } else {
         return 0.0F;
      }
   }

   private static float baseExplosionDamage(double impact) {
      float dmg = (float)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
      switch (mc.level.getDifficulty()) {
         case EASY:
            dmg = Math.min(dmg / 2.0F + 1.0F, dmg);
            break;
         case HARD:
            dmg *= 1.5F;
      }

      return dmg;
   }

   private static float armorAbsorb(float dmg, float armor, float toughness) {
      float i = 2.0F + toughness / 4.0F;
      float j = Mth.clamp(armor - dmg / i, armor * 0.2F, 20.0F);
      return dmg * (1.0F - j / 25.0F);
   }

   private static float protectionAbsorb(float dmg) {
      int epf = 0;

      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = mc.player.getItemBySlot(slot);
         if (!stack.isEmpty()) {
            epf += EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
            epf += 2 * EnchantmentUtil.getLevel(Enchantments.BLAST_PROTECTION, stack);
         }
      }

      if (epf > 20) {
         epf = 20;
      }

      return CombatRules.getDamageAfterMagicAbsorb(dmg, epf);
   }

   private static double exposure(Vec3 source, AABB box, BlockPos ignoredBlock) {
      return exposure(source, box, ignoredBlock, false);
   }

   private static double exposure(Vec3 source, AABB box, BlockPos ignoredBlock, boolean solidOnly) {
      double sizeX = box.getXsize();
      double sizeY = box.getYsize();
      double sizeZ = box.getZsize();
      int samples = 2;
      int total = 0;
      int visible = 0;
      BiFunction<Vec3[], BlockPos, BlockHitResult> tester = (contextx, pos) -> {
         if (ignoredBlock != null && pos.equals(ignoredBlock)) {
            return null;
         } else {
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir() && (!solidOnly || state.isSolid())) {
               VoxelShape shape = state.getCollisionShape(mc.level, pos);
               return shape.isEmpty() ? null : shape.clip(contextx[0], contextx[1], pos);
            } else {
               return null;
            }
         }
      };

      for (int x = 0; x <= samples; x++) {
         for (int y = 0; y <= samples; y++) {
            for (int z = 0; z <= samples; z++) {
               Vec3 point = new Vec3(box.minX + sizeX * x / samples, box.minY + sizeY * y / samples, box.minZ + sizeZ * z / samples);
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
}
