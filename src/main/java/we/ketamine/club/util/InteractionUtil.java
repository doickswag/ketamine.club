package we.ketamine.club.util;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import we.ketamine.club.util.traits.Util;

public class InteractionUtil implements Util {
   public InteractionUtil() {
   }

   public static boolean canBreak(BlockPos blockPos, BlockState state) {
      return !mc.player.isCreative() && state.getDestroySpeed(mc.level, blockPos) < 0.0F ? false : state.getShape(mc.level, blockPos) != Shapes.empty();
   }

   public static boolean isPlaceable(BlockPos pos, boolean entityCheck) {
      if (!mc.level.getBlockState(pos).canBeReplaced()) {
         return false;
      } else {
         Iterator var2 = mc.level
            .getEntitiesOfClass(
               Entity.class, new AABB(pos), ex -> !(ex instanceof ThrownExperienceBottle) && !(ex instanceof ItemEntity) && !(ex instanceof ExperienceOrb)
            )
            .iterator();
         if (var2.hasNext()) {
            Entity e = (Entity)var2.next();
            return e instanceof Player ? false : !entityCheck;
         } else {
            return true;
         }
      }
   }

   public static boolean breakBlock(BlockPos pos) {
      if (!canBreak(pos, mc.level.getBlockState(pos))) {
         return false;
      } else {
         BlockPos bp = pos instanceof MutableBlockPos ? new BlockPos(pos) : pos;
         mc.getConnection().send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, bp, Direction.UP));
         mc.player.swing(InteractionHand.MAIN_HAND);
         mc.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, bp, Direction.UP));
         mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
         return true;
      }
   }

   public static void useItem(BlockPos pos) {
      useItem(pos, InteractionHand.MAIN_HAND);
   }

   public static void useItem(BlockPos pos, InteractionHand hand) {
      if (mc.level != null && mc.player != null && mc.gameMode != null) {
         Direction direction = mc.hitResult != null ? ((BlockHitResult)mc.hitResult).getDirection() : Direction.DOWN;
         if (mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(Vec3.atCenterOf(pos), direction, pos, false)) instanceof Success success
            && success.swingSource() != SwingSource.NONE) {
            mc.player.connection.send(new ServerboundSwingPacket(hand));
         }
      }
   }

   public static boolean place(BlockPos pos, boolean airPlace) {
      return place(pos, airPlace, InteractionHand.MAIN_HAND);
   }

   public static boolean place(BlockPos pos, boolean airPlace, InteractionHand hand) {
      if (mc.level == null || mc.player == null || mc.gameMode == null) {
         return false;
      } else if (!isPlaceable(pos, false)) {
         return false;
      } else {
         Direction direction = calcSide(pos);
         if (direction == null) {
            if (!airPlace) {
               return false;
            }

            direction = mc.hitResult != null ? ((BlockHitResult)mc.hitResult).getDirection() : Direction.DOWN;
         }

         BlockPos bp = airPlace ? pos : pos.relative(direction);
         if (mc.gameMode
               .useItemOn(
                  mc.player,
                  hand,
                  new BlockHitResult(
                     airPlace ? Vec3.atCenterOf(pos) : Vec3.atCenterOf(bp).relative(direction.getOpposite(), 0.5),
                     airPlace ? direction : direction.getOpposite(),
                     bp,
                     false
                  )
               ) instanceof Success success
            && success.swingSource() != SwingSource.NONE) {
            mc.player.connection.send(new ServerboundSwingPacket(hand));
         }

         return true;
      }
   }

   public static Direction calcSide(BlockPos pos) {
      for (Direction d : Direction.values()) {
         if (!mc.level.getBlockState(pos.offset(d.getUnitVec3i())).canBeReplaced()) {
            return d;
         }
      }

      return null;
   }

   public static double getBlockBreakingSpeed(int slot, BlockPos pos) {
      return getBlockBreakingSpeed(slot, mc.level.getBlockState(pos));
   }

   public static double getBlockBreakingSpeed(int slot, BlockState block) {
      double speed = rawMiningSpeed(slot, block, mc.player.onGround());
      float hardness = block.getDestroySpeed(null, null);
      if (hardness == -1.0F) {
         return 0.0;
      } else {
         speed /= hardness
            / (
               block.requiresCorrectToolForDrops() && !((ItemStack)mc.player.getInventory().getNonEquipmentItems().get(slot)).isCorrectToolForDrops(block)
                  ? 100
                  : 30
            );
         float ticks = (float)(Math.floor(1.0 / speed) + 1.0);
         return (long)(ticks / 20.0F * 1000.0F);
      }
   }

   public static double rawMiningSpeed(int slot, BlockState block, boolean onGround) {
      double speed = ((ItemStack)mc.player.getInventory().getNonEquipmentItems().get(slot)).getDestroySpeed(block);
      if (speed > 1.0) {
         ItemStack tool = mc.player.getInventory().getItem(slot);
         int efficiency = EnchantmentUtil.getLevel(Enchantments.EFFICIENCY, tool);
         if (efficiency > 0 && !tool.isEmpty()) {
            speed += efficiency * efficiency + 1;
         }
      }

      if (MobEffectUtil.hasDigSpeed(mc.player)) {
         speed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(mc.player) + 1) * 0.2F;
      }

      if (mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
         float k = switch (mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
         speed *= k;
      }

      if (mc.player.isEyeInFluid(FluidTags.WATER) && EnchantmentUtil.has(Enchantments.AQUA_AFFINITY, EquipmentSlot.HEAD)) {
         speed /= 5.0;
      }

      if (!onGround) {
         speed /= 5.0;
      }

      return speed;
   }

   public static double breakDelta(double speed, BlockState block) {
      float hardness = block.getDestroySpeed(null, null);
      return hardness == -1.0F ? 0.0 : speed / hardness / 30.0;
   }

   public static int fastestToolSlot(BlockState block) {
      int best = mc.player.getInventory().getSelectedSlot();
      double bestSpeed = -1.0;

      for (int i = 0; i < 9; i++) {
         double speed = rawMiningSpeed(i, block, true);
         if (speed > bestSpeed) {
            bestSpeed = speed;
            best = i;
         }
      }

      return best;
   }

   public static float getBreakDelta(ItemStack stack, BlockState state, BlockPos pos) {
      float hardness = state.getDestroySpeed(mc.level, pos);
      if (hardness == -1.0F) {
         return 0.0F;
      } else {
         float speed = stack.getDestroySpeed(state);
         if (speed > 1.0F) {
            int efficiency = EnchantmentUtil.getLevel(Enchantments.EFFICIENCY, stack);
            if (efficiency > 0 && !stack.isEmpty()) {
               speed += efficiency * efficiency + 1;
            }
         }

         if (MobEffectUtil.hasDigSpeed(mc.player)) {
            speed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(mc.player) + 1) * 0.2F;
         }

         if (mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
            speed *= switch (mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
               case 0 -> 0.3F;
               case 1 -> 0.09F;
               case 2 -> 0.0027F;
               default -> 8.1E-4F;
            };
         }

         if (mc.player.isEyeInFluid(FluidTags.WATER) && !EnchantmentUtil.has(Enchantments.AQUA_AFFINITY, EquipmentSlot.HEAD)) {
            speed /= 5.0F;
         }

         if (!mc.player.onGround()) {
            speed /= 5.0F;
         }

         boolean harvestable = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
         return speed / hardness / (harvestable ? 30.0F : 100.0F);
      }
   }

   public static Direction right(Direction direction) {
      return switch (direction) {
         case EAST -> Direction.SOUTH;
         case SOUTH -> Direction.WEST;
         case WEST -> Direction.NORTH;
         case NORTH -> Direction.EAST;
         default -> throw new IllegalStateException("Unexpected value: " + direction);
      };
   }

   public static Direction left(Direction direction) {
      return switch (direction) {
         case EAST -> Direction.NORTH;
         case SOUTH -> Direction.EAST;
         case WEST -> Direction.SOUTH;
         case NORTH -> Direction.WEST;
         default -> throw new IllegalStateException("Unexpected value: " + direction);
      };
   }
}
