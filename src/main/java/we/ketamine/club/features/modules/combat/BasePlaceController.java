package we.ketamine.club.features.modules.combat;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.traits.Util;

final class BasePlaceController implements Util {
   private static final double UPGRADE_MULTIPLIER = 2.0;
   private final AutoCrystalModule crystal;
   private final BasePlaceState state = new BasePlaceState();
   private final Map<BlockPos, Double> damageCache = new HashMap<>();
   private BasePlaceController.Config config;
   private Level world;
   private Player self;
   private Player target;

   BasePlaceController(AutoCrystalModule crystal) {
      this.crystal = crystal;
   }

   void reset() {
      this.world = null;
      this.self = null;
      this.target = null;
      this.state.reset();
      this.damageCache.clear();
   }

   void tick(BasePlaceController.Config config) {
      this.config = config;
      if (mc.level != null && mc.player != null) {
         if (this.world != mc.level || this.self != mc.player) {
            this.reset();
            this.world = mc.level;
            this.self = mc.player;
         }

         if (!PingBypassInput.isScreenOpen() && mc.player.containerMenu.containerId == 0) {
            if (!config.pauseEat() || !mc.player.isUsingItem()) {
               long now = System.nanoTime() / 1000000L;
               BasePlaceState.Pending pending = this.state.pending();
               boolean confirmed = pending != null
                  && (
                     pending.obsidian()
                        ? mc.level.getBlockState(pending.pos()).is(Blocks.OBSIDIAN)
                        : mc.level.getBlockState(pending.pos()).is(Blocks.SPONGE) || mc.level.getBlockState(pending.pos()).is(Blocks.WET_SPONGE)
                  );
               if (!this.state.update(now, confirmed)) {
                  if (!this.crystal.isBasePlaceTarget(this.target)) {
                     this.target = this.closestTarget();
                     this.state.activeBase(null);
                  }

                  if (this.target != null) {
                     this.damageCache.clear();
                     BasePlaceController.Candidate bestDry = this.hasItem(Items.OBSIDIAN) ? this.findCandidate(false, false) : null;
                     BlockPos active = this.state.activeBase();
                     if (active != null) {
                        double currentDamage = this.damage(active);
                        if (this.isCrystalBase(active) && this.spotUsable(active, false, true) && currentDamage > 0.0) {
                           if (bestDry != null && bestDry.damage() >= currentDamage * 2.0) {
                              this.placeBase(bestDry.pos(), now);
                           }

                           return;
                        }

                        if (config.spongeMode()
                           && this.hasItem(Items.SPONGE)
                           && this.isCrystalBase(active)
                           && this.spotUsable(active, true, false)
                           && currentDamage > 0.0
                           && this.spongePosition(active) != null) {
                           this.placeSponge(active, now);
                           return;
                        }

                        this.state.activeBase(null);
                     }

                     BasePlaceController.Candidate existing = this.findCandidate(true, false);
                     if (existing != null) {
                        this.state.activeBase(existing.pos());
                     } else {
                        if (config.spongeMode() && this.hasItem(Items.SPONGE)) {
                           BasePlaceController.Candidate submerged = this.findCandidate(true, true);
                           if (submerged != null) {
                              this.state.activeBase(submerged.pos());
                              this.placeSponge(submerged.pos(), now);
                              return;
                           }
                        }

                        if (bestDry != null) {
                           this.placeBase(bestDry.pos(), now);
                        } else if (config.spongeMode() && this.hasItem(Items.SPONGE) && this.hasItem(Items.OBSIDIAN)) {
                           BasePlaceController.Candidate wet = this.findCandidate(false, true);
                           if (wet != null) {
                              this.placeBase(wet.pos(), now);
                           }
                        }
                     }
                  }
               }
            }
         }
      } else {
         this.reset();
      }
   }

   private BasePlaceController.Candidate findCandidate(boolean existing, boolean water) {
      int radius = Mth.ceil(this.crystal.basePlaceRange()) + 1;
      BlockPos center = BlockPos.containing(mc.player.getEyePosition());
      BasePlaceController.Candidate best = null;

      for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
         if (cursor.getY() < this.target.blockPosition().getY()
            && this.crystal.inBaseCrystalRange(cursor)
            && existing == this.isCrystalBase(cursor)
            && (existing || KetamineClub.placementManager.canPlaceBasePlace(cursor, this.crystal.basePlaceRange()))
            && this.spotUsable(cursor, water, existing && !water)
            && (!water || this.spongePosition(cursor) != null)) {
            double damage = this.damage(cursor);
            if (damage > 0.0 && (best == null || damage > best.damage())) {
               best = new BasePlaceController.Candidate(cursor.immutable(), damage);
            }
         }
      }

