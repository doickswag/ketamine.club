package we.ketamine.club.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.util.traits.Util;

public final class PistonCrystalCalculation implements Util {
   private PistonCrystalCalculation() {
   }

   public static PistonCrystalCalculation.PistonPlan calculateBestPlan(
      Player target,
      float minDamage,
      float maxLocalDamage,
      float placeRange,
      boolean autoBase,
      boolean airPlace,
      boolean breakCrystal,
      boolean safety,
      int extrapolationTicks
   ) {
      if (mc.level != null && mc.player != null && target != null) {
         Vec3 targetPosVec = target.position();
         if (extrapolationTicks > 0) {
            targetPosVec = targetPosVec.add(target.getDeltaMovement().scale(extrapolationTicks));
         }

         BlockPos targetBlockPos = BlockPos.containing(targetPosVec);
         List<PistonCrystalCalculation.PistonPlan> validPlans = new ArrayList<>();
         Direction playerFacing = mc.player.getDirection();
         List<Direction> directions = new ArrayList<>();
         directions.add(playerFacing);

         for (Direction d : Plane.HORIZONTAL) {
            if (d != playerFacing) {
               directions.add(d);
            }
         }

         for (int h = 0; h <= 2; h++) {
            BlockPos baseAtHeight = targetBlockPos.above(h);

            for (Direction direction : directions) {
               BlockPos crystalPos = baseAtHeight.relative(direction);
               if (canPlaceCrystal(mc.level, crystalPos, autoBase, airPlace, breakCrystal)) {
                  boolean needsBase = !isCrystalBase(mc.level, crystalPos.below());
                  if (!needsBase || autoBase || airPlace) {
                     BlockPos collinearPiston = crystalPos.relative(direction);
                     testPistonCandidate(
                        validPlans,
                        target,
                        collinearPiston,
                        direction.getOpposite(),
                        crystalPos,
                        needsBase,
                        minDamage,
                        maxLocalDamage,
                        placeRange,
                        airPlace,
                        safety
                     );
                     Direction clockWise = direction.getClockWise();
                     Direction counterClockWise = direction.getCounterClockWise();
                     BlockPos lateral1 = crystalPos.relative(clockWise);
                     testPistonCandidate(
                        validPlans, target, lateral1, clockWise.getOpposite(), crystalPos, needsBase, minDamage, maxLocalDamage, placeRange, airPlace, safety
                     );
                     BlockPos lateral2 = crystalPos.relative(counterClockWise);
                     testPistonCandidate(
                        validPlans,
                        target,
                        lateral2,
                        counterClockWise.getOpposite(),
                        crystalPos,
                        needsBase,
                        minDamage,
                        maxLocalDamage,
                        placeRange,
                        airPlace,
                        safety
                     );
                  }
               }
            }
         }

         return validPlans.stream().max((a, b) -> Float.compare(a.targetDamage, b.targetDamage)).orElse(null);
      } else {
         return null;
      }
   }

   private static void testPistonCandidate(
      List<PistonCrystalCalculation.PistonPlan> validPlans,
      Player target,
      BlockPos pistonPos,
      Direction pistonFacing,
      BlockPos crystalPos,
      boolean needsBase,
      float minDamage,
      float maxLocalDamage,
      float placeRange,
      boolean airPlace,
      boolean safety
   ) {
      if (canPlacePiston(mc.level, pistonPos, placeRange, airPlace)) {
         BlockPos redstonePos = findOptimalRedstonePos(mc.level, pistonPos, crystalPos);
         if (redstonePos != null) {
            Vec3 explosionPos = Vec3.atCenterOf(crystalPos);
            float targetDmg = calculateExplosionDamage(mc.level, explosionPos, target);
            float selfDmg = calculateExplosionDamage(mc.level, explosionPos, mc.player);
            if (targetDmg >= minDamage && (!safety || selfDmg <= maxLocalDamage)) {
               validPlans.add(new PistonCrystalCalculation.PistonPlan(pistonPos, pistonFacing, crystalPos, redstonePos, needsBase, targetDmg, selfDmg));
            }
         }
      }
   }

   public static boolean isCrystalBase(Level level, BlockPos basePos) {
      BlockState baseState = level.getBlockState(basePos);
      return baseState.is(Blocks.OBSIDIAN) || baseState.is(Blocks.BEDROCK);
   }

