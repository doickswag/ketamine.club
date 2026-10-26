package we.ketamine.club.features.modules.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.modules.funny.DoohickeyTrapModule;
import we.ketamine.club.features.modules.world.SpeedMineController;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.PlacementManager;
import we.ketamine.club.util.DamageUtil;
import we.ketamine.club.util.InteractionUtil;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.render.RenderUtil;

public class AutoMineModule extends Module {
   private static final double INVALID_SCORE = -1000.0;
   private static final Direction[] HORIZONTAL = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
   private final Setting<Double> range = this.num("Range", 6.5, 0.0, 7.0);
   private final Setting<AutoMineModule.SortPriority> targetPriority = this.mode("TargetPriority", AutoMineModule.SortPriority.Angle);
   private final Setting<Boolean> ignoreNakeds = this.bool("IgnoreNakeds", true);
   private final Setting<AutoMineModule.ExtendBreakMode> extendBreakMode = this.mode("ExtendBreakMode", AutoMineModule.ExtendBreakMode.None);
   private final Setting<AutoMineModule.AntiSurroundMode> antiSurroundMode = this.mode("AntiSurroundMode", AutoMineModule.AntiSurroundMode.Auto);
   private final Setting<Boolean> antiSurroundInnerSnap = this.bool("AntiSurroundInnerSnap", true);
   private final Setting<Boolean> antiSurroundOuterSnap = this.bool("AntiSurroundOuterSnap", true);
   private final Setting<Boolean> avoidPistonCrystal = this.bool("AvoidPistonCrystal", true);
   private final Setting<Boolean> glassPush = this.bool("GlassPush", false);
   private final Setting<Integer> glassAttempts = this.num("GlassAttempts", 2, 1, 5);
   private final Setting<Boolean> comboScoring = this.bool("ComboScoring", false).setVisibility(value -> false).setPage("Synergy");
   private final Setting<Boolean> replacementPunish = this.bool("ReplacementPunish", false).setVisibility(value -> false).setPage("Synergy");
   private final Setting<Boolean> preRotate = this.bool("PreRotate", false).setVisibility(value -> false).setPage("Synergy");
   private final Setting<Boolean> surroundYield = this.bool("SurroundYield", false).setVisibility(value -> false).setPage("Synergy");
   private final Setting<Boolean> mineCrystalChain = this.bool("MineCrystalChain", false).setVisibility(value -> false).setPage("Synergy");
   private final Setting<Boolean> mineCrystalChainDebug = this.bool("MineCrystalChainDebug", false)
      .setVisibility(value -> this.mineCrystalChain.getValue())
      .setPage("Extra");
   private final Setting<Boolean> glassRender = this.bool("GlassRender", true).setPage("Render");
   private final Setting<Float> glassFadeTime = this.num("GlassFadeTime", 0.5F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> glassFillColor = this.color("GlassFillColor", 255, 255, 255, 50).setPage("Render");
   private final Setting<Color> glassOutlineColor = this.color("GlassOutlineColor", 255, 255, 255, 255).setPage("Render");
   private final Setting<Boolean> renderDebugScores = this.bool("RenderDebugScores", false).setPage("Render");
   private final Setting<Boolean> debugLog = this.bool("DebugLog", false).setPage("Extra");
   private SpeedMineController speedMine;
   private Player targetPlayer;
   private AutoMineModule.CityBlock target1;
   private AutoMineModule.CityBlock target2;
   private BlockPos ignorePos;
   private long lastOuterPlaceTime;
   private boolean crawlTargeting;
   private int crawlPhasedCells;
   private int crawlHitboxCells;
   private float crawlRebreakDamage;
   private String crawlDecision = "idle";
   private BlockPos finishingStandingDelayed;
   private BlockPos finishingStandingRebreak;
   private int lastDebugTick = -1;
   private BlockPos glassTargetPos;
   private int glassUsedAttempts;
   private BlockPos glassRenderPos;
   private long glassRenderStart;
   private BlockPos replacementPos;
   private boolean replacementWasSolid;
   private final SpeedMineController.MineFinishListener finishListener = this::onMineFinished;
   private final MineCrystalChainController mineCrystalController = new MineCrystalChainController(this);
   private final PlacementManager.PlacementListener mineCrystalBlockListener = new PlacementManager.PlacementListener() {
      {
         Objects.requireNonNull(AutoMineModule.this);
      }

      @Override
      public void onBlockUpdate(BlockPos pos, boolean nowAir) {
         AutoMineModule.this.mineCrystalController.onBlockUpdate(pos, nowAir);
      }

      @Override
      public void onExplosion(Vec3 center, float radius) {
         AutoMineModule.this.mineCrystalController.onExplosion(center, radius);
      }
   };

   public AutoMineModule() {
      super("AutoMine", "Automatically mines blocks. Requires SpeedMine to work.", Module.Category.COMBAT);
      this.antiSurroundInnerSnap
         .setVisibility(
            value -> this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Auto
               || this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Inner
         );
      this.antiSurroundOuterSnap
         .setVisibility(
            value -> this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Auto
               || this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Outer
         );
      this.glassAttempts.setVisibility(value -> this.glassPush.getValue());
      this.glassFadeTime.setVisibility(value -> this.glassRender.getValue());
      this.glassFillColor.setVisibility(value -> this.glassRender.getValue());
      this.glassOutlineColor.setVisibility(value -> this.glassRender.getValue());
   }

   @Override
   public void onEnable() {
      this.setSpeedMine(KetamineClub.moduleManager.getSpeedMine());
      KetamineClub.placementManager.addListener(this.mineCrystalBlockListener);
   }

   @Override
   public void onDisable() {
      if (this.speedMine != null) {
         this.speedMine.removeFinishListener(this.finishListener);
      }

      this.speedMine = null;
      KetamineClub.placementManager.removeListener(this.mineCrystalBlockListener);
      this.mineCrystalController.unbind();
      this.targetPlayer = null;
      this.target1 = null;
      this.target2 = null;
      this.ignorePos = null;
      this.crawlTargeting = false;
      this.crawlPhasedCells = 0;
      this.crawlHitboxCells = 0;
      this.crawlRebreakDamage = 0.0F;
      this.crawlDecision = "disabled";
      this.clearFinishingStandingMines();
      this.resetGlass();
      this.glassRenderPos = null;
      this.resetReplacementPunish();
   }

   private void onMineFinished(BlockPos minedPos) {
      if (!nullCheck() && this.targetPlayer != null && this.speedMine != null) {
         if (!this.mineCrystalChain.getValue() || !this.mineCrystalController.shouldSuppressLegacyFinish(minedPos)) {
            AutoMineModule.AntiSurroundMode mode = this.antiSurroundMode.getValue();
            AutoCrystalModule autoCrystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
            if (autoCrystal != null && autoCrystal.isEnabled()) {
               if (mode != AutoMineModule.AntiSurroundMode.None) {
                  if (mode == AutoMineModule.AntiSurroundMode.Auto || mode == AutoMineModule.AntiSurroundMode.Outer) {
                     boolean delayedDestroy = minedPos.equals(this.speedMine.getDelayedDestroyBlockPos());
                     boolean rebreak = minedPos.equals(this.speedMine.getRebreakBlockPos());
                     if (delayedDestroy && this.tryPlaceFurthestFace(autoCrystal, minedPos)) {
                        return;
                     }

                     if (rebreak && this.tryPlaceOpenRebreakFace(autoCrystal, minedPos)) {
                        return;
                     }
                  }

                  if (mode == AutoMineModule.AntiSurroundMode.Auto || mode == AutoMineModule.AntiSurroundMode.Inner) {
                     for (Direction direction : HORIZONTAL) {
                        BlockPos surroundPos = this.targetPlayer.blockPosition().relative(direction);
                        if (surroundPos.equals(minedPos)) {
                           autoCrystal.preplaceCrystal(surroundPos, this.antiSurroundInnerSnap.getValue());
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void update() {
      this.setSpeedMine(KetamineClub.moduleManager.getSpeedMine());
      if (this.speedMine != null && this.speedMine.isEnabled()) {
         Player previousTarget = this.targetPlayer;
         boolean wasCrawlTargeting = this.crawlTargeting;
         this.targetPlayer = this.selectTarget();
         if (this.targetPlayer == null) {
            this.crawlTargeting = false;
            this.crawlPhasedCells = 0;
            this.crawlHitboxCells = 0;
            this.crawlRebreakDamage = 0.0F;
            this.crawlDecision = "no_target";
            this.clearFinishingStandingMines();
            this.resetReplacementPunish();
         } else {
            this.updateReplacementPunish();
            this.handleGlassPush();
            boolean enteringCrawl = previousTarget == this.targetPlayer && !wasCrawlTargeting && this.targetPlayer.isVisuallyCrawling();
            if (enteringCrawl) {
               this.captureFinishingStandingMines();
            }

            if (!this.updateCrawlTargets()) {
               this.clearFinishingStandingMines();
               this.crawlTargeting = false;
               this.crawlPhasedCells = 0;
               this.crawlHitboxCells = 0;
               this.crawlRebreakDamage = 0.0F;
               this.crawlDecision = "standing_targets";
               if ((
                     this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Auto
                        || this.antiSurroundMode.getValue() == AutoMineModule.AntiSurroundMode.Outer
                  )
                  && this.speedMine.hasDelayedDestroy()) {
                  AutoCrystalModule autoCrystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
                  if (autoCrystal != null && autoCrystal.isEnabled()) {
                     this.tryPlaceFurthestFace(autoCrystal, this.speedMine.getDelayedDestroyBlockPos());
                  }
               }

               this.findTargetBlocks();
               this.requestSelectedTargets();
               this.prepareSelectedPlacements();
            } else if (this.waitingForStandingMines()) {
               this.crawlDecision = "finishing_standing_pair";
               this.prepareSelectedPlacements();
            } else {
               this.requestCrawlTargets();
               this.prepareSelectedPlacements();
            }
         }
      } else {
         this.resetReplacementPunish();
      }
   }

   private void setSpeedMine(SpeedMineController next) {
      if (this.speedMine != next) {
         if (this.speedMine != null) {
            this.speedMine.removeFinishListener(this.finishListener);
         }

         this.speedMine = next;
         if (this.speedMine != null) {
            this.speedMine.addFinishListener(this.finishListener);
         }

         this.mineCrystalController.bind(this.speedMine);
      }
   }

   @Subscribe(
      priority = 50
   )
   private void onMineCrystalPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         this.setSpeedMine(KetamineClub.moduleManager.getSpeedMine());
         this.mineCrystalController.onPreTick();
      }
   }

   private void captureFinishingStandingMines() {
      if (this.target1 != null && this.target2 != null && this.speedMine.hasDelayedDestroy() && this.speedMine.hasRebreakBlock()) {
         BlockPos delayed = this.speedMine.getDelayedDestroyBlockPos();
         BlockPos rebreak = this.speedMine.getRebreakBlockPos();
         boolean delayedSelected = this.target1.blockPos.equals(delayed) || this.target2.blockPos.equals(delayed);
         boolean rebreakSelected = this.target1.blockPos.equals(rebreak) || this.target2.blockPos.equals(rebreak);
         if (delayedSelected && rebreakSelected) {
            this.finishingStandingDelayed = delayed.immutable();
            this.finishingStandingRebreak = rebreak.immutable();
         }
      }
   }

   private boolean waitingForStandingMines() {
      if (this.finishingStandingDelayed != null && this.finishingStandingRebreak != null) {
         boolean delayedFinished = !this.finishingStandingDelayed.equals(this.speedMine.getDelayedDestroyBlockPos());
         boolean rebreakFinished = !this.finishingStandingRebreak.equals(this.speedMine.getRebreakBlockPos()) || this.speedMine.canRebreakRebreakBlock();
         if (delayedFinished && rebreakFinished) {
            this.clearFinishingStandingMines();
            return false;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private void clearFinishingStandingMines() {
      this.finishingStandingDelayed = null;
      this.finishingStandingRebreak = null;
   }

   private void requestSelectedTargets() {
      boolean targetingFeet = this.target1 != null && this.target1.isFeetBlock || this.target2 != null && this.target2.isFeetBlock;
      if (targetingFeet
         || (this.target1 == null || !this.target1.blockPos.equals(this.speedMine.getRebreakBlockPos()))
            && (this.target2 == null || !this.target2.blockPos.equals(this.speedMine.getRebreakBlockPos()))) {
         boolean bothInProgress = this.speedMine.hasDelayedDestroy() && this.speedMine.hasRebreakBlock() && !this.speedMine.canRebreakRebreakBlock();
         if (!bothInProgress) {
            Queue<BlockPos> targets = new LinkedList<>();
            if (this.target1 != null) {
               targets.add(this.target1.blockPos);
            }

            if (this.target2 != null) {
               targets.add(this.target2.blockPos);
            }

            if (!targets.isEmpty() && this.speedMine.hasDelayedDestroy()) {
               this.speedMine.silentBreakBlock(targets.remove(), 10.0);
            }

            if (!targets.isEmpty() && (!this.speedMine.hasRebreakBlock() || this.speedMine.canRebreakRebreakBlock())) {
               this.speedMine.silentBreakBlock(targets.remove(), 10.0);
            }
         }
      }
   }

   private void prepareSelectedPlacements() {
      if (this.preRotate.getValue() && this.speedMine != null) {
         AutoCrystalModule autoCrystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
         if (autoCrystal != null && autoCrystal.isEnabled()) {
            Set<BlockPos> nearlyReady = new HashSet<>();
            this.speedMine.collectMiningPositions(nearlyReady, 0.7);
            if (!this.prepareSelectedPlacement(autoCrystal, this.target1, nearlyReady)) {
               this.prepareSelectedPlacement(autoCrystal, this.target2, nearlyReady);
            }
         }
      }
   }

   private boolean prepareSelectedPlacement(AutoCrystalModule autoCrystal, AutoMineModule.CityBlock target, Set<BlockPos> nearlyReady) {
      if (target != null && nearlyReady.contains(target.blockPos)) {
         BlockState base = mc.level.getBlockState(target.blockPos.below());
         if (!base.is(Blocks.OBSIDIAN) && !base.is(Blocks.BEDROCK)) {
            return false;
         } else {
            autoCrystal.preparePlacement(target.blockPos);
            return true;
         }
      } else {
         return false;
      }
   }

   private void updateReplacementPunish() {
      if (this.replacementPunish.getValue() && !this.mineCrystalChain.getValue() && this.speedMine != null) {
         BlockPos current = this.speedMine.getRebreakBlockPos();
         if (current == null) {
            this.resetReplacementPunish();
         } else {
            boolean solid = !mc.level.getBlockState(current).isAir();
            if (!current.equals(this.replacementPos)) {
               this.replacementPos = current.immutable();
               this.replacementWasSolid = solid;
            } else {
               if (this.replacementWasSolid && !solid && this.canPlaceCrystalAt(current) && this.crystalIntersectsTarget(current)) {
                  AutoCrystalModule autoCrystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
                  if (autoCrystal != null && autoCrystal.isEnabled()) {
                     autoCrystal.preplaceCrystal(current, true);
                  }
               }

               this.replacementWasSolid = solid;
            }
         }
      } else {
         this.resetReplacementPunish();
      }
   }

   private void resetReplacementPunish() {
      this.replacementPos = null;
      this.replacementWasSolid = false;
   }

   private boolean updateCrawlTargets() {
      if (!this.targetPlayer.isVisuallyCrawling()) {
         return false;
      } else {
         List<BlockPos> phasedCells = this.phasedCrawlCells();
         this.crawlTargeting = true;
         Set<BlockPos> hitboxCells = this.crawlHitboxCells();
         this.crawlPhasedCells = phasedCells.size();
         this.crawlHitboxCells = hitboxCells.size();
         this.crawlRebreakDamage = 0.0F;
         List<BlockPos> validCells = new ArrayList<>();

         for (BlockPos pos : phasedCells) {
            BlockState state = mc.level.getBlockState(pos);
            if (this.canBreak(pos, state) && this.speedMine.inBreakRange(pos)) {
               validCells.add(pos);
            }
         }

         AutoMineModule.CrawlPair pair = this.selectCrawlPair(phasedCells, validCells, hitboxCells);
         this.target1 = pair == null ? null : cityBlock(pair.first, true);
         this.target2 = pair != null && pair.second != null ? cityBlock(pair.second, false) : null;
         this.ignorePos = this.target1 == null ? null : this.target1.blockPos;
         this.crawlDecision = pair == null
            ? "no_valid_target"
            : (phasedCells.isEmpty() ? "unphased_damage_pair" : (pair.second == null ? "phased_delayed_only" : "phased_yaw_damage_pair"));
         return true;
      }
   }

   private void requestCrawlTargets() {
      if (this.target1 != null) {
         BlockPos delayedTarget = this.target1.blockPos;
         BlockPos rebreakTarget = this.target2 == null ? null : this.target2.blockPos;
         BlockPos delayedPos = this.speedMine.getDelayedDestroyBlockPos();
         BlockPos rebreakPos = this.speedMine.getRebreakBlockPos();
         if (delayedPos != null && !delayedPos.equals(delayedTarget)) {
            if (!this.mineCrystalChain.getValue() || !this.mineCrystalController.isTracking(delayedPos)) {
               ;
            }
         } else {
            if (rebreakPos != null && (rebreakTarget == null || !rebreakPos.equals(rebreakTarget))) {
               if (this.mineCrystalChain.getValue() && this.mineCrystalController.isTracking(rebreakPos)) {
                  return;
               }

               if (!rebreakPos.equals(delayedTarget) || !this.speedMine.relaneRebreakToDelayed(rebreakPos)) {
                  this.speedMine.clearRebreak();
               }
            }

            this.speedMine.silentBreakBlock(delayedTarget, 100.0);
            if (rebreakTarget != null && delayedTarget.equals(this.speedMine.getDelayedDestroyBlockPos())) {
               this.speedMine.silentBreakBlock(rebreakTarget, 100.0);
            }
         }
      }
   }

   private List<BlockPos> phasedCrawlCells() {
      AABB playerBox = this.targetPlayer.getBoundingBox().deflate(1.0E-4);
      List<BlockPos> cells = new ArrayList<>();

      for (BlockPos raw : iterate(playerBox)) {
         BlockPos pos = raw.immutable();
         BlockState state = mc.level.getBlockState(pos);
         if (state.getCollisionShape(mc.level, pos)
            .toAabbs()
            .stream()
            .map(shape -> shape.move(pos.getX(), pos.getY(), pos.getZ()))
            .anyMatch(shape -> shape.intersects(playerBox))) {
            cells.add(pos);
         }
      }

      return cells;
   }

   private AutoMineModule.CrawlPair selectCrawlPair(List<BlockPos> phasedCells, List<BlockPos> validCells, Set<BlockPos> hitboxCells) {
      double yaw = Math.toRadians(this.targetPlayer.getYRot());
      double lookX = -Math.sin(yaw);
      double lookZ = Math.cos(yaw);
      if (phasedCells.isEmpty()) {
         return this.selectUnphasedPair(hitboxCells, lookX, lookZ);
      } else {
         Set<BlockPos> validSet = new HashSet<>(validCells);
         AutoMineModule.CrawlPair best = null;
         double bestYaw = -Double.MAX_VALUE;
         float bestDamage = -1.0F;

         for (BlockPos delayedCandidate : validCells) {
            double yawScore = this.facingScore(delayedCandidate, lookX, lookZ);
            BlockPos rebreakCandidate = null;
            float rebreakDamage = -1.0F;

            for (Direction direction : HORIZONTAL) {
               BlockPos adjacent = delayedCandidate.relative(direction);
               if (phasedCells.size() > 1
                  ? validSet.contains(adjacent) && this.validCrystalMine(adjacent)
                  : !hitboxCells.contains(adjacent) && this.validCrystalMine(adjacent)) {
                  float damage = this.crawlCrystalDamage(adjacent);
                  if (!(damage <= 1.0F) && !(damage <= rebreakDamage)) {
                     rebreakDamage = damage;
                     rebreakCandidate = adjacent;
                  }
               }
            }

            float candidateDamage = Math.max(rebreakDamage, 0.0F);
            if (yawScore > bestYaw + 1.0E-6 || Math.abs(yawScore - bestYaw) <= 1.0E-6 && candidateDamage > bestDamage) {
               bestYaw = yawScore;
               bestDamage = candidateDamage;
               best = new AutoMineModule.CrawlPair(delayedCandidate, rebreakCandidate);
            }
         }

         this.crawlRebreakDamage = Math.max(bestDamage, 0.0F);
         return best;
      }
   }

   private Set<BlockPos> crawlHitboxCells() {
      AABB box = this.targetPlayer.getBoundingBox().deflate(1.0E-4);
      Set<BlockPos> cells = new HashSet<>();

      for (BlockPos pos : iterate(box)) {
         cells.add(pos.immutable());
      }

      return cells;
   }

   private AutoMineModule.CrawlPair selectUnphasedPair(Set<BlockPos> hitboxCells, double lookX, double lookZ) {
      if (hitboxCells.isEmpty()) {
         return null;
      } else {
         int minX = hitboxCells.stream().mapToInt(Vec3i::getX).min().orElse(0) - 2;
         int maxX = hitboxCells.stream().mapToInt(Vec3i::getX).max().orElse(0) + 2;
         int minZ = hitboxCells.stream().mapToInt(Vec3i::getZ).min().orElse(0) - 2;
         int maxZ = hitboxCells.stream().mapToInt(Vec3i::getZ).max().orElse(0) + 2;
         int y = Mth.floor(this.targetPlayer.getBoundingBox().minY);
         List<BlockPos> candidates = new ArrayList<>();

         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               BlockPos candidate = new BlockPos(x, y, z);
               if (!hitboxCells.contains(candidate) && this.validCrystalMine(candidate)) {
                  candidates.add(candidate);
               }
            }
         }

         AutoMineModule.CrawlPair best = null;
         float bestCombinedDamage = -1.0F;
         double bestYaw = -Double.MAX_VALUE;

         for (int firstIndex = 0; firstIndex < candidates.size(); firstIndex++) {
            BlockPos first = candidates.get(firstIndex);

            for (int secondIndex = firstIndex + 1; secondIndex < candidates.size(); secondIndex++) {
               BlockPos second = candidates.get(secondIndex);
               if (cardinallyAdjacent(first, second)) {
                  float firstDamage = this.crawlCrystalDamage(first);
                  float secondDamage = this.crawlCrystalDamage(second);
                  if (!(firstDamage <= 1.0F) && !(secondDamage <= 1.0F)) {
                     BlockPos delayed = this.facingScore(first, lookX, lookZ) >= this.facingScore(second, lookX, lookZ) ? first : second;
                     BlockPos rebreak = delayed.equals(first) ? second : first;
                     float combinedDamage = firstDamage + secondDamage;
                     double yawScore = this.facingScore(delayed, lookX, lookZ);
                     if (combinedDamage > bestCombinedDamage + 1.0E-4F || Math.abs(combinedDamage - bestCombinedDamage) <= 1.0E-4F && yawScore > bestYaw) {
                        bestCombinedDamage = combinedDamage;
                        bestYaw = yawScore;
                        this.crawlRebreakDamage = rebreak.equals(first) ? firstDamage : secondDamage;
                        best = new AutoMineModule.CrawlPair(delayed, rebreak);
                     }
                  }
               }
            }
         }

         return best;
      }
   }

   private boolean validCrystalMine(BlockPos pos) {
      BlockState state = mc.level.getBlockState(pos);
      BlockState base = mc.level.getBlockState(pos.below());
      return this.canBreak(pos, state)
         && this.speedMine.inBreakRange(pos)
         && (base.is(Blocks.OBSIDIAN) || base.is(Blocks.BEDROCK))
         && this.crystalIntersectsTarget(pos);
   }

   private static boolean cardinallyAdjacent(BlockPos first, BlockPos second) {
      return first.getY() == second.getY() && Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ()) == 1;
   }

   private double facingScore(BlockPos pos, double lookX, double lookZ) {
      Vec3 offset = Vec3.atCenterOf(pos).subtract(this.targetPlayer.position());
      return offset.x * lookX + offset.z * lookZ;
   }

   private boolean crystalIntersectsTarget(BlockPos airPos) {
      double centerX = airPos.getX() + 0.5;
      double centerZ = airPos.getZ() + 0.5;
      AABB crystalBox = new AABB(centerX - 1.0, airPos.getY(), centerZ - 1.0, centerX + 1.0, airPos.getY() + 2.0, centerZ + 1.0);
      return crystalBox.intersects(this.targetPlayer.getBoundingBox());
   }

   private float crawlCrystalDamage(BlockPos airPos) {
      Vec3 explosionPos = new Vec3(airPos.getX() + 0.5, airPos.getY(), airPos.getZ() + 0.5);
      return DamageUtil.crystalDamage(this.targetPlayer, explosionPos, airPos);
   }

   private Player selectTarget() {
      TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
      Player best = null;
      double bestMetric = Double.MAX_VALUE;
      AABB area = mc.player.getBoundingBox().inflate(this.range.getValue());

      for (Entity entity : mc.level.getEntities(mc.player, area)) {
         if (entity instanceof Player player
            && player.isAlive()
            && !player.isDeadOrDying()
            && !player.isCreative()
            && !player.isSpectator()
            && (targets == null || targets.isValidTarget(player))
            && !(player.position().distanceTo(mc.player.getEyePosition()) > this.range.getValue())
            && (!this.ignoreNakeds.getValue() || !this.isNaked(player))) {
            double metric = this.targetPriority.getValue() == AutoMineModule.SortPriority.Distance ? mc.player.distanceToSqr(player) : this.angleTo(player);
            if (metric < bestMetric) {
               bestMetric = metric;
               best = player;
            }
         }
      }

      return best;
   }

   private static AutoMineModule.CityBlock cityBlock(BlockPos pos, boolean feet) {
      AutoMineModule.CityBlock block = new AutoMineModule.CityBlock();
      block.blockPos = pos;
      block.isFeetBlock = feet;
      return block;
   }

   private boolean tryPlaceFurthestFace(AutoCrystalModule autoCrystal, BlockPos minedPos) {
      if (minedPos != null && System.currentTimeMillis() - this.lastOuterPlaceTime >= 75L) {
         BlockPos best = null;
         double bestDistance = -1.0;

         for (Direction direction : HORIZONTAL) {
            BlockPos candidate = minedPos.relative(direction);
            if (this.canPlaceCrystalAt(candidate)) {
               double distance = this.targetPlayer.distanceToSqr(Vec3.atCenterOf(candidate));
               if (distance > bestDistance) {
                  bestDistance = distance;
                  best = candidate;
               }
            }
         }

         return this.placeOuter(autoCrystal, best);
      } else {
         return false;
      }
   }

   private boolean tryPlaceOpenRebreakFace(AutoCrystalModule autoCrystal, BlockPos minedPos) {
      if (minedPos != null && System.currentTimeMillis() - this.lastOuterPlaceTime >= 75L) {
         Set<BlockPos> targetFeet = new HashSet<>();

         for (BlockPos pos : iterate(this.targetFeetBox())) {
            targetFeet.add(pos.immutable());
         }

         BlockPos openFace = null;
         double bestDistance = -1.0;

         for (Direction direction : HORIZONTAL) {
            BlockPos face = minedPos.relative(direction);
            if (!targetFeet.contains(face)) {
               if (mc.level.getBlockState(face).is(Blocks.OBSIDIAN)) {
                  return false;
               }

               if (this.canPlaceCrystalAt(face)) {
                  double distance = this.targetPlayer.distanceToSqr(Vec3.atCenterOf(face));
                  if (distance > bestDistance) {
                     bestDistance = distance;
                     openFace = face;
                  }
               }
            }
         }

         return this.placeOuter(autoCrystal, openFace);
      } else {
         return false;
      }
   }

   private boolean canPlaceCrystalAt(BlockPos pos) {
      BlockState state = mc.level.getBlockState(pos);
      BlockState base = mc.level.getBlockState(pos.below());
      return (state.isAir() || state.canBeReplaced()) && (base.is(Blocks.OBSIDIAN) || base.is(Blocks.BEDROCK));
   }

   private boolean placeOuter(AutoCrystalModule autoCrystal, BlockPos pos) {
      if (pos != null && autoCrystal.preplaceCrystal(pos, this.antiSurroundOuterSnap.getValue())) {
         this.lastOuterPlaceTime = System.currentTimeMillis();
         return true;
      } else {
         return false;
      }
   }

   private void handleGlassPush() {
      if (!this.glassPush.getValue()) {
         this.resetGlass();
      } else {
         AutoCrystalModule autoCrystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
         if (autoCrystal != null && autoCrystal.isEnabled() && this.speedMine.canRebreakRebreakBlock()) {
            BlockPos pos = this.speedMine.getRebreakBlockPos();
            if (pos == null) {
               this.resetGlass();
            } else {
               AABB feetBox = this.targetFeetBox();
               boolean bedrock = BlockPos.betweenClosedStream(feetBox).anyMatch(blockPos -> mc.level.getBlockState(blockPos).is(Blocks.BEDROCK));
               if (this.isGoodRebreak(pos, bedrock, feetBox) && autoCrystal.isDesirablePlacement(pos)) {
                  if (!pos.equals(this.glassTargetPos)) {
                     this.glassTargetPos = pos.immutable();
                     this.glassUsedAttempts = 0;
                  }

                  if (!this.itemInCrystalSpot(pos)) {
                     this.glassUsedAttempts = 0;
                  } else if (this.glassUsedAttempts < this.glassAttempts.getValue()) {
                     if (mc.level.getBlockState(pos).isAir()) {
                        if (this.placeGlass(pos)) {
                           this.glassUsedAttempts++;
                           this.speedMine.holdRebreak(pos, 5);
                           this.glassRenderPos = pos.immutable();
                           this.glassRenderStart = System.currentTimeMillis();
                        }
                     }
                  }
               } else {
                  this.resetGlass();
               }
            }
         } else {
            this.resetGlass();
         }
      }
   }

   private boolean itemInCrystalSpot(BlockPos airPos) {
      AABB box = new AABB(airPos).expandTowards(0.0, 1.0, 0.0);

      for (Entity entity : mc.level.getEntities((Entity)null, box)) {
         if (entity instanceof ItemEntity) {
            return true;
         }
      }

      return false;
   }

   private boolean placeGlass(BlockPos pos) {
      Result glass = InventoryUtil.find(Items.GLASS, InventoryUtil.PLACE_SCOPE);
      if (glass.found() && PlaceUtil.canPlace(pos)) {
         if (!KetamineClub.placementManager.enqueue(pos, glass.slot())) {
            return false;
         } else {
            KetamineClub.placementManager.flushQueue();
            return true;
         }
      } else {
         return false;
      }
   }

   private void resetGlass() {
      this.glassTargetPos = null;
      this.glassUsedAttempts = 0;
   }

   private void renderGlass(Render3DEvent event) {
      if (this.glassRender.getValue() && this.glassRenderPos != null) {
         long age = System.currentTimeMillis() - this.glassRenderStart;
         double fadeMs = this.glassFadeTime.getValue().floatValue() * 1000.0;
         if (age > fadeMs) {
            this.glassRenderPos = null;
         } else {
            double progress = age / fadeMs;
            Color fill = this.glassFillColor.getValue();
            Color outline = this.glassOutlineColor.getValue();
            RenderUtil.drawBoxFilled(event.getMatrix(), this.glassRenderPos, fadeColor(fill, progress));
            RenderUtil.drawBox(event.getMatrix(), this.glassRenderPos, fadeColor(outline, progress), 1.5F);
         }
      }
   }

   private static Color fadeColor(Color color, double progress) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(color.getAlpha() * (1.0 - progress)));
   }

   private double angleTo(Player player) {
      float[] angle = MathUtil.calcAngle(mc.player.getEyePosition(), player.getEyePosition());
      double yaw = MathUtil.wrapDegrees(angle[0] - mc.player.getYRot());
      double pitch = angle[1] - mc.player.getXRot();
      return Math.sqrt(yaw * yaw + pitch * pitch);
   }

   private boolean isNaked(Player player) {
      return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
         && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
         && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
         && player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
   }

   private void findTargetBlocks() {
      this.target1 = this.findCityBlock(null);
      this.ignorePos = this.target1 == null ? null : this.target1.blockPos;
      this.target2 = this.findCityBlock(this.target1 == null ? null : this.target1.blockPos);
   }

   private AutoMineModule.CityBlock findCityBlock(BlockPos exclude) {
      if (this.targetPlayer == null) {
         return null;
      } else {
         AABB feetBox = this.targetFeetBox();
         boolean bedrock = BlockPos.betweenClosedStream(feetBox).anyMatch(posx -> mc.level.getBlockState(posx).is(Blocks.BEDROCK));
         Set<AutoMineModule.CheckPos> candidates = new HashSet<>();
         if (bedrock) {
            this.addBedrockCaseCheckPositions(candidates);
         } else {
            this.addNormalCaseCheckPositions(candidates);
         }

         AutoMineModule.CityBlock best = new AutoMineModule.CityBlock();
         boolean found = false;

         for (AutoMineModule.CheckPos candidate : candidates) {
            BlockPos pos = candidate.blockPos;
            if (!pos.equals(exclude)) {
               BlockState state = mc.level.getBlockState(pos);
               boolean goodRebreak = this.isGoodRebreak(pos, bedrock, feetBox);
               if ((!state.isAir() || goodRebreak) && (this.canBreak(pos, state) || goodRebreak) && this.speedMine.inBreakRange(pos)) {
                  double score = bedrock ? this.scoreBedrockCityBlock(candidate, feetBox) : this.scoreNormalCityBlock(candidate);
                  if (score != -1000.0) {
                     if (goodRebreak) {
                        score += 40.0;
                     }

                     if (score > best.score) {
                        best.score = score;
                        best.blockPos = pos;
                        best.isFeetBlock = this.isBlockInFeet(pos);
                        found = true;
                     }
                  }
               }
            }
         }

         return found ? best : null;
      }
   }

   private boolean isGoodRebreak(BlockPos pos, boolean bedrock, AABB feetBox) {
      if (!this.speedMine.canRebreakRebreakBlock() || !pos.equals(this.speedMine.getRebreakBlockPos())) {
         return false;
      } else if (!bedrock) {
         if (!pos.equals(this.targetPlayer.blockPosition()) && !this.isBlockInFeet(pos)) {
            for (Direction direction : HORIZONTAL) {
               BlockPos surround = this.targetPlayer.blockPosition().relative(direction);
               if (surround.equals(pos) && this.isCrystalBlock(surround.below())) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         boolean selfTrap = false;

         for (Direction directionx : HORIZONTAL) {
            if (this.targetPlayer.blockPosition().above().relative(directionx).equals(pos)) {
               selfTrap = true;
               break;
            }
         }

         boolean canFacePlace = mc.level.getBlockState(this.targetPlayer.blockPosition().above()).isAir();
         return BlockPos.betweenClosedStream(feetBox).count() == 1L && (pos.equals(this.targetPlayer.blockPosition().above(2)) || selfTrap && canFacePlace);
      }
   }

   private void addNormalCaseCheckPositions(Set<AutoMineModule.CheckPos> candidates) {
      AABB feetBox = this.targetFeetBox();

      for (BlockPos pos : iterate(feetBox)) {
         candidates.add(new AutoMineModule.CheckPos(pos.immutable(), AutoMineModule.CheckPosType.Feet));
      }

      for (BlockPos pos : iterate(feetBox)) {
         for (Direction direction : HORIZONTAL) {
            candidates.add(new AutoMineModule.CheckPos(pos.relative(direction), AutoMineModule.CheckPosType.Surround));
         }
      }

      candidates.add(new AutoMineModule.CheckPos(this.targetPlayer.blockPosition(), AutoMineModule.CheckPosType.Feet));
      if (BlockPos.betweenClosedStream(feetBox).count() == 1L) {
         for (Direction direction : HORIZONTAL) {
            switch ((AutoMineModule.ExtendBreakMode)this.extendBreakMode.getValue()) {
               case None:
               default:
                  break;
               case Long:
                  candidates.add(new AutoMineModule.CheckPos(this.targetPlayer.blockPosition().relative(direction, 2), AutoMineModule.CheckPosType.Extend));
                  break;
               case Corner:
                  candidates.add(
                     new AutoMineModule.CheckPos(
                        this.targetPlayer.blockPosition().relative(direction).relative(this.getCornerPerpDir(direction)), AutoMineModule.CheckPosType.Extend
                     )
                  );
            }
         }
      }
   }

   private void addBedrockCaseCheckPositions(Set<AutoMineModule.CheckPos> candidates) {
      AABB feetBox = this.targetFeetBox();
      boolean canFall = BlockPos.betweenClosedStream(feetBox).allMatch(posx -> !mc.level.getBlockState(posx.below()).is(Blocks.BEDROCK));
      boolean canRise = BlockPos.betweenClosedStream(feetBox).allMatch(posx -> !mc.level.getBlockState(posx.above(2)).is(Blocks.BEDROCK));

      for (BlockPos raw : iterate(feetBox)) {
         BlockPos pos = raw.immutable();
         if (canFall) {
            candidates.add(new AutoMineModule.CheckPos(pos.below(), AutoMineModule.CheckPosType.Below));
         }

         if (canRise) {
            candidates.add(new AutoMineModule.CheckPos(pos.above(2), AutoMineModule.CheckPosType.Head));
         }

         candidates.add(new AutoMineModule.CheckPos(pos.above(), AutoMineModule.CheckPosType.FacePlace));

         for (Direction direction : HORIZONTAL) {
            candidates.add(new AutoMineModule.CheckPos(pos.above().relative(direction), AutoMineModule.CheckPosType.FacePlace));
         }

         candidates.add(new AutoMineModule.CheckPos(pos, AutoMineModule.CheckPosType.Surround));

         for (Direction direction : HORIZONTAL) {
            candidates.add(new AutoMineModule.CheckPos(pos.relative(direction), AutoMineModule.CheckPosType.Surround));
         }
      }
   }

   private double scoreNormalCityBlock(AutoMineModule.CheckPos candidate) {
      BlockPos pos = candidate.blockPos;
      BlockState state = mc.level.getBlockState(pos);
      double score = 0.0;
      if (pos.equals(this.targetPlayer.blockPosition())) {
         if (mc.level.getBlockState(pos.above()).is(Blocks.OBSIDIAN)) {
            score += 100.0;
         } else {
            if (state.is(Blocks.COBWEB)) {
               return -1000.0;
            }

            score += 50.0;
         }
      } else {
         BlockState selfHead = mc.level.getBlockState(mc.player.blockPosition().above());
         if (pos.equals(mc.player.blockPosition()) && (selfHead.is(Blocks.OBSIDIAN) || selfHead.is(Blocks.BEDROCK))) {
            return -1000.0;
         }

         if (candidate.type == AutoMineModule.CheckPosType.Surround) {
            score += 3.0;

            for (Direction direction : HORIZONTAL) {
               if (this.targetPlayer.blockPosition().relative(direction).equals(pos)) {
                  BlockPos straight = this.targetPlayer.blockPosition().relative(direction, 2);
                  BlockPos corner = this.targetPlayer.blockPosition().relative(direction).relative(this.getCornerPerpDir(direction));
                  if (this.getBlockStateIgnore(straight).isAir() && this.isCrystalBlock(straight.below())
                     || this.getBlockStateIgnore(corner).isAir() && this.isCrystalBlock(corner.below())) {
                     score += 25.0;
                     break;
                  }
               }
            }
         }

         if (candidate.type == AutoMineModule.CheckPosType.Extend) {
            score += 20.0;
         }
      }

      return score + 10.0 / this.targetPlayer.position().distanceTo(Vec3.atCenterOf(pos)) + this.comboFollowupScore(pos);
   }

   private double scoreBedrockCityBlock(AutoMineModule.CheckPos candidate, AABB feetBox) {
      BlockPos pos = candidate.blockPos;
      double score = 0.0;
      if (pos.getY() == this.targetPlayer.getBlockY() + 2 || pos.getY() == this.targetPlayer.getBlockY() - 1) {
         score += 10.0;
      }

      if (BlockPos.betweenClosedStream(feetBox).count() == 1L && !mc.level.getBlockState(this.targetPlayer.blockPosition().above()).is(Blocks.BEDROCK)) {
         if (pos.equals(this.targetPlayer.blockPosition().above())) {
            score += 20.0;
         } else {
            for (Direction direction : HORIZONTAL) {
               if (this.targetPlayer.blockPosition().above().relative(direction).equals(pos)) {
                  score += 7.5;
                  break;
               }
            }
         }
      }

      return score + 10.0 / this.targetPlayer.position().distanceTo(Vec3.atCenterOf(pos)) + this.comboFollowupScore(pos);
   }

   private double comboFollowupScore(BlockPos minedPos) {
      if (this.comboScoring.getValue() && this.targetPlayer != null) {
         float bestDamage = this.potentialCrystalDamage(minedPos, true);

         for (Direction direction : HORIZONTAL) {
            bestDamage = Math.max(bestDamage, this.potentialCrystalDamage(minedPos.relative(direction), false));
         }

         double score = bestDamage * 5.0;
         float health = this.targetPlayer.getHealth() + this.targetPlayer.getAbsorptionAmount();
         if (bestDamage >= health) {
            score += 200.0;
         }

         return score;
      } else {
         return 0.0;
      }
   }

   private float potentialCrystalDamage(BlockPos airPos, boolean minedCell) {
      BlockState base = mc.level.getBlockState(airPos.below());
      if (!base.is(Blocks.OBSIDIAN) && !base.is(Blocks.BEDROCK)) {
         return 0.0F;
      } else {
         if (!minedCell) {
            BlockState state = mc.level.getBlockState(airPos);
            if (!state.isAir() && !state.canBeReplaced()) {
               return 0.0F;
            }
         }

         Vec3 explosionPos = new Vec3(airPos.getX() + 0.5, airPos.getY(), airPos.getZ() + 0.5);
         return DamageUtil.crystalDamage(this.targetPlayer, explosionPos, minedCell ? airPos : null);
      }
   }

   private AABB targetFeetBox() {
      AABB bounds = this.targetPlayer.getBoundingBox().deflate(0.01, 0.1, 0.01);
      double feetY = this.targetPlayer.getY();
      return new AABB(bounds.minX, feetY, bounds.minZ, bounds.maxX, feetY + 0.1, bounds.maxZ);
   }

   private boolean isBlockInFeet(BlockPos blockPos) {
      for (BlockPos pos : iterate(this.targetFeetBox())) {
         if (blockPos.equals(pos)) {
            return true;
         }
      }

      return false;
   }

   private boolean isCrystalBlock(BlockPos pos) {
      BlockState state = mc.level.getBlockState(pos);
      return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
   }

   private BlockState getBlockStateIgnore(BlockPos pos) {
      return pos != null && !pos.equals(this.ignorePos) ? mc.level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
   }

   private Direction getCornerPerpDir(Direction direction) {
      return switch (direction) {
         case NORTH -> Direction.EAST;
         case SOUTH -> Direction.WEST;
         case EAST -> Direction.NORTH;
         case WEST -> Direction.SOUTH;
         default -> Direction.NORTH;
      };
   }

   private boolean canBreak(BlockPos pos, BlockState state) {
      return !InteractionUtil.canBreak(pos, state) ? false : !this.isOwnPistonCrystalBlock(pos);
   }

   private boolean isOwnPistonCrystalBlock(BlockPos pos) {
      if (!this.avoidPistonCrystal.getValue()) {
         return false;
      } else {
         PistonCrystalModule pistonCrystal = KetamineClub.moduleManager.getModuleByClass(PistonCrystalModule.class);
         if (pistonCrystal != null && pistonCrystal.isOwnPistonBlock(pos)) {
            return true;
         } else {
            PistonPushModule pistonPush = KetamineClub.moduleManager.getModuleByClass(PistonPushModule.class);
            if (pistonPush != null && pistonPush.isOwnPistonBlock(pos)) {
               return true;
            } else {
               DoohickeyTrapModule doohickeyTrap = KetamineClub.moduleManager.getModuleByClass(DoohickeyTrapModule.class);
               return doohickeyTrap != null && doohickeyTrap.isOwnPistonBlock(pos);
            }
         }
      }
   }

   private static Iterable<BlockPos> iterate(AABB box) {
      return BlockPos.betweenClosed(
         Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ)
      );
   }

   public boolean isTargetedPos(BlockPos pos) {
      return this.target1 != null && this.target1.blockPos.equals(pos) || this.target2 != null && this.target2.blockPos.equals(pos);
   }

   public boolean isTargetingAnything() {
      return this.target1 != null || this.target2 != null;
   }

   public Player getTargetPlayer() {
      return this.targetPlayer;
   }

   boolean isMineCrystalChainEnabled() {
      return this.mineCrystalChain.getValue();
   }

   boolean isMineCrystalChainDebugEnabled() {
      return this.mineCrystalChainDebug.getValue();
   }

   boolean ownsMineChainPosition(BlockPos pos) {
      return pos == null
         ? false
         : this.target1 != null && this.target1.blockPos.equals(pos)
            || this.target2 != null && this.target2.blockPos.equals(pos)
            || this.finishingStandingDelayed != null && this.finishingStandingDelayed.equals(pos)
            || this.finishingStandingRebreak != null && this.finishingStandingRebreak.equals(pos)
            || this.mineCrystalController.isTracking(pos);
   }

   public boolean onMineChainCrystalAdded(EndCrystal crystal) {
      return this.mineCrystalController.onCrystalAdded(crystal);
   }

   public boolean isMineChainCrystalProtected(EndCrystal crystal) {
      return this.mineCrystalController.isProtected(crystal);
   }

   boolean shouldClaimMineCrystalUpdate(BlockPos pos) {
      return this.isEnabled() && this.mineCrystalChain.getValue() && this.mineCrystalController.isTracking(pos);
   }

   public boolean shouldYieldSurround() {
      if (this.surroundYield.getValue() && !this.isDisabled() && this.speedMine != null && this.speedMine.isEnabled()) {
         Set<BlockPos> nearlyReady = new HashSet<>();
         this.speedMine.collectMiningPositions(nearlyReady, 0.7);

         for (BlockPos pos : nearlyReady) {
            if (this.isTargetedPos(pos)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public BlockPos getReadyRebreakPoint() {
      if (!this.isDisabled() && this.speedMine != null && this.speedMine.isEnabled() && this.speedMine.canRebreakRebreakBlock()) {
         BlockPos rebreak = this.speedMine.getRebreakBlockPos();
         if (rebreak == null) {
            return null;
         } else {
            return (this.target1 == null || !this.target1.blockPos.equals(rebreak))
                  && (this.target2 == null || !this.target2.blockPos.equals(rebreak))
                  && (this.finishingStandingRebreak == null || !this.finishingStandingRebreak.equals(rebreak))
               ? null
               : rebreak.immutable();
         }
      } else {
         return null;
      }
   }

   @Override
   public void onTick() {
      if (!nullCheck()) {
         this.update();
         this.logDebugState();
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck()) {
         this.renderGlass(event);
         if (this.renderDebugScores.getValue() && this.targetPlayer != null && this.speedMine != null) {
            if (this.crawlTargeting) {
               if (this.target1 != null) {
                  RenderUtil.drawBoxFilled(event.getMatrix(), this.target1.blockPos, new Color(255, 0, 0, 110));
                  RenderUtil.drawBox(event.getMatrix(), this.target1.blockPos, new Color(255, 255, 255, 220), 1.5F);
               }

               if (this.target2 != null) {
                  RenderUtil.drawBoxFilled(event.getMatrix(), this.target2.blockPos, new Color(255, 0, 0, 70));
                  RenderUtil.drawBox(event.getMatrix(), this.target2.blockPos, new Color(255, 255, 255, 180), 1.0F);
               }
            } else {
               AABB feetBox = this.targetFeetBox();
               boolean bedrock = BlockPos.betweenClosedStream(feetBox).anyMatch(pos -> mc.level.getBlockState(pos).is(Blocks.BEDROCK));
               Set<AutoMineModule.CheckPos> candidates = new HashSet<>();
               if (bedrock) {
                  this.addBedrockCaseCheckPositions(candidates);
               } else {
                  this.addNormalCaseCheckPositions(candidates);
               }

               double bestScore = 0.0;

               for (AutoMineModule.CheckPos candidate : candidates) {
                  AutoMineModule.CityBlock scored = this.scoreDebug(candidate, bedrock, feetBox);
                  if (scored != null) {
                     bestScore = Math.max(bestScore, scored.score);
                  }
               }

               if (!(bestScore <= 0.0)) {
                  for (AutoMineModule.CheckPos candidatex : candidates) {
                     AutoMineModule.CityBlock scored = this.scoreDebug(candidatex, bedrock, feetBox);
                     if (scored != null) {
                        int alpha = (int)(255.0 * (scored.score / bestScore) / 4.0);
                        RenderUtil.drawBoxFilled(event.getMatrix(), candidatex.blockPos, new Color(255, 0, 0, Mth.clamp(alpha, 0, 255)));
                     }
                  }
               }
            }
         }
      }
   }

   private void logDebugState() {
      if (this.debugLog.getValue() && mc.player.tickCount != this.lastDebugTick) {
         this.lastDebugTick = mc.player.tickCount;
         if (this.targetPlayer == null) {
            KetamineClub.LOGGER
               .info(
                  "[AutoMine] tick={} target=none decision=no_target active={}",
                  mc.player.tickCount,
                  this.speedMine == null ? "unavailable" : this.speedMine.getDebugState()
               );
         } else {
            KetamineClub.LOGGER
               .info(
                  "[AutoMine] tick={} target={} pos={} yaw={} pose={} crawl={} crawlMode={} phase={}/{} decision={} selected[delayed={},rebreak={}] rebreakDamage={} active={}",
                  new Object[]{
                     mc.player.tickCount,
                     this.targetPlayer.getName().getString(),
                     this.targetPlayer.position(),
                     String.format(Locale.ROOT, "%.1f", this.targetPlayer.getYRot()),
                     this.targetPlayer.getPose(),
                     this.targetPlayer.isVisuallyCrawling(),
                     this.crawlTargeting,
                     this.crawlPhasedCells,
                     this.crawlHitboxCells,
                     this.crawlDecision,
                     this.target1 == null ? "none" : this.target1.blockPos,
                     this.target2 == null ? "none" : this.target2.blockPos,
                     String.format(Locale.ROOT, "%.2f", this.crawlRebreakDamage),
                     this.speedMine == null ? "unavailable" : this.speedMine.getDebugState()
                  }
               );
         }
      }
   }

   private AutoMineModule.CityBlock scoreDebug(AutoMineModule.CheckPos candidate, boolean bedrock, AABB feetBox) {
      BlockState state = mc.level.getBlockState(candidate.blockPos);
      boolean goodRebreak = this.isGoodRebreak(candidate.blockPos, bedrock, feetBox);
      if (state.isAir() && !goodRebreak) {
         return null;
      } else if (!this.canBreak(candidate.blockPos, state) && !goodRebreak) {
         return null;
      } else if (!this.speedMine.inBreakRange(candidate.blockPos)) {
         return null;
      } else {
         double score = bedrock ? this.scoreBedrockCityBlock(candidate, feetBox) : this.scoreNormalCityBlock(candidate);
         if (score == -1000.0) {
            return null;
         } else {
            AutoMineModule.CityBlock result = new AutoMineModule.CityBlock();
            result.score = score + (goodRebreak ? 40 : 0);
            return result;
         }
      }
   }

   @Override
   public String getDisplayInfo() {
      return this.targetPlayer == null ? null : this.targetPlayer.getName().getString();
   }

   private static enum AntiSurroundMode {
      None,
      Inner,
      Outer,
      Auto;

      private AntiSurroundMode() {
      }
   }

   private record CheckPos(BlockPos blockPos, AutoMineModule.CheckPosType type) {
      @Override
      public int hashCode() {
         return this.blockPos.hashCode();
      }

      @Override
      public boolean equals(Object object) {
         return object instanceof AutoMineModule.CheckPos other && this.blockPos.equals(other.blockPos);
      }
   }

   private static enum CheckPosType {
      Feet,
      Surround,
      Extend,
      FacePlace,
      Head,
      Below;

      private CheckPosType() {
      }
   }

   private static final class CityBlock {
      private BlockPos blockPos;
      private double score;
      private boolean isFeetBlock;

      private CityBlock() {
      }
   }

   private record CrawlPair(BlockPos first, BlockPos second) {
   }

   private static enum ExtendBreakMode {
      None,
      Long,
      Corner;

      private ExtendBreakMode() {
      }
   }

   private static enum SortPriority {
      Distance,
      Angle;

      private SortPriority() {
      }
   }
}
