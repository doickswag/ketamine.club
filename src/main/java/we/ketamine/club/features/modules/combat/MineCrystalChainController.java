package we.ketamine.club.features.modules.combat;

import java.util.Locale;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.world.SpeedMineController;
import we.ketamine.club.util.traits.Util;

final class MineCrystalChainController implements SpeedMineController.MineChainHandler, Util {
   private static final int MAX_TOP_SENDS = 2;
   private static final int MAX_ACTION_FAILURES = 3;
   private static final int MAX_TIMING_BIAS = 3;
   private final AutoMineModule owner;
   private final MineCrystalChainController.LaneState delayed = new MineCrystalChainController.LaneState(SpeedMineController.MineLane.DELAYED);
   private final MineCrystalChainController.LaneState rebreak = new MineCrystalChainController.LaneState(SpeedMineController.MineLane.REBREAK);
   private SpeedMineController speedMine;
   private AutoCrystalModule autoCrystal;
   private long nextCycleId;
   private int delayedTimingBias = 1;
   private int timingSamples;

   MineCrystalChainController(AutoMineModule owner) {
      this.owner = owner;
   }

   void bind(SpeedMineController next) {
      if (this.speedMine != next) {
         if (this.speedMine != null) {
            this.speedMine.clearMineChainHandler(this);
         }

         this.speedMine = next;
         if (this.speedMine != null) {
            this.speedMine.setMineChainHandler(this);
         }

         this.clearLanes();
      }
   }

   void unbind() {
      this.bind(null);
      this.autoCrystal = null;
   }

   void onPreTick() {
      this.autoCrystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
      if (!this.active()) {
         this.clearLanes();
      } else if (!this.canOperate()) {
         this.clearLanes();
      } else {
         this.sync(this.delayed, this.speedMine.getDelayedLaneState());
         this.sync(this.rebreak, this.speedMine.getRebreakLaneState());
         this.maintain(this.delayed);
         this.maintain(this.rebreak);
      }
   }

   void onBlockUpdate(BlockPos pos, boolean nowAir) {
      if (this.active() && pos != null && this.owner.ownsMineChainPosition(pos)) {
         this.updateBlock(this.delayed, pos, nowAir);
         this.updateBlock(this.rebreak, pos, nowAir);
      }
   }

   void onExplosion(Vec3 center, float radius) {
      if (this.active() && center != null && !(radius <= 0.0F)) {
         this.markExplosion(this.delayed, center, radius);
         this.markExplosion(this.rebreak, center, radius);
      }
   }

   boolean onCrystalAdded(EndCrystal crystal) {
      if (!this.active() || crystal == null) {
         return false;
      } else {
         return this.captureCrystal(this.delayed, crystal) ? true : this.captureCrystal(this.rebreak, crystal);
      }
   }

   boolean isProtected(EndCrystal crystal) {
      return this.active() && crystal != null ? this.protects(this.delayed, crystal) || this.protects(this.rebreak, crystal) : false;
   }

   boolean isTracking(BlockPos pos) {
      return pos != null && (this.isTracking(this.delayed, pos) || this.isTracking(this.rebreak, pos));
   }

   @Override
   public boolean shouldHoldPosition(BlockPos requested) {
      return this.active() && requested != null ? this.holdsLane(this.delayed, requested) || this.holdsLane(this.rebreak, requested) : false;
   }

   private boolean holdsLane(MineCrystalChainController.LaneState lane, BlockPos requested) {
      return this.canOperate()
         && lane.pos != null
         && lane.phase != MineCrystalChainController.Phase.IDLE
         && lane.phase != MineCrystalChainController.Phase.FAILED
         && !lane.pos.equals(requested);
   }

   private boolean isTracking(MineCrystalChainController.LaneState lane, BlockPos pos) {
      return lane.pos != null
         && lane.pos.equals(pos)
         && lane.phase != MineCrystalChainController.Phase.IDLE
         && lane.phase != MineCrystalChainController.Phase.FAILED;
   }