      return best;
   }

   private double damage(BlockPos base) {
      return this.damageCache.computeIfAbsent(base.immutable(), pos -> this.crystal.basePlaceDamage(pos, this.target, this.config.diffRatio()));
   }

   private boolean spotUsable(BlockPos base, boolean water, boolean allowOccupied) {
      BlockPos air = base.above();
      boolean wet = this.isWater(air) || this.isWater(air.above());
      if (water) {
         if (!wet || !this.airOrWater(air) || !this.airOrWater(air.above())) {
            return false;
         }
      } else if (!mc.level.getBlockState(air).isAir() || wet) {
         return false;
      }

      AABB placementBox = new AABB(air.getX(), air.getY(), air.getZ(), air.getX() + 1, air.getY() + 2, air.getZ() + 1);

      for (Entity entity : mc.level.getEntities(null, placementBox)) {
         if (!allowOccupied || !(entity instanceof EndCrystal crystalEntity) || !crystalEntity.isAlive() || !crystalEntity.blockPosition().equals(air)) {
            return false;
         }
      }

      return true;
   }

   private void placeBase(BlockPos base, long now) {
      if (this.state.ready(now, this.delayMs()) && !(this.damage(base) <= 0.0)) {
         this.placeBlock(base, Items.OBSIDIAN, now);
      }
   }

   private void placeSponge(BlockPos base, long now) {
      long delay = this.config.replaceDelay() == BasePlaceController.ReplaceDelayMode.PlaceDelay ? this.delayMs() : 0L;
      if (this.state.ready(now, delay) && !(this.damage(base) <= 0.0)) {
         BlockPos sponge = this.spongePosition(base);
         if (sponge != null) {
            this.placeBlock(sponge, Items.SPONGE, now);
         }
      }
   }

   private boolean placeBlock(BlockPos pos, Item item, long now) {
      Result result = InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE);
      if (!result.found()) {
         return false;
      } else if (!KetamineClub.placementManager.placeBasePlace(pos, result.slot(), this.crystal.basePlaceRange())) {
         return false;
      } else {
         this.state.sent(pos, item == Items.OBSIDIAN, now, this.crystal.basePlaceConfirmMs());
         return true;
      }
   }

   private BlockPos spongePosition(BlockPos base) {
      BlockPos best = null;
      double furthest = -1.0;

      for (Direction direction : Plane.HORIZONTAL) {
         BlockPos pos = base.relative(direction).above();
         if (KetamineClub.placementManager.canPlaceBasePlace(pos, this.crystal.basePlaceRange())) {
            double distance = this.target.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance > furthest) {
               furthest = distance;
               best = pos;
            }
         }
      }

      return best;
   }

   private Player closestTarget() {
      Player best = null;
      double closest = Double.MAX_VALUE;

      for (Player player : mc.level.players()) {
         if (this.crystal.isBasePlaceTarget(player)) {
            double distance = mc.player.distanceToSqr(player);
            if (distance < closest) {
               best = player;
               closest = distance;
            }
         }
      }

      return best;
   }

   private boolean hasItem(Item item) {
      return InventoryUtil.find(item, InventoryUtil.PLACE_SCOPE).found();
   }

   private long delayMs() {
      return Math.max(0L, Math.round(this.config.placeDelay() * 1000.0));
   }

   private boolean isWater(BlockPos pos) {
      return mc.level.getFluidState(pos).is(FluidTags.WATER);
   }

   private boolean airOrWater(BlockPos pos) {
      return mc.level.getBlockState(pos).isAir() || this.isWater(pos);
   }

   private boolean isCrystalBase(BlockPos pos) {
      BlockState block = mc.level.getBlockState(pos);
      return block.is(Blocks.OBSIDIAN) || block.is(Blocks.BEDROCK);
   }

   private record Candidate(BlockPos pos, double damage) {
   }

   record Config(double diffRatio, boolean pauseEat, double placeDelay, BasePlaceController.ReplaceDelayMode replaceDelay, boolean spongeMode) {
   }

   static enum ReplaceDelayMode {
      PlaceDelay,
      Automatic;

      private ReplaceDelayMode() {
      }
   }
}
