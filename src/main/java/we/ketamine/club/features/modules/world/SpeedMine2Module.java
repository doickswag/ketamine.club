package we.ketamine.club.features.modules.world;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.network.AttackBlockEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.combat.OffhandModule;
import we.ketamine.club.features.modules.movement.VelocityModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.PlacementManager;
import we.ketamine.club.manager.SwapManager;
import we.ketamine.club.util.InteractionUtil;
import we.ketamine.club.util.render.RenderUtil;

public class SpeedMine2Module extends Module implements SpeedMineController {
   private static final int DECOY_Y_OFFSET = 2000;
   private static final double MIN_EYE_HEIGHT = 0.4;
   private static final double MAX_EYE_HEIGHT = 1.62;
   private static final int SECONDARY_TIMEOUT = 10;
   private static final double BREAK_AHEAD_EDGE = 0.05;
   private static final String SWAP_ID = "SpeedMine2";
   private static final int SECONDARY_SWAP_PRIORITY = 120;
   private static final int STOP_SWAP_PRIORITY = 121;
   private static final int BREAK_CONFIRM_TICKS = 20;
   private static final int RECOVERY_TOOL_SETTLE_TICKS = 2;
   private static final long REBREAK_INTERVAL_NANOS = 50000000L;
   private final Setting<Double> threshold = this.num("Threshold", 0.7, 0.1, 1.0);
   private final Setting<Integer> fudgeTicks = this.num("FudgeTicks", 1, 0, 5);
   private final Setting<Integer> breakDelay = this.num("BreakDelay", 6, 0, 10);
   private final Setting<Boolean> pauseOnEat = this.bool("PauseOnEat", true);
   private final Setting<Boolean> decoy = this.bool("GrimDecoy", true);
   private final Setting<Boolean> doubleBreak = this.bool("DoubleBreak", true);
   private final Setting<Boolean> breakAhead = this.bool("BreakAhead", false);
   private final Setting<Boolean> rebreak = this.bool("Rebreak", true);
   private final Setting<Boolean> swing = this.bool("Swing", true);
   private final Setting<Double> range = this.num("Range", 5.0, 1.0, 7.0);
   private final Setting<Boolean> debug = this.bool("Debug", false).setPage("Extra");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<SpeedMine2Module.Animation> animation = this.mode("Animation", SpeedMine2Module.Animation.Out).setPage("Render");
   private final Setting<Boolean> fill = this.bool("Fill", true).setPage("Render");
   private final Setting<Boolean> outline = this.bool("Outline", true).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.5F, 0.1F, 5.0F).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 250.0F, 0.0F, 1000.0F).setPage("Render");
   private final Setting<Boolean> dynamicColor = this.bool("DynamicColor", true).setPage("Render");
   private final Setting<Color> fillStart = this.color("FillStart", 0, 255, 150, 60).setPage("Render");
   private final Setting<Color> fillEnd = this.color("FillEnd", 255, 50, 50, 90).setPage("Render");
   private final Setting<Color> outlineStart = this.color("OutlineStart", 0, 255, 150, 255).setPage("Render");
   private final Setting<Color> outlineEnd = this.color("OutlineEnd", 255, 50, 50, 255).setPage("Render");
   private final CopyOnWriteArrayList<SpeedMineController.MineFinishListener> finishListeners = new CopyOnWriteArrayList<>();
   private final PlacementManager.PlacementListener blockListener = this::onBlockUpdate;
   private SpeedMineController.MineChainHandler mineChainHandler;
   private BlockPos pos;
   private Direction direction;
   private int ticks;
   private double progress;
   private boolean started;
   private boolean finished;
   private boolean awaitingBreak;
   private int unconfirmedTicks;
   private boolean primaryAirObserved;
   private boolean recoveringPrimary;
   private SwapManager.SwapHandle recoveryHold;
   private int recoveryOriginalSlot = -1;
   private int recoveryToolReadyTick;
   private BlockPos secondaryPos;
   private int secondaryTicks;
   private SwapManager.SwapHandle secondaryHold;
   private int secondaryOriginalSlot = -1;
   private int secondaryToolTicks;
   private boolean secondaryFinishNotified;
   private double secondaryDelta;
   private int secondaryExpectedBreakTick;
   private int stopCooldown;
   private long lastStopMs;
   private boolean sendingMineAction;
   private double delayBalance;
   private long nextRebreakCycleNanos;
   private boolean chainAirObserved;
   private boolean chainReplacementReady;
   private boolean primaryIsRebreak;
   private boolean primaryBeenAir;
   private int rebreakToolSlot = -1;
   private BlockPos rebreakHoldPos;
   private int rebreakHoldTicks;
   private final SpeedMine2Module.Fade primaryFade = new SpeedMine2Module.Fade();
   private final SpeedMine2Module.Fade secondaryFade = new SpeedMine2Module.Fade();
   private long lastNanos;

   public List<SpeedMine2Module.PingBypassRenderEntry> pingBypassRenderEntries() {
      if (this.isToggled() && !nullCheck() && !mc.player.isDeadOrDying()) {
         List<SpeedMine2Module.PingBypassRenderEntry> result = new ArrayList<>(2);
         if (this.pos != null) {
            float value = this.finished ? 1.0F : (this.started ? (float)Math.min(this.progress / this.completionThreshold(), 1.0) : 0.0F);
            result.add(this.renderEntry(this.pos, 0, this.primaryFade.cycle, value));
         }

         if (this.secondaryPos != null) {
            result.add(this.renderEntry(this.secondaryPos, 1, this.secondaryFade.cycle, Math.min((float)(this.secondaryTicks * this.secondaryDelta), 1.0F)));
         }

         return result;
      } else {
         return List.of();
      }
   }

   private SpeedMine2Module.PingBypassRenderEntry renderEntry(BlockPos target, int lane, long cycle, float progress) {
      BlockPos immutable = target.immutable();
      BlockState state = mc.level.getBlockState(immutable);
      return new SpeedMine2Module.PingBypassRenderEntry(immutable, lane, cycle, Mth.clamp(progress, 0.0F, 1.0F), !state.isAir(), this.targetBox(immutable));
   }

   public SpeedMine2Module() {
      super("SpeedMine2", "Direct port of the doohickey packet mine.", Module.Category.WORLD);
      this.breakAhead.setVisibility(value -> this.doubleBreak.getValue());
      this.lineWidth.setVisibility(value -> this.render.getValue());
      this.fadeTime.setVisibility(value -> this.render.getValue());
      this.dynamicColor.setVisibility(value -> this.render.getValue());
      this.fill.setVisibility(value -> this.render.getValue());
      this.outline.setVisibility(value -> this.render.getValue());
      this.fillStart.setVisibility(value -> this.render.getValue() && this.fill.getValue());
      this.fillEnd.setVisibility(value -> this.render.getValue() && this.fill.getValue() && this.dynamicColor.getValue());
      this.outlineStart.setVisibility(value -> this.render.getValue() && this.outline.getValue());
      this.outlineEnd.setVisibility(value -> this.render.getValue() && this.outline.getValue() && this.dynamicColor.getValue());
   }

   @Override
   public void onEnable() {
      if (this.secondaryHold != null) {
         this.clearSecondary();
      }

      KetamineClub.placementManager.addListener(this.blockListener);
      this.debugLog("enabled");
   }

   @Override
   public void onDisable() {
      KetamineClub.placementManager.removeListener(this.blockListener);
      if (!nullCheck() && this.pos != null && this.started && !this.finished) {
         this.abortBreak();
      }

      this.discardRecoveryTool();
      if (!this.clearSecondary()) {
         this.discardSecondaryLease();
         this.clearSecondaryState();
      }

      this.clearMine();
      this.rebreakHoldPos = null;
      this.rebreakHoldTicks = 0;
      this.lastStopMs = 0L;
      this.delayBalance = 0.0;
      this.nextRebreakCycleNanos = 0L;
   }

   @Override
   public String getDisplayInfo() {
      String extra = this.secondaryPos != null ? " +1" : "";
      if (this.pos == null) {
         return this.secondaryPos != null ? "+1" : null;
      } else {
         return !this.started
            ? "wait" + extra
            : (this.finished ? (this.awaitingBreak ? "confirm" : "rebreak") : (int)(Math.min(this.progress / this.completionThreshold(), 1.0) * 100.0) + "%")
               + extra;
      }
   }

   @Subscribe
   private void onAttackBlock(AttackBlockEvent event) {
      if (!nullCheck() && !mc.player.isCreative() && !mc.player.isSpectator() && !this.shouldPauseForEat()) {
         event.cancel();
         BlockPos ahead = this.breakAheadPos(event.getPos());
         if (ahead != null) {
            this.request(ahead, this.validFace(ahead), false);
         }

         this.request(event.getPos(), event.getDirection(), true);
      }
   }

   @Override
   public boolean silentBreakBlock(BlockPos target, double priority) {
      return this.isEnabled() && !nullCheck() && target != null && !mc.player.isCreative() && !mc.player.isSpectator() && !this.shouldPauseForEat()
         ? this.request(target, null, false)
         : false;
   }

   private BlockPos breakAheadPos(BlockPos target) {
      if (this.breakAhead.getValue() && this.doubleBreak.getValue()) {
         if (this.secondaryPos == null && (this.pos == null || !this.started || this.finished)) {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 look = mc.player.getLookAngle();
            AABB box = new AABB(target);
            double tEnter = Double.NEGATIVE_INFINITY;
            double tExit = Double.POSITIVE_INFINITY;
            Direction exitFace = null;

            for (Axis axis : Axis.values()) {
               double d = axis.choose(look.x, look.y, look.z);
               double origin = axis.choose(eye.x, eye.y, eye.z);
               double min = axis.choose(box.minX, box.minY, box.minZ);
               double max = axis.choose(box.maxX, box.maxY, box.maxZ);
               if (Math.abs(d) < 1.0E-7) {
                  if (origin < min || origin > max) {
                     return null;
                  }
               } else {
                  double t1 = (min - origin) / d;
                  double t2 = (max - origin) / d;
                  double near = Math.min(t1, t2);
                  double far = Math.max(t1, t2);
                  if (near > tEnter) {
                     tEnter = near;
                  }

                  if (far < tExit) {
                     tExit = far;
                     exitFace = Direction.fromAxisAndDirection(axis, d > 0.0 ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
                  }
               }
            }

            if (exitFace != null && !(tEnter > tExit) && !(tExit <= 0.0)) {
               Vec3 exit = eye.add(look.scale(tExit));

               for (Axis axisx : Axis.values()) {
                  if (axisx != exitFace.getAxis()) {
                     double position = axisx.choose(exit.x, exit.y, exit.z) - axisx.choose(target.getX(), target.getY(), target.getZ());
                     if (position < 0.05 || position > 0.95) {
                        return null;
                     }
                  }
               }

               BlockPos ahead = target.relative(exitFace);
               if (!this.inBreakRange(ahead)) {
                  return null;
               } else {
                  BlockState state = mc.level.getBlockState(ahead);
                  return !state.isAir() && InteractionUtil.canBreak(ahead, state) ? ahead : null;
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private boolean request(BlockPos target, Direction hint, boolean manual) {
      if (target == null || mc.player == null || mc.level == null) {
         return false;
      } else if (target.equals(this.pos) || target.equals(this.secondaryPos)) {
         return true;
      } else if (this.mineChainHandler != null
         && this.mineChainHandler.shouldHoldPosition(target)
         && !target.equals(this.pos)
         && !target.equals(this.secondaryPos)) {
         this.debugLog("request held by mine-crystal chain target={}", target);
         return false;
      } else {
         BlockState state = mc.level.getBlockState(target);
         if (InteractionUtil.canBreak(target, state) && (manual || this.inBreakRange(target))) {
            if (this.pos != null && !this.started && !manual) {
               return false;
            } else {
               boolean makeRebreak = manual || this.secondaryPos != null || this.pos != null && this.started && !this.primaryBeenAir;
               if (this.pos != null && this.started && !this.finished && (!this.doubleBreak.getValue() || this.secondaryPos != null || !this.demote())) {
                  this.abortBreak();
               }

               this.discardRecoveryTool();
               this.pos = target.immutable();
               this.direction = hint == null ? this.validFace(this.pos) : hint;
               this.ticks = 0;
               this.progress = 0.0;
               this.started = false;
               this.finished = false;
               this.awaitingBreak = false;
               this.unconfirmedTicks = 0;
               this.primaryAirObserved = false;
               this.recoveringPrimary = false;
               this.primaryIsRebreak = makeRebreak;
               this.primaryBeenAir = false;
               this.rebreakToolSlot = -1;
               this.chainAirObserved = false;
               this.chainReplacementReady = false;
               this.nextRebreakCycleNanos = 0L;
               this.primaryFade.reset();
               this.debugLog("request target={} rebreak={}", this.pos, this.primaryIsRebreak);
               if (this.stopCooldown == 0 && this.canBegin()) {
                  this.begin();
               }

               return true;
            }
         } else {
            return false;
         }
      }
   }

   private boolean demote() {
      BlockState state = mc.level.getBlockState(this.pos);
      if (state.isAir()) {
         return false;
      } else {
         int slot = this.bestSlot(state, this.pos);
         if (!this.stopBreak(slot, false)) {
            this.debugLog("demote swap unavailable target={} slot={}", this.pos, slot);
            return false;
         } else {
            this.secondaryPos = this.pos;
            this.secondaryTicks = this.ticks;
            this.secondaryDelta = this.bestBreakDelta(state, this.secondaryPos);
            this.secondaryExpectedBreakTick = this.expectedBreakTick(this.secondaryDelta, 1.0);
            this.secondaryToolTicks = 0;
            this.secondaryFinishNotified = false;
            this.secondaryFade.box = this.primaryFade.box;
            this.secondaryFade.cycle = this.primaryFade.cycle;
            this.secondaryFade.alpha = this.primaryFade.alpha;
            this.secondaryFade.prev = this.secondaryFade.cur = (float)Math.min(this.progress, 1.0);
            this.debugLog("demote target={} ticks={} progress={}", this.secondaryPos, this.secondaryTicks, this.progress);
            return true;
         }
      }
   }

   private void begin() {
      if (this.canBegin()) {
         this.started = true;
         this.trackStarts(this.useDecoy() ? 2 : 1);
         if (this.faceMargin(this.direction, this.targetBox(this.pos)) <= 0.0) {
            this.direction = this.validFace(this.pos);
         }

         this.sendAction(Action.START_DESTROY_BLOCK, this.pos, this.direction);
         if (this.useDecoy()) {
            BlockPos decoyPos = this.pos.below(2000);
            this.sendAction(Action.START_DESTROY_BLOCK, decoyPos, this.validFace(decoyPos));
         }

         if (this.swing.getValue()) {
            this.swingHand();
         }

         this.debugLog("start target={} face={}", this.pos, this.direction);
      }
   }

   private boolean canBegin() {
      VelocityModule velocity = KetamineClub.moduleManager.getModuleByClass(VelocityModule.class);
      if (velocity != null && velocity.hasPendingStaticCorrection()) {
         return false;
      } else {
         long delay = System.currentTimeMillis() - this.lastStopMs;
         if (delay >= 275L) {
            return true;
         } else {
            double cost = (300.0 - delay) * (this.useDecoy() ? 2.0 : 1.0);
            return this.delayBalance + cost <= 900.0;
         }
      }
   }

   private boolean useDecoy() {
      return this.decoy.getValue() && !this.recoveringPrimary;
   }

   private double completionThreshold() {
      return this.recoveringPrimary ? 1.0 : this.threshold.getValue();
   }

   private void trackStarts(int starts) {
      long delay = System.currentTimeMillis() - this.lastStopMs;

      for (int i = 0; i < starts; i++) {
         if (delay >= 275L) {
            this.delayBalance *= 0.9;
         } else {
            this.delayBalance += 300.0 - delay;
         }
      }

      this.delayBalance = Mth.clamp(this.delayBalance, -1000.0, 1000.0);
   }

   @Subscribe(
      priority = 40
   )
   private void onTick(PreTickEvent event) {
      if (!nullCheck()) {
         if (mc.player.isCreative() || mc.player.isSpectator()) {
            this.discardRecoveryTool();
            if (!this.clearSecondary()) {
               this.discardSecondaryLease();
               this.clearSecondaryState();
            }

            this.clearMine();
         } else if (this.shouldPauseForEat()) {
            this.discardRecoveryTool();
            this.releaseSecondaryTool();
         } else {
            if (this.stopCooldown > 0) {
               this.stopCooldown--;
            }

            if (this.rebreakHoldTicks > 0) {
               this.rebreakHoldTicks--;
            }

            this.tickSecondary();
            this.tickPrimary();
         }
      }
   }

   private void tickPrimary() {
      if (this.pos != null) {
         if (!this.inBreakRange(this.pos)) {
            if (this.started && !this.finished) {
               this.abortBreak();
            }

            this.clearMine();
         } else {
            BlockState state = mc.level.getBlockState(this.pos);
            if ((this.started || this.recoveringPrimary) && (state.isAir() || this.primaryAirObserved)) {
               this.primaryAirObserved = false;
               this.awaitingBreak = false;
               this.unconfirmedTicks = 0;
               this.recoveringPrimary = false;
               this.discardRecoveryTool();
               if (!this.primaryIsRebreak || !this.rebreak.getValue()) {
                  if (!this.primaryBeenAir) {
                     this.fireFinish(this.pos);
                  }

                  this.clearMine();
                  return;
               }

               if (!this.primaryBeenAir) {
                  this.primaryBeenAir = true;
                  this.chainAirObserved = false;
                  this.chainReplacementReady = !state.isAir();
                  this.fireFinish(this.pos);
                  this.debugLog("rebreak ready target={}", this.pos);
               }

               this.started = true;
               this.finished = true;
            }

            if (!this.started && (state.isAir() || !InteractionUtil.canBreak(this.pos, state))) {
               this.clearMine();
            } else if (!this.recoveringPrimary || this.holdRecoveryTool(this.bestSlot(state, this.pos))) {
               if (!this.started) {
                  if (this.stopCooldown <= 0 && this.canBegin()) {
                     this.direction = this.validFace(this.pos);
                     this.begin();
                  }
               } else if (!this.finished) {
                  int slot = this.bestSlot(state, this.pos);
                  double delta = InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(slot), state, this.pos);
                  if (delta <= 0.0) {
                     this.abortBreak();
                     this.clearMine();
                  } else {
                     this.ticks++;
                     this.progress = Math.max(this.ticks - this.fudgeTicks.getValue(), 0) * delta;
                     this.primaryFade.push((float)Math.min(this.progress / this.completionThreshold(), 1.0));
                     if (this.progress >= this.completionThreshold()) {
                        this.rebreakToolSlot = slot;
                        this.finished = this.stopBreak(slot);
                     }
                  }
               } else {
                  this.primaryFade.push(1.0F);
                  if (this.awaitingBreak && ++this.unconfirmedTicks >= this.breakConfirmTicks()) {
                     this.restartUnconfirmedBreak();
                  } else if (!this.primaryBeenAir || this.rebreakHoldTicks <= 0 || !this.pos.equals(this.rebreakHoldPos)) {
                     if (this.isRebreakCycle()) {
                        this.runRebreakCycle(state);
                     } else if (this.stopCooldown <= 0) {
                        int slot = this.bestSlot(state, this.pos);
                        this.tryRebreakCycle(state, slot, true);
                     }
                  }
               }
            }
         }
      }
   }

   private void onBlockUpdate(BlockPos target, boolean nowAir) {
      if (this.pos != null && (this.started || this.recoveringPrimary) && this.pos.equals(target)) {
         this.debugLog("block update target={} air={} awaiting={} age={}", target, nowAir, this.awaitingBreak, this.unconfirmedTicks);
         if (nowAir) {
            this.primaryAirObserved = true;
            this.awaitingBreak = false;
            this.unconfirmedTicks = 0;
         } else if (this.primaryAirObserved && this.primaryIsRebreak) {
            this.chainReplacementReady = true;
         }
      }
   }

   private int breakConfirmTicks() {
      if (mc.getConnection() != null && mc.player != null) {
         PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
         int latency = info == null ? 0 : Math.max(0, info.getLatency());
         return Math.max(20, Mth.ceil(latency / 50.0) + 10);
      } else {
         return 20;
      }
   }

   private void restartUnconfirmedBreak() {
      this.debugLog("restart unconfirmed target={} waited={} slot={}", this.pos, this.unconfirmedTicks, KetamineClub.swapManager.serverSlot());
      this.abortBreak();
      this.discardRecoveryTool();
      this.ticks = 0;
      this.progress = 0.0;
      this.started = false;
      this.finished = false;
      this.awaitingBreak = false;
      this.unconfirmedTicks = 0;
      this.primaryAirObserved = false;
      this.primaryBeenAir = false;
      this.chainAirObserved = false;
      this.chainReplacementReady = false;
      this.nextRebreakCycleNanos = 0L;
      this.stopCooldown = 0;
      this.primaryFade.reset();
      this.recoveringPrimary = true;
   }

   private boolean holdRecoveryTool(int slot) {
      SwapManager swaps = KetamineClub.swapManager;
      if (this.recoveryHold != null && !swaps.holds(this.recoveryHold)) {
         this.recoveryHold = null;
         this.recoveryOriginalSlot = -1;
      }

      if (this.recoveryHold == null) {
         this.recoveryHold = swaps.acquireLease("SpeedMine2", 121);
         if (this.recoveryHold == null) {
            return false;
         }

         this.recoveryOriginalSlot = swaps.serverSlot();
         this.recoveryToolReadyTick = mc.player.tickCount + 2;
      }

      if (!swaps.holdsActive(this.recoveryHold)) {
         this.recoveryToolReadyTick = mc.player.tickCount + 2;
         return false;
      } else {
         if (swaps.serverSlot() != slot) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
            this.recoveryToolReadyTick = mc.player.tickCount + 2;
         }

         return mc.player.tickCount >= this.recoveryToolReadyTick;
      }
   }

   private void discardRecoveryTool() {
      if (this.recoveryHold != null) {
         SwapManager swaps = KetamineClub.swapManager;

         try {
            if (swaps.holdsActive(this.recoveryHold)
               && mc.getConnection() != null
               && this.recoveryOriginalSlot >= 0
               && swaps.serverSlot() != this.recoveryOriginalSlot) {
               mc.getConnection().send(new ServerboundSetCarriedItemPacket(this.recoveryOriginalSlot));
            }
         } finally {
            swaps.release(this.recoveryHold);
            this.recoveryHold = null;
            this.recoveryOriginalSlot = -1;
         }
      }
   }

   private void runRebreakCycle(BlockState state) {
      long now = System.nanoTime();
      if (this.rebreakCycleReady(now)) {
         int slot = state.isAir() && this.rebreakToolSlot >= 0 ? this.rebreakToolSlot : this.bestSlot(state, this.pos);
         if (!state.isAir()) {
            this.rebreakToolSlot = slot;
         }

         this.tryRebreakCycle(state, slot, false);
      }
   }

   private boolean tryRebreakCycle(BlockState state, int slot, boolean cooldown) {
      SpeedMineController.MineLaneState laneState = this.rebreakLaneState(state, slot);
      if (!state.isAir() && this.mineChainHandler != null && this.mineChainHandler.shouldHandleRebreak(laneState)) {
         boolean[] stopSent = new boolean[]{false};
         Runnable stopAction = () -> {
            if (this.sendStopBreak(slot, false, this.swing.getValue())) {
               stopSent[0] = true;
            }
         };
         boolean needToolSwap = this.mineChainHandler.requiresRebreakToolSwap(laneState);
         boolean handled;
         if (!needToolSwap) {
            handled = this.mineChainHandler.onRebreak(laneState, slot, stopAction);
         } else {
            handled = this.withTool(slot, () -> this.mineChainHandler.onRebreak(laneState, slot, stopAction));
         }

         if (handled || stopSent[0]) {
            return stopSent[0];
         }
      }

      return this.stopBreak(slot, cooldown);
   }

   private void tickSecondary() {
      if (this.secondaryHold != null && !KetamineClub.swapManager.holds(this.secondaryHold)) {
         this.clearSecondaryLeaseState();
      }

      if (this.secondaryPos != null) {
         if (!this.inBreakRange(this.secondaryPos)) {
            this.clearSecondary();
         } else {
            BlockState state = mc.level.getBlockState(this.secondaryPos);
            if (state.isAir()) {
               if (this.releaseSecondaryTool()) {
                  if (!this.secondaryFinishNotified) {
                     this.secondaryFinishNotified = true;
                     this.fireFinish(this.secondaryPos);
                     this.debugLog("secondary broke target={} ticks={}", this.secondaryPos, this.secondaryTicks);
                  }

                  this.clearSecondary();
               }
            } else {
               this.secondaryTicks++;
               int slot = this.bestSlot(state, this.secondaryPos);
               double delta = InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(slot), state, this.secondaryPos);
               if (delta <= 0.0) {
                  this.clearSecondary();
               } else {
                  int expected = this.expectedBreakTick(delta, 1.0);
                  this.secondaryDelta = delta;
                  this.secondaryExpectedBreakTick = expected;
                  this.secondaryFade.push((float)Math.min(this.secondaryTicks * delta, 1.0));
                  this.secondaryToolTicks = KetamineClub.swapManager.holdsActive(this.secondaryHold) && KetamineClub.swapManager.serverSlot() == slot
                     ? this.secondaryToolTicks + 1
                     : 0;
                  if (this.mineChainHandler != null) {
                     this.mineChainHandler
                        .onDelayedTick(
                           new SpeedMineController.MineLaneState(
                              SpeedMineController.MineLane.DELAYED,
                              this.secondaryPos.immutable(),
                              mc.player.tickCount,
                              this.secondaryTicks,
                              expected,
                              delta,
                              Math.min(this.secondaryTicks * delta, 1.0),
                              true,
                              false
                           )
                        );
                  }

                  if (this.secondaryTicks > expected + 10L && this.secondaryToolTicks >= 10) {
                     this.debugLog("secondary timeout target={} ticks={} expected={}", this.secondaryPos, this.secondaryTicks, expected);
                     if (this.releaseSecondaryTool()) {
                        this.clearSecondary();
                     }
                  } else {
                     if (this.secondaryHold != null || this.secondaryTicks >= expected - 1) {
                        this.holdSecondaryTool(slot);
                     }
                  }
               }
            }
         }
      }
   }

   private void holdSecondaryTool(int slot) {
      if (mc.getConnection() != null && slot >= 0 && slot <= 8) {
         SwapManager swaps = KetamineClub.swapManager;
         if (this.secondaryHold != null && !swaps.holds(this.secondaryHold)) {
            this.clearSecondaryLeaseState();
         }

         if (this.secondaryHold == null) {
            SwapManager.SwapHandle handle = swaps.acquireLease("SpeedMine2", 120);
            if (handle == null) {
               return;
            }

            this.secondaryHold = handle;
            this.secondaryOriginalSlot = swaps.serverSlot();
            this.debugLog("secondary hold target={} slot={}", this.secondaryPos, slot);
         }

         if (swaps.holdsActive(this.secondaryHold) && swaps.serverSlot() != slot) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
         }
      }
   }

   private boolean clearSecondary() {
      if (!this.releaseSecondaryTool()) {
         return false;
      } else {
         this.clearSecondaryState();
         return true;
      }
   }

   private void clearSecondaryState() {
      this.secondaryPos = null;
      this.secondaryTicks = 0;
      this.secondaryDelta = 0.0;
      this.secondaryExpectedBreakTick = 0;
      this.secondaryToolTicks = 0;
      this.secondaryFinishNotified = false;
   }

   private void clearSecondaryLeaseState() {
      this.secondaryHold = null;
      this.secondaryOriginalSlot = -1;
      this.secondaryToolTicks = 0;
   }

   private void discardSecondaryLease() {
      if (this.secondaryHold != null) {
         SwapManager.SwapHandle handle = this.secondaryHold;
         if (KetamineClub.swapManager.holdsActive(handle)
            && this.secondaryOriginalSlot != -1
            && mc.getConnection() != null
            && KetamineClub.swapManager.serverSlot() != this.secondaryOriginalSlot) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(this.secondaryOriginalSlot));
         }

         KetamineClub.swapManager.release(handle);
         this.clearSecondaryLeaseState();
      }
   }

   private boolean releaseSecondaryTool() {
      if (this.secondaryHold == null) {
         return true;
      } else {
         SwapManager.SwapHandle handle = this.secondaryHold;
         if (handle.isReleased() || !KetamineClub.swapManager.holds(handle)) {
            this.clearSecondaryLeaseState();
            return true;
         } else if (!KetamineClub.swapManager.holdsActive(handle)) {
            return false;
         } else {
            if (this.secondaryOriginalSlot != -1 && mc.getConnection() != null && KetamineClub.swapManager.serverSlot() != this.secondaryOriginalSlot) {
               mc.getConnection().send(new ServerboundSetCarriedItemPacket(this.secondaryOriginalSlot));
            }

            KetamineClub.swapManager.release(handle);
            this.clearSecondaryLeaseState();
            return true;
         }
      }
   }

   private boolean shouldPauseForEat() {
      OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
      return this.pauseOnEat.getValue() && offhand != null && offhand.shouldDeferForEat();
   }

   @Subscribe
   private void onRender(Render3DEvent event) {
      if (this.render.getValue() && !nullCheck()) {
         long now = System.nanoTime();
         float dtMs = this.lastNanos == 0L ? 0.0F : (float)(now - this.lastNanos) / 1000000.0F;
         this.lastNanos = now;
         this.renderTarget(event, this.primaryFade, this.pos, dtMs);
         this.renderTarget(event, this.secondaryFade, this.secondaryPos, dtMs);
      } else {
         this.lastNanos = 0L;
         this.primaryFade.alpha = 0.0F;
         this.secondaryFade.alpha = 0.0F;
      }
   }

   private void renderTarget(Render3DEvent event, SpeedMine2Module.Fade fade, BlockPos target, float dtMs) {
      boolean visible = target != null && !mc.level.getBlockState(target).isAir();
      if (visible) {
         fade.box = this.targetBox(target);
      }

      float step = this.fadeTime.getValue() <= 0.0F ? 1.0F : dtMs / this.fadeTime.getValue();
      fade.alpha = Mth.clamp(fade.alpha + (visible ? step : -step), 0.0F, 1.0F);
      if (fade.box != null && !(fade.alpha <= 0.004F)) {
         float p = Mth.clamp(Mth.lerp(event.getDelta(), fade.prev, fade.cur), 0.0F, 1.0F);
         float alpha = this.smoothstep(fade.alpha);
         AABB box = this.animateBox(fade.box, p);
         if (this.fill.getValue()) {
            Color color = this.dynamicColor.getValue() ? this.mix(this.fillStart.getValue(), this.fillEnd.getValue(), p) : this.fillStart.getValue();
            RenderUtil.drawBoxFilled(event.getMatrix(), box, this.faded(color, alpha));
         }

         if (this.outline.getValue()) {
            Color color = this.dynamicColor.getValue() ? this.mix(this.outlineStart.getValue(), this.outlineEnd.getValue(), p) : this.outlineStart.getValue();
            RenderUtil.drawBox(event.getMatrix(), box, this.faded(color, alpha), this.lineWidth.getValue());
         }
      }
   }

   private AABB animateBox(AABB box, float progress) {
      return switch ((SpeedMine2Module.Animation)this.animation.getValue()) {
         case None -> box;
         case Out -> this.lerpBox(box, progress, true);
         case In -> this.lerpBox(box, progress, false);
         case InOut -> progress >= 0.5F ? this.lerpBox(box, (progress - 0.5F) * 2.0F, true) : this.lerpBox(box, progress * 2.0F, false);
         case OutIn -> progress >= 0.5F ? this.lerpBox(box, (progress - 0.5F) * 2.0F, false) : this.lerpBox(box, progress * 2.0F, true);
      };
   }

   private AABB lerpBox(AABB box, float progress, boolean growing) {
      float scale = this.smoothstep(Mth.clamp(growing ? progress : 1.0F - progress, 0.0F, 1.0F));
      Vec3 center = box.getCenter();
      return new AABB(
         Mth.lerp(scale, center.x, box.minX),
         Mth.lerp(scale, center.y, box.minY),
         Mth.lerp(scale, center.z, box.minZ),
         Mth.lerp(scale, center.x, box.maxX),
         Mth.lerp(scale, center.y, box.maxY),
         Mth.lerp(scale, center.z, box.maxZ)
      );
   }

   private Color mix(Color from, Color to, float progress) {
      return new Color(
         Math.round(Mth.lerp(progress, from.getRed(), to.getRed())),
         Math.round(Mth.lerp(progress, from.getGreen(), to.getGreen())),
         Math.round(Mth.lerp(progress, from.getBlue(), to.getBlue())),
         Math.round(Mth.lerp(progress, from.getAlpha(), to.getAlpha()))
      );
   }

   private Color faded(Color color, float alpha) {
      return alpha >= 1.0F ? color : new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * alpha));
   }

   private float smoothstep(float value) {
      return value * value * (3.0F - 2.0F * value);
   }

   private boolean stopBreak(int slot) {
      return this.stopBreak(slot, true);
   }

   private boolean stopBreak(int slot, boolean cooldown) {
      return this.pos == null ? false : this.withTool(slot, () -> this.sendStopBreak(slot, cooldown, this.swing.getValue()));
   }

   private boolean withTool(int slot, BooleanSupplier action) {
      if (mc.getConnection() != null && slot >= 0 && slot <= 8) {
         SwapManager swaps = KetamineClub.swapManager;
         boolean usingRecoveryHold = swaps.holdsActive(this.recoveryHold);
         SwapManager.SwapHandle handle = usingRecoveryHold ? this.recoveryHold : swaps.acquire("SpeedMine2", 121);
         if (handle == null) {
            return false;
         } else {
            int originalSlot = swaps.serverSlot();

            boolean var7;
            try {
               if (slot != originalSlot) {
                  mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
               }

               var7 = action.getAsBoolean();
            } finally {
               try {
                  if (swaps.holdsActive(handle) && mc.getConnection() != null && swaps.serverSlot() != originalSlot) {
                     mc.getConnection().send(new ServerboundSetCarriedItemPacket(originalSlot));
                  }
               } finally {
                  if (!usingRecoveryHold) {
                     swaps.release(handle);
                  }
               }
            }

            return var7;
         }
      } else {
         return false;
      }
   }

   private boolean sendStopBreak(int slot, boolean cooldown, boolean sendSwing) {
      if (this.pos != null && mc.getConnection() != null) {
         if (!mc.level.getBlockState(this.pos).isAir() && !this.awaitingBreak) {
            this.awaitingBreak = true;
            this.unconfirmedTicks = 0;
         }

         boolean rebreakCycle = this.isRebreakCycle();
         if (cooldown && !rebreakCycle) {
            this.stopCooldown = Math.max(1, this.breakDelay.getValue());
         }

         this.sendAction(Action.STOP_DESTROY_BLOCK, this.pos, this.validFace(this.pos));
         boolean airPoll = rebreakCycle && !this.chainReplacementReady && mc.level.getBlockState(this.pos).isAir();
         if (sendSwing && !airPoll) {
            this.swingHand();
         }

         if (rebreakCycle) {
            this.scheduleNextRebreakCycle();
         }

         this.debugLog(
            "stop target={} slot={} progress={} awaiting={} age={} recovery={}",
            this.pos,
            slot,
            this.progress,
            this.awaitingBreak,
            this.unconfirmedTicks,
            this.recoveringPrimary
         );
         return true;
      } else {
         return false;
      }
   }

   private void abortBreak() {
      this.sendAction(Action.ABORT_DESTROY_BLOCK, this.pos, Direction.DOWN);
      this.debugLog("abort target={}", this.pos);
   }

   private Direction validFace(BlockPos target) {
      AABB box = this.targetBox(target);
      Direction best = Direction.UP;
      double bestMargin = -Double.MAX_VALUE;

      for (Direction candidate : Direction.values()) {
         double margin = this.faceMargin(candidate, box);
         if (margin > bestMargin) {
            bestMargin = margin;
            best = candidate;
         }
      }

      return best;
   }

   private double faceMargin(Direction face, AABB box) {
      Vec3 now = mc.player.position();
      Vec3 previous = new Vec3(mc.player.xo, mc.player.yo, mc.player.zo);
      return Math.min(this.feetMargin(face, box, now), this.feetMargin(face, box, previous));
   }

   private double feetMargin(Direction face, AABB box, Vec3 feet) {
      return switch (face) {
         case UP -> feet.y + 1.62 - box.maxY;
         case DOWN -> box.minY - (feet.y + 0.4);
         case EAST -> feet.x - box.maxX;
         case WEST -> box.minX - feet.x;
         case SOUTH -> feet.z - box.maxZ;
         case NORTH -> box.minZ - feet.z;
         default -> throw new MatchException(null, null);
      };
   }

   private AABB targetBox(BlockPos target) {
      VoxelShape shape = mc.level.getBlockState(target).getShape(mc.level, target);
      return shape.isEmpty() ? new AABB(target) : shape.bounds().move(target);
   }

   private int bestSlot(BlockState state, BlockPos target) {
      int best = mc.player.getInventory().getSelectedSlot();
      double bestDelta = InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(best), state, target);

      for (int slot = 0; slot < 9; slot++) {
         double delta = InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(slot), state, target);
         if (delta > bestDelta) {
            best = slot;
            bestDelta = delta;
         }
      }

      return best;
   }

   private double bestBreakDelta(BlockState state, BlockPos target) {
      int slot = this.bestSlot(state, target);
      return InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(slot), state, target);
   }

   private int expectedBreakTick(double delta, double completion) {
      return delta <= 0.0 ? Integer.MAX_VALUE : Math.max(0, Mth.ceil(completion / delta) - 1);
   }

   private SpeedMineController.MineLaneState rebreakLaneState(BlockState state, int slot) {
      double delta = state.isAir() ? 0.0 : InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(slot), state, this.pos);
      int expected = this.expectedBreakTick(delta, this.completionThreshold());
      return new SpeedMineController.MineLaneState(
         SpeedMineController.MineLane.REBREAK,
         this.pos.immutable(),
         mc.player.tickCount,
         this.ticks,
         expected,
         delta,
         this.progress,
         this.finished,
         this.hasChainRebreakEvidence()
      );
   }

   private void sendAction(Action action, BlockPos target, Direction face) {
      if (mc.getConnection() != null) {
         boolean wasSending = this.sendingMineAction;
         this.sendingMineAction = true;

         try {
            mc.getConnection().send(new ServerboundPlayerActionPacket(action, target, face));
         } finally {
            this.sendingMineAction = wasSending;
         }
      }
   }

   @Subscribe(
      priority = Integer.MIN_VALUE
   )
   private void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundPlayerActionPacket packet && packet.getAction() == Action.STOP_DESTROY_BLOCK) {
         this.lastStopMs = System.currentTimeMillis();
         if (!this.sendingMineAction && this.debug.getValue()) {
            this.debugLog("external stop target={} primary={} secondary={}", packet.getPos(), this.pos, this.secondaryPos);
         }
      }
   }

   private void swingHand() {
      if (mc.getConnection() != null) {
         mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
      }
   }

   private void clearMine() {
      this.discardRecoveryTool();
      this.pos = null;
      this.direction = null;
      this.ticks = 0;
      this.progress = 0.0;
      this.started = false;
      this.finished = false;
      this.awaitingBreak = false;
      this.unconfirmedTicks = 0;
      this.primaryAirObserved = false;
      this.recoveringPrimary = false;
      this.primaryIsRebreak = false;
      this.primaryBeenAir = false;
      this.rebreakToolSlot = -1;
      this.chainAirObserved = false;
      this.chainReplacementReady = false;
      this.nextRebreakCycleNanos = 0L;
   }

   private boolean hasChainRebreakEvidence() {
      return this.primaryBeenAir || this.chainAirObserved || this.chainReplacementReady;
   }

   private boolean isRebreakCycle() {
      return this.finished && this.primaryIsRebreak && this.hasChainRebreakEvidence();
   }

   private boolean rebreakCycleReady(long now) {
      if (this.nextRebreakCycleNanos == 0L) {
         this.nextRebreakCycleNanos = now;
         return true;
      } else {
         return now >= this.nextRebreakCycleNanos;
      }
   }

   private void scheduleNextRebreakCycle() {
      long now = System.nanoTime();
      this.nextRebreakCycleNanos = now + 50000000L;
   }

   @Override
   public void markRebreakAir(BlockPos target) {
      if (target != null && this.pos != null && this.primaryIsRebreak && this.pos.equals(target)) {
         if (!this.chainAirObserved || this.chainReplacementReady) {
            this.chainAirObserved = true;
            this.chainReplacementReady = false;
            this.debugLog("chain air evidence target={}", target);
         }
      }
   }

   @Override
   public void resetRebreakStopBudget(BlockPos target) {
      if (target != null && this.pos != null && this.primaryIsRebreak && this.pos.equals(target)) {
         if (!this.chainReplacementReady) {
            this.chainReplacementReady = true;
            this.debugLog("chain replacement target={}", target);
         }
      }
   }

   private void fireFinish(BlockPos target) {
      for (SpeedMineController.MineFinishListener listener : this.finishListeners) {
         listener.onMineFinish(target);
      }
   }

   @Override
   public boolean hasActiveMining() {
      return this.isEnabled() && !nullCheck()
         ? this.pos != null && (this.recoveringPrimary || !mc.level.getBlockState(this.pos).isAir())
            || this.secondaryPos != null && !mc.level.getBlockState(this.secondaryPos).isAir()
         : false;
   }

   @Override
   public boolean hasDelayedDestroy() {
      return this.secondaryPos != null || this.pos != null && !this.primaryIsRebreak;
   }

   @Override
   public boolean hasRebreakBlock() {
      return this.pos != null && this.primaryIsRebreak && !this.primaryBeenAir;
   }

   @Override
   public BlockPos getDelayedDestroyBlockPos() {
      return this.secondaryPos != null ? this.secondaryPos : (this.primaryIsRebreak ? null : this.pos);
   }

   @Override
   public BlockPos getRebreakBlockPos() {
      return this.primaryIsRebreak ? this.pos : null;
   }

   @Override
   public boolean canRebreakRebreakBlock() {
      return this.pos != null && this.primaryIsRebreak && this.primaryBeenAir;
   }

   @Override
   public boolean inBreakRange(BlockPos target) {
      return target != null && mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(target)) <= this.range.getValue();
   }

   @Override
   public boolean alreadyBreaking(BlockPos target) {
      return target != null && (target.equals(this.pos) || target.equals(this.secondaryPos));
   }

   @Override
   public boolean cancelPrimary(BlockPos target) {
      if (target != null && target.equals(this.pos)) {
         if (!nullCheck() && this.started && !mc.level.getBlockState(this.pos).isAir()) {
            this.abortBreak();
         }

         this.clearMine();
         if (target.equals(this.rebreakHoldPos)) {
            this.rebreakHoldPos = null;
            this.rebreakHoldTicks = 0;
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public BlockPos getPrimaryBlockPos() {
      return this.pos;
   }

   @Override
   public BlockPos getSecondaryBlockPos() {
      return this.secondaryPos;
   }

   @Override
   public boolean isDoubleBreakEnabled() {
      return this.doubleBreak.getValue();
   }

   @Override
   public void addFinishListener(SpeedMineController.MineFinishListener listener) {
      this.finishListeners.addIfAbsent(listener);
   }

   @Override
   public void removeFinishListener(SpeedMineController.MineFinishListener listener) {
      this.finishListeners.remove(listener);
   }

   @Override
   public void holdRebreak(BlockPos target, int holdTicks) {
      this.rebreakHoldPos = target == null ? null : target.immutable();
      this.rebreakHoldTicks = target == null ? 0 : holdTicks;
   }

   @Override
   public void clearRebreak() {
      if (this.pos != null && this.primaryIsRebreak) {
         if (!mc.level.getBlockState(this.pos).isAir()) {
            this.abortBreak();
         }

         this.clearMine();
      }

      this.rebreakHoldPos = null;
      this.rebreakHoldTicks = 0;
   }

   @Override
   public boolean relaneRebreakToDelayed(BlockPos target) {
      if (target == null || this.pos == null || !this.primaryIsRebreak || !this.pos.equals(target)) {
         return false;
      } else if (this.primaryBeenAir) {
         return false;
      } else {
         this.primaryIsRebreak = false;
         this.chainAirObserved = false;
         this.chainReplacementReady = false;
         this.nextRebreakCycleNanos = 0L;
         this.rebreakHoldPos = null;
         this.rebreakHoldTicks = 0;
         this.debugLog("relane rebreak->delayed target={} ticks={} progress={}", this.pos, this.ticks, this.progress);
         return true;
      }
   }

   @Override
   public void collectMiningPositions(Set<BlockPos> positions, double minProgress) {
      if (this.pos != null && (this.finished || this.progress >= minProgress)) {
         positions.add(this.pos);
      }

      if (this.secondaryPos != null && this.secondaryProgress() >= minProgress) {
         positions.add(this.secondaryPos);
      }
   }

   @Override
   public SpeedMineController.MineLaneState getDelayedLaneState() {
      BlockPos target = this.getDelayedDestroyBlockPos();
      if (target == null || mc.player == null || mc.level == null) {
         return null;
      } else if (this.secondaryPos != null) {
         BlockState state = mc.level.getBlockState(this.secondaryPos);
         double delta = state.isAir() ? this.secondaryDelta : this.bestBreakDelta(state, this.secondaryPos);
         int expected = delta <= 0.0 ? this.secondaryExpectedBreakTick : this.expectedBreakTick(delta, 1.0);
         return new SpeedMineController.MineLaneState(
            SpeedMineController.MineLane.DELAYED,
            this.secondaryPos.immutable(),
            mc.player.tickCount,
            this.secondaryTicks,
            expected,
            delta,
            Math.min(this.secondaryTicks * delta, 1.0),
            true,
            state.isAir()
         );
      } else {
         BlockState state = mc.level.getBlockState(this.pos);
         int slot = state.isAir() ? mc.player.getInventory().getSelectedSlot() : this.bestSlot(state, this.pos);
         double delta = state.isAir() ? 0.0 : InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(slot), state, this.pos);
         int expected = this.expectedBreakTick(delta, this.completionThreshold());
         return new SpeedMineController.MineLaneState(
            SpeedMineController.MineLane.DELAYED,
            this.pos.immutable(),
            mc.player.tickCount,
            this.ticks,
            expected,
            delta,
            this.progress,
            this.finished,
            state.isAir()
         );
      }
   }

   @Override
   public SpeedMineController.MineLaneState getRebreakLaneState() {
      if (this.pos != null && this.primaryIsRebreak && mc.player != null && mc.level != null) {
         BlockState state = mc.level.getBlockState(this.pos);
         int slot = state.isAir() && this.rebreakToolSlot >= 0 ? this.rebreakToolSlot : this.bestSlot(state, this.pos);
         return this.rebreakLaneState(state, slot);
      } else {
         return null;
      }
   }

   @Override
   public void setMineChainHandler(SpeedMineController.MineChainHandler handler) {
      this.mineChainHandler = handler;
   }

   @Override
   public void clearMineChainHandler(SpeedMineController.MineChainHandler handler) {
      if (this.mineChainHandler == handler) {
         this.mineChainHandler = null;
      }
   }

   private double secondaryProgress() {
      if (this.secondaryPos == null) {
         return 0.0;
      } else {
         BlockState state = mc.level.getBlockState(this.secondaryPos);
         if (state.isAir()) {
            return 1.0;
         } else {
            int slot = this.bestSlot(state, this.secondaryPos);
            double delta = InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(slot), state, this.secondaryPos);
            return delta <= 0.0 ? 0.0 : Math.min(this.secondaryTicks * delta, 1.0);
         }
      }
   }

   @Override
   public String getDebugState() {
      return "primary="
         + (
            this.pos == null
               ? "none"
               : this.pos
                  + "{progress="
                  + String.format(Locale.ROOT, "%.3f", this.progress)
                  + ",ticks="
                  + this.ticks
                  + ",started="
                  + this.started
                  + ",finished="
                  + this.finished
                  + ",rebreak="
                  + this.primaryIsRebreak
                  + ",air="
                  + this.primaryBeenAir
                  + "}"
         )
         + ",secondary="
         + (this.secondaryPos == null ? "none" : this.secondaryPos + "{ticks=" + this.secondaryTicks + "}")
         + ",hold="
         + (this.secondaryHold != null)
         + ",cooldown="
         + this.stopCooldown
         + ",awaiting="
         + this.awaitingBreak
         + ",confirmTicks="
         + this.unconfirmedTicks
         + ",recovery="
         + this.recoveringPrimary
         + ",rebreakHold="
         + this.rebreakHoldTicks;
   }

   private void debugLog(String message, Object... args) {
      if (this.debug.getValue()) {
         KetamineClub.LOGGER.info("[SpeedMine2] " + message, args);
      }
   }

   public static enum Animation {
      None,
      Out,
      In,
      InOut,
      OutIn;

      private Animation() {
      }
   }

   private static final class Fade {
      private AABB box;
      private float alpha;
      private float prev;
      private float cur;
      private long cycle;

      private Fade() {
      }

      private void reset() {
         this.cycle++;
         this.prev = 0.0F;
         this.cur = 0.0F;
      }

      private void push(float progress) {
         this.prev = this.cur;
         this.cur = progress;
      }
   }

   public record PingBypassRenderEntry(BlockPos pos, int lane, long cycle, float progress, boolean visible, AABB box) {
   }
}