   boolean shouldSuppressLegacyFinish(BlockPos pos) {
      return this.active() && pos != null ? this.suppressFinish(this.delayed, pos) || this.suppressFinish(this.rebreak, pos) : false;
   }

   private boolean suppressFinish(MineCrystalChainController.LaneState lane, BlockPos pos) {
      return pos.equals(lane.pos) && lane.phase != MineCrystalChainController.Phase.IDLE && lane.phase != MineCrystalChainController.Phase.FAILED
         ? lane.phase == MineCrystalChainController.Phase.WAIT_TOP
            || lane.phase == MineCrystalChainController.Phase.TOP_LIVE
            || lane.phase == MineCrystalChainController.Phase.WAIT_INNER
            || lane.phase == MineCrystalChainController.Phase.WAIT_AIR
         : false;
   }

   @Override
   public void onDelayedTick(SpeedMineController.MineLaneState snapshot) {
      if (this.active() && snapshot != null && this.owner.ownsMineChainPosition(snapshot.pos())) {
         if (this.autoCrystal != null && this.autoCrystal.isEnabled()) {
            this.sync(this.delayed, snapshot);
            this.predictDelayed(this.delayed, snapshot);
         }
      }
   }

   @Override
   public boolean shouldHandleRebreak(SpeedMineController.MineLaneState snapshot) {
      return this.active() && snapshot != null && this.owner.ownsMineChainPosition(snapshot.pos())
         ? snapshot.seenAir() || this.rebreak.airObserved || this.rebreak.replacementPending
         : false;
   }

   @Override
   public boolean requiresRebreakToolSwap(SpeedMineController.MineLaneState snapshot) {
      return !this.shouldHandleRebreak(snapshot)
         ? false
         : this.rebreak.phase == MineCrystalChainController.Phase.TOP_LIVE
            || this.rebreak.phase == MineCrystalChainController.Phase.ARMING
            || this.rebreak.phase == MineCrystalChainController.Phase.WAIT_AIR
            || this.rebreak.phase == MineCrystalChainController.Phase.FAILED;
   }

   @Override
   public boolean onRebreak(SpeedMineController.MineLaneState snapshot, int toolSlot, Runnable stopAction) {
      if (this.active() && snapshot != null && this.owner.ownsMineChainPosition(snapshot.pos())) {
         this.sync(this.rebreak, snapshot);
         if (this.rebreak.pos == null || mc.level.getBlockState(this.rebreak.pos).isAir()) {
            return false;
         } else if (!snapshot.seenAir() && !this.rebreak.airObserved && !this.rebreak.replacementPending) {
            return false;
         } else {
            if (!this.rebreak.lastSolid) {
               this.restartReplacement(this.rebreak, snapshot, "REPLACED");
            }

            this.rebreak.lastSolid = true;
            if (this.rebreak.phase == MineCrystalChainController.Phase.TOP_LIVE && this.canOperate()) {
               return this.execute(this.rebreak, toolSlot, stopAction) ? true : this.rebreak.actionFailures < 3;
            } else if (this.rebreak.phase == MineCrystalChainController.Phase.WAIT_INNER) {
               return true;
            } else if (this.rebreak.phase == MineCrystalChainController.Phase.INNER_CONFIRMED) {
               return true;
            } else if (this.rebreak.phase == MineCrystalChainController.Phase.WAIT_AIR) {
               return this.boundedStop(this.rebreak, stopAction);
            } else {
               return this.rebreak.phase != MineCrystalChainController.Phase.FAILED
                     && this.rebreak.phase != MineCrystalChainController.Phase.ARMING
                     && this.canOperate()
                  ? this.rebreak.phase == MineCrystalChainController.Phase.WAIT_TOP
                  : this.boundedStop(this.rebreak, stopAction);
            }
         }
      } else {
         return false;
      }
   }