   public static boolean canPlaceCrystal(Level level, BlockPos pos, boolean autoBase, boolean airPlace, boolean breakCrystal) {
      BlockPos basePos = pos.below();
      BlockState baseState = level.getBlockState(basePos);
      boolean hasBase = isCrystalBase(level, basePos);
      if (!hasBase) {
         if (autoBase) {
            if (!baseState.canBeReplaced() && !baseState.isAir() && !airPlace) {
               return false;
            }
         } else if (!airPlace) {
            return false;
         }
      }

      if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) {
         return false;
      } else {
         AABB placementBox = new AABB(pos);
         List<Entity> entities = level.getEntities(null, placementBox);
         if (!entities.isEmpty()) {
            if (!breakCrystal) {
               return false;
            }

            for (Entity entity : entities) {
               if (!(entity instanceof EndCrystal)) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   public static boolean canPlacePiston(Level level, BlockPos pos, float placeRange, boolean airPlace) {
      if (level == null || mc.player == null) {
         return false;
      } else if (!level.getBlockState(pos).canBeReplaced()) {
         return false;
      } else {
         return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > placeRange ? false : airPlace || hasClickableNeighbour(level, pos);
      }
   }

   private static boolean hasClickableNeighbour(Level level, BlockPos pos) {
      for (Direction direction : Direction.values()) {
         BlockPos neighbour = pos.relative(direction);
         BlockState state = level.getBlockState(neighbour);
         if (!state.canBeReplaced() && !state.getCollisionShape(level, neighbour).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   public static BlockPos findOptimalRedstonePos(Level level, BlockPos pistonPos, BlockPos crystalPos) {
      for (Direction d : Direction.values()) {
         BlockPos candidate = pistonPos.relative(d);
         if (!candidate.equals(crystalPos) && level.getBlockState(candidate).canBeReplaced()) {
            return candidate;
         }
      }

      return null;
   }

   public static float calculateExplosionDamage(Level level, Vec3 explosionPos, Player entity) {
      double distance = entity.position().distanceTo(explosionPos);
      double power = 12.0;
      if (distance > power) {
         return 0.0F;
      } else {
         double impact = 1.0 - distance / power;
         if (impact <= 0.0) {
            return 0.0F;
         } else {
            float rawDamage = (float)((impact * impact + impact) / 2.0 * 7.0 * power + 1.0);
            switch (level.getDifficulty()) {
               case EASY:
                  rawDamage = Math.min(rawDamage / 2.0F + 1.0F, rawDamage);
                  break;
               case HARD:
                  rawDamage *= 1.5F;
            }

            float armor = (float)entity.getAttributeValue(Attributes.ARMOR);
            float toughness = (float)entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            float i = 2.0F + toughness / 4.0F;
            float j = Math.clamp(armor - rawDamage / i, armor * 0.2F, 20.0F);
            float damageWithArmor = rawDamage * (1.0F - j / 25.0F);
            int epf = 0;

            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
               ItemStack stack = entity.getItemBySlot(slot);
               if (!stack.isEmpty()) {
                  epf += EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
                  epf += 2 * EnchantmentUtil.getLevel(Enchantments.BLAST_PROTECTION, stack);
               }
            }

            if (epf > 20) {
               epf = 20;
            }

            return Math.max(0.0F, CombatRules.getDamageAfterMagicAbsorb(damageWithArmor, epf));
         }
      }
   }

   public static class PistonPlan {
      public final BlockPos pistonPos;
      public final Direction pistonFacing;
      public final BlockPos crystalPos;
      public final BlockPos redstonePos;
      public final boolean needsBase;
      public final float targetDamage;
      public final float selfDamage;

      public PistonPlan(
         BlockPos pistonPos, Direction pistonFacing, BlockPos crystalPos, BlockPos redstonePos, boolean needsBase, float targetDamage, float selfDamage
      ) {
         this.pistonPos = pistonPos;
         this.pistonFacing = pistonFacing;
         this.crystalPos = crystalPos;
         this.redstonePos = redstonePos;
         this.needsBase = needsBase;
         this.targetDamage = targetDamage;
         this.selfDamage = selfDamage;
      }
   }
}