   private boolean active() {
      return this.owner.isEnabled()
         && this.owner.isMineCrystalChainEnabled()
         && this.speedMine != null
         && this.speedMine.isEnabled()
         && this.autoCrystal != null
         && this.autoCrystal.isEnabled()
         && mc.player != null
         && mc.level != null;
   }

   private boolean canOperate() {
      return this.autoCrystal != null && this.autoCrystal.isEnabled() && this.autoCrystal.canMineCrystalChain();
   }

   private void sync(MineCrystalChainController.LaneState lane, SpeedMineController.MineLaneState snapshot) {
      if (snapshot != null && this.owner.ownsMineChainPosition(snapshot.pos())) {
         boolean positionChanged = lane.pos == null || !lane.pos.equals(snapshot.pos());
         if (positionChanged) {
            this.beginEpoch(lane, snapshot, "ASSIGN");
         }

         lane.snapshot = snapshot;
         if (snapshot.seenAir()) {
            boolean localAir = mc.level.getBlockState(lane.pos).isAir();
            if (localAir) {
               this.observeAir(lane);
            } else if (!lane.replacementPending && !lane.airObserved) {
               this.recordAirEvidence(lane);
            }

            if (lane.role == SpeedMineController.MineLane.REBREAK && !localAir) {
               lane.replacementPending = true;
            }
         }

         if (snapshot.expectedBreakTick() != Integer.MAX_VALUE) {
            lane.predictedClientTick = snapshot.clientTick() + snapshot.expectedBreakTick() - snapshot.elapsedTicks();
         }

         boolean solid = !mc.level.getBlockState(lane.pos).isAir();
         if (!solid && lane.explosionPendingUntil >= this.currentTick()) {
            lane.explosionPendingUntil = 0;
            this.observeAir(lane);
         }

         if (solid && lane.explosionPendingUntil < this.currentTick()) {
            lane.explosionPendingUntil = 0;
         }

         if (!solid && lane.lastSolid && !positionChanged) {
            this.observeAir(lane);
         }

         if (solid && !lane.lastSolid && !positionChanged) {
            this.transitionToSolid(lane, snapshot, "SOLID_TRANSITION");
         }

         lane.lastSolid = solid;
      } else {
         lane.clear();
      }
   }

   private void transitionToSolid(MineCrystalChainController.LaneState lane, SpeedMineController.MineLaneState snapshot, String reason) {
      boolean hadAir = lane.airObserved || lane.replacementPending;
      boolean keepTransaction = lane.role == SpeedMineController.MineLane.REBREAK
         && (lane.phase == MineCrystalChainController.Phase.WAIT_INNER || lane.phase == MineCrystalChainController.Phase.INNER_CONFIRMED);
      if (keepTransaction) {
         lane.replacementPending |= hadAir;
         lane.airObserved = false;
         if (hadAir && this.speedMine != null) {
            this.speedMine.resetRebreakStopBudget(lane.pos);
         }
      } else {
         this.beginEpoch(lane, snapshot, reason);
         lane.replacementPending = hadAir;
         lane.airObserved = false;
         if (hadAir && this.speedMine != null) {
            this.speedMine.resetRebreakStopBudget(lane.pos);
         }
      }
   }

   private void restartReplacement(MineCrystalChainController.LaneState lane, SpeedMineController.MineLaneState snapshot, String reason) {
      boolean observed = lane.airObserved || lane.replacementPending;
      this.beginEpoch(lane, snapshot, reason);
      lane.replacementPending = observed;
      if (observed && lane.role == SpeedMineController.MineLane.REBREAK && this.speedMine != null) {
         this.speedMine.resetRebreakStopBudget(lane.pos);
      }
   }

   private void maintain(MineCrystalChainController.LaneState lane) {
      if (lane.pos != null) {
         EndCrystal top = this.findCrystal(lane.topPos());
         if (top != null
            && lane.topCrystalId < 0
            && lane.topSends > 0
            && (lane.phase == MineCrystalChainController.Phase.ARMING || lane.phase == MineCrystalChainController.Phase.WAIT_TOP)) {
            this.captureTop(lane, top);
         }

         boolean air = mc.level.getBlockState(lane.pos).isAir();
         if (air) {
            lane.lastSolid = false;
            this.handleAir(lane);
         } else {
            if (lane.phase == MineCrystalChainController.Phase.INNER_CONFIRMED && lane.role == SpeedMineController.MineLane.REBREAK) {
               this.restartReplacement(lane, lane.snapshot, "NEXT_REPLACEMENT");
            }

            if (lane.replacementPending
               && lane.role == SpeedMineController.MineLane.REBREAK
               && (lane.phase == MineCrystalChainController.Phase.WAIT_INNER || lane.phase == MineCrystalChainController.Phase.INNER_CONFIRMED)) {
               this.restartReplacement(lane, lane.snapshot, "REPLACEMENT_OBSERVED");
            }

            if (lane.phase == MineCrystalChainController.Phase.ARMING || lane.phase == MineCrystalChainController.Phase.WAIT_TOP) {
               this.autoCrystal.prepareMineCrystalChain(lane.pos);
               this.armTop(lane);
            }

            if (lane.phase == MineCrystalChainController.Phase.TOP_LIVE) {
               this.autoCrystal.prepareMineCrystalChain(lane.pos);
               if (lane.role == SpeedMineController.MineLane.DELAYED) {
                  this.predictDelayed(lane, lane.snapshot);
               }
            }

            if (lane.phase == MineCrystalChainController.Phase.WAIT_INNER && this.currentTick() > lane.deadlineTick) {
               this.confirmOrRetry(lane);
            }
         }
      }
   }

   private void predictDelayed(MineCrystalChainController.LaneState lane, SpeedMineController.MineLaneState snapshot) {
      if (lane.role == SpeedMineController.MineLane.DELAYED && lane.phase == MineCrystalChainController.Phase.TOP_LIVE && snapshot != null && this.canOperate()
         )
       {
         if (snapshot.expectedBreakTick() != Integer.MAX_VALUE) {
            int fireTick = snapshot.expectedBreakTick() + this.delayedTimingBias;
            if (snapshot.elapsedTicks() >= fireTick) {
               this.trace(
                  lane,
                  "PREDICT_WINDOW",
                  false,
                  "elapsed=" + snapshot.elapsedTicks() + " expected=" + snapshot.expectedBreakTick() + " bias=" + this.delayedTimingBias
               );
               this.execute(lane, -1, null);
            }
         }
      }
   }

   private void armTop(MineCrystalChainController.LaneState lane) {
      if (this.canOperate() && lane.pos != null) {
         BlockState base = mc.level.getBlockState(lane.pos);
         if (!base.is(Blocks.OBSIDIAN) && !base.is(Blocks.BEDROCK)) {
            this.fail(lane, "BASE_INVALID");
         } else {
            int tick = this.currentTick();
            if (lane.phase != MineCrystalChainController.Phase.WAIT_TOP || tick > lane.deadlineTick) {
               if (lane.phase == MineCrystalChainController.Phase.WAIT_TOP) {
                  if (lane.topSends >= 2) {
                     this.fail(lane, "TOP_TIMEOUT");
                     return;
                  }

                  lane.phase = MineCrystalChainController.Phase.ARMING;
               }

               if (lane.armDeadlineTick == 0) {
                  lane.armDeadlineTick = tick + this.operationTimeoutTicks();
               }

               if (lane.topSends < 2 && tick <= lane.armDeadlineTick) {
                  if (this.autoCrystal.preplaceMineCrystalChain(lane.topPos())) {
                     lane.topSends++;
                     lane.phase = MineCrystalChainController.Phase.WAIT_TOP;
                     lane.deadlineTick = tick + this.operationTimeoutTicks();
                     this.trace(lane, "TOP_PLACE_SENT", true, this.timingDetail(lane) + " items=" + this.blockingItems(lane.pos));
                  }
               } else {
                  this.fail(lane, "ARM_TIMEOUT");
               }
            }
         }
      }
   }

   private void handleAir(MineCrystalChainController.LaneState lane) {
      if (lane.phase == MineCrystalChainController.Phase.TOP_LIVE && this.canOperate()) {
         this.execute(lane, -1, null);
      } else if (lane.phase == MineCrystalChainController.Phase.WAIT_AIR) {
         if (lane.topCrystalId >= 0 && this.canOperate()) {
            this.execute(lane, -1, null);
         } else {
            this.retryInner(lane);
         }
      } else if ((lane.phase == MineCrystalChainController.Phase.ARMING || lane.phase == MineCrystalChainController.Phase.WAIT_TOP)
         && this.currentTick() > lane.deadlineTick) {
         this.retryInner(lane);
      } else {
         if (lane.phase == MineCrystalChainController.Phase.WAIT_INNER && this.currentTick() > lane.deadlineTick) {
            this.confirmOrRetry(lane);
         }
      }
   }

   private boolean execute(MineCrystalChainController.LaneState lane, int toolSlot, Runnable stopAction) {
      if (lane.topCrystalId >= 0 && lane.attempts < 2 && this.canOperate()) {
         boolean sent = this.autoCrystal
            .executeMineCrystalChain(lane.topCrystalId, lane.pos, toolSlot, stopAction, event -> this.trace(lane, event, true, this.timingDetail(lane)));
         if (!sent) {
            lane.actionFailures++;
            if (lane.actionFailures >= 3) {
               this.fail(lane, "TRANSACTION_REJECTED");
            }

            return false;
         } else {
            lane.attempts++;
            lane.actionFailures = 0;
            if (lane.role == SpeedMineController.MineLane.REBREAK) {
               if (this.speedMine != null) {
                  this.speedMine.markRebreakAir(lane.pos);
               }

               lane.airObserved = true;
               lane.replacementPending = false;
            }

            lane.phase = MineCrystalChainController.Phase.WAIT_INNER;
            lane.deadlineTick = this.currentTick() + this.operationTimeoutTicks();
            this.trace(lane, "INNER_CONFIRM_WAIT", false, this.timingDetail(lane) + " items=" + this.blockingItems(lane.pos));
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean boundedStop(MineCrystalChainController.LaneState lane, Runnable stopAction) {
      int tick = this.currentTick();
      if (lane.phase == MineCrystalChainController.Phase.WAIT_AIR && tick <= lane.deadlineTick) {
         return true;
      } else if (lane.fallbackStops < 2 && stopAction != null) {
         this.trace(lane, "STOP_SENT", true, "fallback=true");
         stopAction.run();
         lane.fallbackStops++;
         lane.phase = MineCrystalChainController.Phase.WAIT_AIR;
         lane.deadlineTick = tick + this.operationTimeoutTicks();
         return true;
      } else {
         return false;
      }
   }

   private void confirmOrRetry(MineCrystalChainController.LaneState lane) {
      EndCrystal inner = this.findCrystal(lane.pos);
      if (inner != null) {
         this.confirmInner(lane, inner);
      } else {
         if (lane.role == SpeedMineController.MineLane.DELAYED && lane.attempts > 0) {
            this.delayedTimingBias = Math.min(3, this.delayedTimingBias + 1);
            this.trace(lane, "TIMING_LATE_ADJUST", false, "bias=" + this.delayedTimingBias);
         }

         this.retryInner(lane);
      }
   }

   private void retryInner(MineCrystalChainController.LaneState lane) {
      if (lane.attempts < 2 && this.canOperate() && mc.level.getBlockState(lane.pos).isAir()) {
         if (!this.autoCrystal.retryMineCrystalInner(lane.pos, event -> this.trace(lane, event, true, "fallback=true"))) {
            lane.actionFailures++;
            if (lane.actionFailures >= 3) {
               this.fail(lane, "FALLBACK_REJECTED");
            } else {
               lane.deadlineTick = this.currentTick() + 1;
            }
         } else {
            lane.attempts++;
            lane.actionFailures = 0;
            lane.phase = MineCrystalChainController.Phase.WAIT_INNER;
            lane.deadlineTick = this.currentTick() + this.operationTimeoutTicks();
         }
      } else {
         this.fail(lane, "INNER_TIMEOUT");
      }
   }

   private boolean captureCrystal(MineCrystalChainController.LaneState lane, EndCrystal crystal) {
      if (lane.pos != null && this.owner.ownsMineChainPosition(lane.pos)) {
         BlockPos crystalPos = crystal.blockPosition();
         if (lane.topSends <= 0
            || !crystalPos.equals(lane.topPos())
            || lane.phase != MineCrystalChainController.Phase.ARMING
               && lane.phase != MineCrystalChainController.Phase.WAIT_TOP
               && lane.phase != MineCrystalChainController.Phase.TOP_LIVE
               && lane.phase != MineCrystalChainController.Phase.WAIT_AIR) {
            if (crystalPos.equals(lane.pos) && lane.phase == MineCrystalChainController.Phase.WAIT_INNER) {
               this.confirmInner(lane, crystal);
            }

            return false;
         } else {
            this.captureTop(lane, crystal);
            return true;
         }
      } else {
         return false;
      }
   }

   private void captureTop(MineCrystalChainController.LaneState lane, EndCrystal crystal) {
      if (lane.topCrystalId < 0) {
         lane.topCrystalId = crystal.getId();
         lane.phase = MineCrystalChainController.Phase.TOP_LIVE;
         lane.actionFailures = 0;
         this.trace(lane, "TOP_SPAWN", false, "entity=" + crystal.getId() + " " + this.timingDetail(lane));
      }
   }

   private void confirmInner(MineCrystalChainController.LaneState lane, EndCrystal crystal) {
      lane.phase = MineCrystalChainController.Phase.INNER_CONFIRMED;
      lane.actionFailures = 0;
      this.trace(lane, "INNER_SPAWN", false, "entity=" + crystal.getId() + " attempts=" + lane.attempts);
   }

   private boolean protects(MineCrystalChainController.LaneState lane, EndCrystal crystal) {
      if (lane.pos == null || lane.phase == MineCrystalChainController.Phase.IDLE || lane.phase == MineCrystalChainController.Phase.FAILED) {
         return false;
      } else if (lane.topCrystalId >= 0) {
         EndCrystal liveTop = this.findCrystal(lane.topPos());
         if (liveTop != null && liveTop.getId() == lane.topCrystalId) {
            return crystal.getId() == lane.topCrystalId;
         } else {
            lane.topCrystalId = -1;
            return false;
         }
      } else {
         return lane.topSends > 0
            && (lane.phase == MineCrystalChainController.Phase.ARMING || lane.phase == MineCrystalChainController.Phase.WAIT_TOP)
            && crystal.blockPosition().equals(lane.topPos());
      }
   }

   private void updateBlock(MineCrystalChainController.LaneState lane, BlockPos pos, boolean nowAir) {
      if (lane.pos != null && lane.pos.equals(pos)) {
         this.trace(lane, nowAir ? "AIR_UPDATE" : "SOLID_UPDATE", false, this.timingDetail(lane) + " items=" + this.blockingItems(pos));
         if (nowAir) {
            this.observeAir(lane);
         } else if (!lane.lastSolid) {
            this.transitionToSolid(lane, lane.snapshot, "SOLID_UPDATE");
            lane.lastSolid = true;
         }
      }
   }

   private void observeAir(MineCrystalChainController.LaneState lane) {
      boolean firstObservation = !lane.airObserved;
      this.recordAirEvidence(lane);
      if (firstObservation) {
         this.calibrateDelayed(lane);
      }

      this.handleAir(lane);
   }

   private void recordAirEvidence(MineCrystalChainController.LaneState lane) {
      if (!lane.airObserved && lane.role == SpeedMineController.MineLane.REBREAK && this.speedMine != null) {
         this.speedMine.markRebreakAir(lane.pos);
      }

      lane.airObserved = true;
      lane.lastSolid = false;
   }

   private void markExplosion(MineCrystalChainController.LaneState lane, Vec3 center, float radius) {
      if (lane.pos != null && this.owner.ownsMineChainPosition(lane.pos)) {
         double reach = Math.max(1.0, (double)radius) + 1.0;
         AABB block = new AABB(lane.pos);
         double dx = center.x - Mth.clamp(center.x, block.minX, block.maxX);
         double dy = center.y - Mth.clamp(center.y, block.minY, block.maxY);
         double dz = center.z - Mth.clamp(center.z, block.minZ, block.maxZ);
         if (!(dx * dx + dy * dy + dz * dz > reach * reach)) {
            lane.explosionPendingUntil = this.currentTick() + 2;
            if (mc.level.getBlockState(lane.pos).isAir()) {
               lane.explosionPendingUntil = 0;
               this.observeAir(lane);
            } else if (lane.role == SpeedMineController.MineLane.REBREAK
               && lane.phase != MineCrystalChainController.Phase.IDLE
               && lane.phase != MineCrystalChainController.Phase.FAILED) {
               this.recordAirEvidence(lane);
               lane.replacementPending = true;
            }
         }
      }
   }

   private void calibrateDelayed(MineCrystalChainController.LaneState lane) {
      if (lane.role == SpeedMineController.MineLane.DELAYED && lane.predictedClientTick != Integer.MIN_VALUE) {
         int observedOffset = this.currentTick() - lane.predictedClientTick;
         int rttTicks = (this.latencyMs() + 49) / 50;
         int oneWayTicks = (this.latencyMs() + 99) / 100;
         int sample = Mth.clamp(observedOffset - oneWayTicks, 1, 3);
         this.delayedTimingBias = this.timingSamples == 0
            ? sample
            : Mth.clamp(Math.round((float)(this.delayedTimingBias * this.timingSamples + sample) / (this.timingSamples + 1)), 0, 3);
         this.timingSamples = Math.min(this.timingSamples + 1, 8);
         this.trace(
            lane,
            "TIMING_SAMPLE",
            false,
            "observedOffset="
               + observedOffset
               + " rttTicks="
               + rttTicks
               + " oneWayTicks="
               + oneWayTicks
               + " sample="
               + sample
               + " bias="
               + this.delayedTimingBias
         );
      }
   }

   private void beginEpoch(MineCrystalChainController.LaneState lane, SpeedMineController.MineLaneState snapshot, String reason) {
      lane.reset(snapshot == null ? lane.pos : snapshot.pos(), ++this.nextCycleId);
      lane.snapshot = snapshot;
      lane.lastSolid = lane.pos != null && !mc.level.getBlockState(lane.pos).isAir();
      this.trace(lane, "ARM", false, "reason=" + reason + " " + this.timingDetail(lane));
   }

   private void fail(MineCrystalChainController.LaneState lane, String reason) {
      if (lane.phase != MineCrystalChainController.Phase.FAILED) {
         lane.phase = MineCrystalChainController.Phase.FAILED;
         this.trace(lane, "FAILED", false, "reason=" + reason + " attempts=" + lane.attempts);
      }
   }

   private EndCrystal findCrystal(BlockPos pos) {
      if (pos == null) {
         return null;
      } else {
         for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(pos), Entity::isAlive)) {
            if (crystal.blockPosition().equals(pos)) {
               return crystal;
            }
         }

         return null;
      }
   }

   private int blockingItems(BlockPos pos) {
      AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0);
      return mc.level.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive).size();
   }

   private int operationTimeoutTicks() {
      return Mth.clamp((this.latencyMs() + 49) / 50 + 4, 4, 12);
   }

   private int latencyMs() {
      if (mc.getConnection() != null && mc.player != null) {
         PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
         return info == null ? 0 : Math.max(0, info.getLatency());
      } else {
         return 0;
      }
   }

   private int currentTick() {
      return mc.player == null ? 0 : mc.player.tickCount;
   }

   private String timingDetail(MineCrystalChainController.LaneState lane) {
      SpeedMineController.MineLaneState snapshot = lane.snapshot;
      return snapshot == null
         ? "elapsed=? expected=? bias=" + this.delayedTimingBias
         : "elapsed="
            + snapshot.elapsedTicks()
            + " expected="
            + snapshot.expectedBreakTick()
            + " delta="
            + String.format(Locale.ROOT, "%.4f", snapshot.delta())
            + " bias="
            + this.delayedTimingBias;
   }

   private void trace(MineCrystalChainController.LaneState lane, String event, boolean packet, String detail) {
      if (this.owner.isMineCrystalChainDebugEnabled()) {
         if (packet) {
            lane.packetOrdinal++;
         }

         KetamineClub.LOGGER
            .info(
               "[MineCrystalChain] cycle={} lane={} pos={} phase={} tick={} ns={} ordinal={} event={} {}",
               lane.cycleId,
               lane.role,
               lane.pos,
               lane.phase,
               this.currentTick(),
               System.nanoTime(),
               lane.packetOrdinal,
               event,
               detail
            );
      }
   }

   private void clearLanes() {
      this.delayed.clear();
      this.rebreak.clear();
   }

   private static final class LaneState {
      private final SpeedMineController.MineLane role;
      private BlockPos pos;
      private SpeedMineController.MineLaneState snapshot;
      private MineCrystalChainController.Phase phase = MineCrystalChainController.Phase.IDLE;
      private long cycleId;
      private int topCrystalId = -1;
      private int topSends;
      private int attempts;
      private int actionFailures;
      private int fallbackStops;
      private int deadlineTick;
      private int armDeadlineTick;
      private int packetOrdinal;
      private int predictedClientTick = Integer.MIN_VALUE;
      private boolean lastSolid;
      private boolean airObserved;
      private boolean replacementPending;
      private int explosionPendingUntil;

      private LaneState(SpeedMineController.MineLane role) {
         this.role = role;
      }

      private BlockPos topPos() {
         return this.pos == null ? null : this.pos.above();
      }

      private void reset(BlockPos nextPos, long nextCycleId) {
         this.pos = nextPos == null ? null : nextPos.immutable();
         this.snapshot = null;
         this.phase = this.pos == null ? MineCrystalChainController.Phase.IDLE : MineCrystalChainController.Phase.ARMING;
         this.cycleId = nextCycleId;
         this.topCrystalId = -1;
         this.topSends = 0;
         this.attempts = 0;
         this.actionFailures = 0;
         this.fallbackStops = 0;
         this.deadlineTick = 0;
         this.armDeadlineTick = 0;
         this.packetOrdinal = 0;
         this.predictedClientTick = Integer.MIN_VALUE;
         this.lastSolid = false;
         this.airObserved = false;
         this.replacementPending = false;
         this.explosionPendingUntil = 0;
      }

      private void clear() {
         this.reset(null, 0L);
      }
   }

   private static enum Phase {
      IDLE,
      ARMING,
      WAIT_TOP,
      TOP_LIVE,
      WAIT_INNER,
      WAIT_AIR,
      INNER_CONFIRMED,
      FAILED;

      private Phase() {
      }
   }
}
