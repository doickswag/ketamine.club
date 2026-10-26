package we.ketamine.club.features.modules.combat;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.Stage;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.entity.player.UpdateWalkingPlayerEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.render.BreakIndicatorsModule;
import we.ketamine.club.features.modules.world.SpeedMineController;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.PlacementManager;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;
import we.ketamine.club.util.render.RenderUtil;

public class SurroundModule extends Module {
   private final Setting<Boolean> attack = this.bool("Attack", true).setPage("General");
   private final Setting<Boolean> debug = this.bool("Debug", false).setPage("General");
   private final Setting<Boolean> mineProtect = this.bool("MineProtect", true).setPage("General");
   private final Setting<Boolean> fireworks = this.bool("Fireworks", true).setPage("General");
   private final Setting<Boolean> crystalProtect = this.bool("CrystalProtect", true).setPage("General");
   private final Setting<Boolean> safeRocket = this.bool("SafeRocket", false).setPage("General");
   private final Setting<Boolean> avoidHelping = this.bool("AvoidHelpingOpponents", true).setPage("General");
   private final Setting<Boolean> selfTrap = this.bool("SelfTrap", true).setPage("SelfTrap");
   private final Setting<SurroundModule.SelfTrapMode> selfTrapMode = this.mode("SelfTrapMode", SurroundModule.SelfTrapMode.Smart).setPage("SelfTrap");
   private final Setting<Boolean> selfTrapHead = this.bool("SelfTrapHead", true).setPage("SelfTrap");
   private final Setting<Boolean> antiPiston = this.bool("AntiPiston", false).setPage("SelfTrap");
   private final Setting<Boolean> crawlTrap = this.bool("CrawlTrap", true).setPage("SelfTrap");
   private final Setting<Boolean> extend = this.bool("Extend", true).setPage("Extend");
   private final Setting<SurroundModule.ExtendMode> extendMode = this.mode("ExtendMode", SurroundModule.ExtendMode.Smart).setPage("Extend");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 0.2F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 0, 62, 122, 148).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 0, 62, 122, 148).setPage("Render");
   private final Setting<Color> rocketFillColor = this.color("RocketFillColor", 0, 255, 80, 80).setPage("Render");
   private final Setting<Color> rocketOutlineColor = this.color("RocketOutlineColor", 80, 255, 120, 180).setPage("Render");
   private final Map<BlockPos, Long> renderMap = new HashMap<>();
   private final Map<BlockPos, Long> rocketRenderMap = new HashMap<>();
   private final Set<BlockPos> wantedPoses = ConcurrentHashMap.newKeySet();
   private final Set<BlockPos> ownedQueued = new HashSet<>();
   private final Map<Integer, Long> attackedCrystals = new HashMap<>();
   private final Map<Integer, Set<BlockPos>> crystalRepairs = new HashMap<>();
   private final SurroundRepairState repairState = new SurroundRepairState();
   private Level sessionLevel;
   private Player sessionPlayer;
   private SurroundFootprint footprint;
   private final Set<BlockPos> corePoses = new HashSet<>();
   private final Set<BlockPos> urgentRepairs = new LinkedHashSet<>();
   private boolean servicingRepairs;
   private long nextRepairRetry;
   private final Map<SurroundModule.DebugKey, Long> debugSkips = new HashMap<>();
   private long lastDebugStatus;
   private String debugScope;
   private int cachedObsSlot = -1;
   private int cachedFireworkSlot = -1;
   private final Set<BlockPos> fireworkPoses = ConcurrentHashMap.newKeySet();
   private final Map<BlockPos, Long> fireworkDeployedAt = new HashMap<>();
   private static final long FIREWORK_REDEPLOY_COOLDOWN_MS = 500L;
   private final Set<BlockPos> extendPoses = ConcurrentHashMap.newKeySet();
   private final Set<BlockPos> opponentSurroundPoses = ConcurrentHashMap.newKeySet();
   private final Set<BlockPos> helpBlockedPoses = ConcurrentHashMap.newKeySet();
   private final Map<BlockPos, Deque<Long>> breakTimes = new ConcurrentHashMap<>();
   private final Map<BlockPos, Integer> breakCounts = new ConcurrentHashMap<>();
   private static final long REBREAK_WINDOW_MS = 1000L;
   private static final int REBREAK_THRESHOLD = 3;
   private static final int SAFE_REBREAK_THRESHOLD = 3;
   private final PlacementManager.PlacementListener airRefillListener = new PlacementManager.PlacementListener() {
      {
         Objects.requireNonNull(SurroundModule.this);
      }

      @Override
      public void onBlockUpdate(BlockPos pos, boolean nowAir) {
         SurroundModule.this.onServerBlockUpdate(pos, nowAir);
      }

      @Override
      public void onEntityRemoved(int entityId) {
         SurroundModule.this.onCrystalRemoved(entityId);
      }

      @Override
      public boolean validateQueuedPlacement(BlockPos pos) {
         return SurroundModule.this.queuedPlacementValid(pos);
      }

      @Override
      public int queuedPlacementPriority(BlockPos pos, int priority) {
         return SurroundModule.this.placementPriority(pos);
      }

      @Override
      public void onPlacementSent(BlockPos pos, long sentAt) {
         SurroundModule.this.queuedPlacementSent(pos, sentAt);
      }

      @Override
      public void onPlacementDiscarded(BlockPos pos) {
         SurroundModule.this.repairState.discarded(pos.asLong());
         if (!SurroundModule.this.repairState.hasPending(pos.asLong())) {
            SurroundModule.this.ownedQueued.remove(pos);
         }

         SurroundModule.this.debugLog("queue-discarded pos={}", pos);
      }
   };
   private long lastCrystalNearHead = 0L;
   private BlockPos lastExtendOffset = null;
   private long lastExtendThreat = 0L;
   private static final long EXTEND_HOLD_MS = 1000L;
   private static final Direction[] HORIZONTALS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

   public boolean isSurroundPos(BlockPos pos) {
      return this.wantedPoses.contains(pos);
   }

   public List<SurroundModule.PingBypassRenderEntry> pingBypassRenderEntries(long now) {
      if (this.isToggled() && this.sessionLevel == mc.level && this.sessionPlayer == mc.player) {
         long fadeMs = Math.max(1L, (long)(this.fadeTime.getValue().floatValue() * 1000.0));
         List<SurroundModule.PingBypassRenderEntry> result = new ArrayList<>(this.renderMap.size() + this.rocketRenderMap.size());
         this.renderMap.forEach((pos, stamp) -> {
            long remaining = fadeMs - (now - stamp);
            if (remaining > 0L) {
               result.add(new SurroundModule.PingBypassRenderEntry(pos.immutable(), false, remaining));
            }
         });
         this.rocketRenderMap.forEach((pos, stamp) -> {
            long remaining = fadeMs - (now - stamp);
            if (remaining > 0L) {
               result.add(new SurroundModule.PingBypassRenderEntry(pos.immutable(), true, remaining));
            }
         });
         return result;
      } else {
         return List.of();
      }
   }

   public boolean hasOpenCoreCell() {
      if (this.isEnabled() && !nullCheck()) {
         Result obsidian = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.PLACE_SCOPE);
         if (obsidian.found() && obsidian.type() != ResultType.OFFHAND) {
            AABB bounds = mc.player.getBoundingBox();
            int feetY = mc.player.blockPosition().getY();
            int minX = PlaceUtil.minCell(bounds.minX);
            int maxX = PlaceUtil.maxCell(bounds.maxX);
            int minZ = PlaceUtil.minCell(bounds.minZ);
            int maxZ = PlaceUtil.maxCell(bounds.maxZ);

            for (int x = minX; x <= maxX; x++) {
               for (int z = minZ; z <= maxZ; z++) {
                  BlockPos feet = new BlockPos(x, feetY, z);
                  if (this.isOpenCoreCell(feet.below())) {
                     return true;
                  }

                  for (Direction direction : HORIZONTALS) {
                     BlockPos adjacent = feet.relative(direction);
                     if ((adjacent.getX() < minX || adjacent.getX() > maxX || adjacent.getZ() < minZ || adjacent.getZ() > maxZ)
                        && this.isOpenCoreCell(adjacent)) {
                        return true;
                     }
                  }
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean isOpenCoreCell(BlockPos pos) {
      BlockState state = mc.level.getBlockState(pos);
      return (state.isAir() || state.canBeReplaced()) && (PlaceUtil.canPlace(pos) || this.hasBlockingCrystal(pos));
   }

   public SurroundModule() {
      super("Surround", "Surrounds you in obsidian to prevent crystal damage.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.clearState();
      KetamineClub.placementManager.addImmediateListener(this.airRefillListener);
      this.debugLog(
         "enabled attack={} fireworks={} crystalProtect={} mineProtect={} selfTrap={} extend={} avoidHelping={}",
         this.attack.getValue(),
         this.fireworks.getValue(),
         this.crystalProtect.getValue(),
         this.mineProtect.getValue(),
         this.selfTrap.getValue(),
         this.extend.getValue(),
         this.avoidHelping.getValue()
      );
   }

   @Override
   public void onDisable() {
      this.debugLog("disabled watched={} ownedRequests={} crystalRepairs={}", this.wantedPoses.size(), this.ownedQueued.size(), this.crystalRepairs.size());
      KetamineClub.placementManager.removeImmediateListener(this.airRefillListener);
      this.clearState();
   }

   private void clearState() {
      KetamineClub.placementManager.removeQueuedFor(this.airRefillListener, pos -> true);
      this.ownedQueued.clear();
      this.wantedPoses.clear();
      this.fireworkPoses.clear();
      this.extendPoses.clear();
      this.opponentSurroundPoses.clear();
      this.helpBlockedPoses.clear();
      this.fireworkDeployedAt.clear();
      this.breakTimes.clear();
      this.breakCounts.clear();
      this.attackedCrystals.clear();
      this.crystalRepairs.clear();
      this.repairState.reset();
      this.footprint = null;
      this.corePoses.clear();
      this.urgentRepairs.clear();
      this.nextRepairRetry = 0L;
      this.sessionLevel = null;
      this.sessionPlayer = null;
      this.cachedObsSlot = -1;
      this.cachedFireworkSlot = -1;
      this.renderMap.clear();
      this.rocketRenderMap.clear();
      this.lastExtendOffset = null;
      this.lastExtendThreat = 0L;
      this.lastCrystalNearHead = 0L;
      this.debugSkips.clear();
      this.lastDebugStatus = 0L;
   }

   private void ensureWorld() {
      if (this.sessionLevel != mc.level || this.sessionPlayer != mc.player) {
         this.debugLog("session-reset levelChanged={} playerChanged={}", this.sessionLevel != mc.level, this.sessionPlayer != mc.player);
         this.clearState();
         this.sessionLevel = mc.level;
         this.sessionPlayer = mc.player;
      }
   }

   private boolean canReact() {
      if (!this.isEnabled()) {
         return false;
      } else if (nullCheck() || mc.getConnection() == null) {
         return this.debugBlocked("no-world-or-connection", null);
      } else if (mc.player.isDeadOrDying()) {
         return this.debugBlocked("player-dead", null);
      } else if (PingBypassInput.isScreenOpen()) {
         return this.debugBlocked("screen-open", null);
      } else if (mc.player.containerMenu.containerId != 0) {
         return this.debugBlocked("container-open", null);
      } else if (!InventoryUtil.cursor().isEmpty()) {
         return this.debugBlocked("cursor-occupied", null);
      } else {
         this.ensureWorld();
         AutoMineModule mine = KetamineClub.moduleManager.getModuleByClass(AutoMineModule.class);
         if (mine != null && mine.shouldYieldSurround()) {
            return this.debugBlocked("automine-yield", null);
         } else {
            OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
            return offhand != null && offhand.shouldDeferForEat() ? this.debugBlocked("offhand-eating", null) : true;
         }
      }
   }

   private long confirmationMs() {
      if (mc.player != null && mc.getConnection() != null) {
         PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
         return Math.max(100L, Math.min(350L, (info == null ? 0L : info.getLatency()) + 50L));
      } else {
         return 100L;
      }
   }

   private boolean isCoreCell(BlockPos pos) {
      return SurroundFootprint.of(mc.player.getBoundingBox(), mc.player.blockPosition().getY()).contains(pos);
   }

   public int reservedRepairPlacements() {
      if (this.isEnabled()
         && !nullCheck()
         && !mc.player.isDeadOrDying()
         && mc.getConnection() != null
         && !PingBypassInput.isScreenOpen()
         && mc.player.containerMenu.containerId == 0
         && InventoryUtil.cursor().isEmpty()
         && this.cachedObsSlot >= 0
         && this.cachedObsSlot < 36
         && mc.player.getInventory().getItem(this.cachedObsSlot).is(Items.OBSIDIAN)) {
         AutoMineModule mine = KetamineClub.moduleManager.getModuleByClass(AutoMineModule.class);
         if (mine != null && mine.shouldYieldSurround()) {
            return 0;
         } else {
            OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
            return offhand != null && offhand.shouldDeferForEat() ? 0 : 2;
         }
      } else {
         return 0;
      }
   }

   private boolean refreshFootprint() {
      SurroundFootprint current = SurroundFootprint.of(mc.player.getBoundingBox(), mc.player.blockPosition().getY());
      if (current.equals(this.footprint)) {
         return false;
      } else {
         Set<BlockPos> cells = current.cells();
         if (this.footprint != null) {
            for (BlockPos pos : cells) {
               if (current.wall(pos) && !this.corePoses.contains(pos) && mc.level.getBlockState(pos).canBeReplaced()) {
                  this.urgentRepairs.add(pos);
                  this.debugLog("new-gap pos={} previousFootprint={} currentFootprint={}", pos, this.footprint, current);
               }
            }
         }

         this.wantedPoses.removeAll(this.corePoses);
         this.footprint = current;
         this.corePoses.clear();
         this.corePoses.addAll(cells);
         this.wantedPoses.addAll(cells);
         this.computeHelpBlocked(current.minX(), current.maxX(), current.minZ(), current.maxZ(), current.feetY());
         this.urgentRepairs.removeIf(posx -> !this.wantedPoses.contains(posx) && !this.extendPoses.contains(posx));
         KetamineClub.placementManager
            .removeQueuedFor(this.airRefillListener, posx -> !this.wantedPoses.contains(posx) || mc.player.getBoundingBox().intersects(new AABB(posx)));
         return true;
      }
   }

   private int placementPriority(BlockPos pos) {
      if (this.urgentRepairs.contains(pos)) {
         return 200;
      } else {
         SurroundFootprint current = SurroundFootprint.of(mc.player.getBoundingBox(), mc.player.blockPosition().getY());
         return !current.wall(pos) && (mc.player.onGround() || !current.floor(pos)) ? 0 : 100;
      }
   }

   private boolean queuedPlacementValid(BlockPos pos) {
      if (this.sessionLevel == mc.level && this.sessionPlayer == mc.player && this.canReact()) {
         this.refreshFootprint();
         return (this.wantedPoses.contains(pos) || this.isCoreCell(pos))
            && !this.helpBlockedPoses.contains(pos)
            && !this.fireworkPoses.contains(pos)
            && !this.speedMineClaims(pos)
            && !mc.player.getBoundingBox().intersects(new AABB(pos))
            && !this.hasBlockingCrystal(pos)
            && this.repairState.ready(pos.asLong(), System.currentTimeMillis());
      } else {
         return false;
      }
   }

   private void queuedPlacementSent(BlockPos pos, long stamp) {
      if (this.isEnabled() && this.sessionLevel == mc.level && this.sessionPlayer == mc.player) {
         this.repairState.sent(pos.asLong(), stamp, this.confirmationMs());
         this.ownedQueued.add(pos);
         this.renderMap.put(pos, stamp);
         this.debugLog("placement-dispatched pos={} confirmationMs={}", pos, this.confirmationMs());
      }
   }

   private boolean queuePlacement(BlockPos pos, int slot, String origin) {
      int priority = this.placementPriority(pos);
      if (!KetamineClub.placementManager.enqueueSurround(pos, slot, priority, this.airRefillListener)) {
         return false;
      } else {
         this.repairState.queued(pos.asLong());
         this.ownedQueued.add(pos.immutable());
         this.debugLog("queued origin={} pos={} slot={} priority={}", origin, pos, slot, priority);
         return true;
      }
   }

   @Subscribe(
      priority = 100
   )
   private void onPostMovement(UpdateWalkingPlayerEvent event) {
      if (event.getStage() == Stage.POST) {
         this.serviceRepairs("post-movement");
      }
   }

   private void serviceRepairs(String origin) {
      if (!this.servicingRepairs && this.canReact()) {
         if (this.cachedObsSlot < 0 || !mc.player.getInventory().getItem(this.cachedObsSlot).is(Items.OBSIDIAN)) {
            Result obsidian = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.PLACE_SCOPE);
            if (!obsidian.found()) {
               this.debugBlocked("repair-no-obsidian", null);
               return;
            }

            this.cachedObsSlot = obsidian.slot();
         }

         boolean moved = this.refreshFootprint();
         long now = System.currentTimeMillis();
         if (moved || now >= this.nextRepairRetry) {
            List<BlockPos> cells = new ArrayList<>(this.urgentRepairs);

            for (BlockPos pos : this.corePoses) {
               if (this.placementPriority(pos) >= 100 && !cells.contains(pos)) {
                  cells.add(pos);
               }
            }

            cells.removeIf(posx -> !mc.level.getBlockState(posx).canBeReplaced() || mc.player.getBoundingBox().intersects(new AABB(posx)));
            cells.sort(
               Comparator.comparingInt(this::placementPriority)
                  .reversed()
                  .thenComparingDouble(posx -> Vec3.atCenterOf(posx).distanceToSqr(mc.player.position()))
            );
            this.servicingRepairs = true;

            try {
               if (!cells.isEmpty()) {
                  if (this.attack.getValue()) {
                     EndCrystal crystal = this.findThreateningCrystal();
                     if (crystal != null) {
                        this.attackAndRepair(crystal, origin);
                     }
                  }

                  this.repairCrystalCells(cells, now, origin);
               }

               this.flushRepairs();
            } finally {
               this.servicingRepairs = false;
               long wait = KetamineClub.placementManager.repairWindowWaitMs();
               this.nextRepairRetry = now + Math.max(1L, Math.min(25L, wait > 0L ? wait : 25L));
            }
         }
      }
   }

   private void onServerBlockUpdate(BlockPos pos, boolean air) {
      if (this.isEnabled() && !nullCheck()) {
         this.ensureWorld();
         this.refreshFootprint();
         boolean wanted = this.wantedPoses.contains(pos) || this.isCoreCell(pos);
         if (wanted || this.extendPoses.contains(pos)) {
            if (wanted) {
               this.wantedPoses.add(pos.immutable());
            }

            if (this.debug.getValue()) {
               this.debugLog(
                  "block-update pos={} air={} wanted={} prior={}", pos, air, wanted, this.repairState.snapshot(pos.asLong(), System.currentTimeMillis())
               );
            }

            boolean broken = this.repairState.blockUpdate(pos.asLong(), air);
            if (!air) {
               this.urgentRepairs.remove(pos);
               boolean hadRequest = this.ownedQueued.remove(pos);
               KetamineClub.placementManager.removeQueuedFor(this.airRefillListener, pos::equals);
               if (hadRequest) {
                  this.breakCounts.merge(pos.immutable(), 1, Integer::sum);
               }

               this.debugLog("block-solid pos={} hadOwnRequest={}", pos, hadRequest);
            } else {
               long now = System.currentTimeMillis();
               if (broken) {
                  this.recordBreak(pos, now);
               }

               if (wanted && (broken || this.isCoreCell(pos))) {
                  this.urgentRepairs.add(pos.immutable());
               }

               if (this.canReact()) {
                  if (this.fireworkPoses.contains(pos)) {
                     this.debugBlocked("firework-reserved", pos);
                  } else if (this.speedMineClaims(pos)) {
                     this.debugBlocked("speedmine-claimed", pos);
                  } else if (this.cachedFireworkSlot >= 0
                     && mc.player.getInventory().getItem(this.cachedFireworkSlot).is(Items.FIREWORK_ROCKET)
                     && this.canRocket(pos, now)) {
                     this.fireworkPoses.add(pos.immutable());
                     this.debugLog("air-update assigned-to-firework pos={} slot={}", pos, this.cachedFireworkSlot);
                  } else if (!wanted) {
                     this.debugBlocked("not-wanted", pos);
                  } else if (this.helpBlockedPoses.contains(pos)) {
                     this.debugBlocked("avoid-helping-opponent", pos);
                  } else {
                     if (!this.hasBlockingCrystal(pos)) {
                        this.repairState.unblockedAirCorrection(pos.asLong());
                     }

                     if (this.attack.getValue()) {
                        for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, new AABB(pos), Entity::isAlive)) {
                           this.attackAndRepair(crystal, "block-update");
                        }
                     }

                     this.repairCrystalCells(List.of(pos), now, "block-update");
                  }
               }
            }
         }
      }
   }

   private void onCrystalRemoved(int entityId) {
      Set<BlockPos> cells = this.crystalRepairs.remove(entityId);
      Long requestedAt = this.attackedCrystals.remove(entityId);
      if (cells != null) {
         this.debugLog("crystal-removed id={} cells={} requestAgeMs={}", entityId, cells, requestedAt == null ? -1L : System.currentTimeMillis() - requestedAt);
      }

      if (cells != null && this.canReact()) {
         this.refreshFootprint();
         List<BlockPos> current = new ArrayList<>();

         for (BlockPos pos : cells) {
            if (this.wantedPoses.contains(pos) || this.isCoreCell(pos)) {
               this.repairState.crystalRemoved(pos.asLong());
               this.urgentRepairs.add(pos);
               current.add(pos);
            }
         }

         this.repairCrystalCells(current, System.currentTimeMillis(), "crystal-removed");
      }
   }

   @Subscribe(
      priority = 10
   )
   private void onPreTick(PreTickEvent event) {
      if (nullCheck()) {
         this.debugBlocked("no-world", null);
      } else {
         this.ensureWorld();
         this.refreshFootprint();
         this.attackedCrystals.entrySet().removeIf(entry -> {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (entity != null && entity.isAlive()) {
               return false;
            } else {
               this.crystalRepairs.remove(entry.getKey());
               return true;
            }
         });
         this.crystalRepairs.keySet().removeIf(id -> {
            Entity crystal = mc.level.getEntity(id);
            return crystal == null || !crystal.isAlive();
         });
         if (PingBypassInput.isScreenOpen()) {
            this.debugBlocked("screen-open", null);
            this.clearOwnedQueue();
         } else {
            AutoMineModule autoMine = KetamineClub.moduleManager.getModuleByClass(AutoMineModule.class);
            if (autoMine != null && autoMine.shouldYieldSurround()) {
               this.debugBlocked("automine-yield", null);
               this.clearOwnedQueue();
            } else {
               Result obs = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.PLACE_SCOPE);
               if (obs.found() && obs.type() != ResultType.OFFHAND) {
                  int obsSlot = obs.slot();
                  if (this.cachedObsSlot != obsSlot) {
                     this.debugLog("obsidian-slot previous={} current={} scope={}", this.cachedObsSlot, obsSlot, obs.type());
                  }

                  this.cachedObsSlot = obsSlot;
                  this.cachedFireworkSlot = -1;
                  if (this.fireworks.getValue()) {
                     Result fw = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.PLACE_SCOPE);
                     if (fw.found() && fw.type() != ResultType.OFFHAND) {
                        this.cachedFireworkSlot = fw.slot();
                     }
                  }

                  List<BlockPos> placePoses = new ArrayList<>();
                  List<BlockPos> rebreakPlacePoses = new ArrayList<>();
                  List<BlockPos> rebreakSides = new ArrayList<>();
                  List<BlockPos> fireworkPlacePoses = new ArrayList<>();
                  List<BlockPos> feetPositions = new ArrayList<>();
                  this.wantedPoses.clear();
                  this.fireworkPoses.clear();
                  this.extendPoses.clear();
                  long now = System.currentTimeMillis();
                  AABB bounds = mc.player.getBoundingBox();
                  int feetY = mc.player.blockPosition().getY();
                  int minX = PlaceUtil.minCell(bounds.minX);
                  int maxX = PlaceUtil.maxCell(bounds.maxX);
                  int minZ = PlaceUtil.minCell(bounds.minZ);
                  int maxZ = PlaceUtil.maxCell(bounds.maxZ);

                  for (int x = minX; x <= maxX; x++) {
                     for (int z = minZ; z <= maxZ; z++) {
                        BlockPos feetPos = new BlockPos(x, feetY, z);
                        feetPositions.add(feetPos);

                        for (Direction dir : HORIZONTALS) {
                           BlockPos adjacent = feetPos.relative(dir);
                           if (adjacent.getX() < minX || adjacent.getX() > maxX || adjacent.getZ() < minZ || adjacent.getZ() > maxZ) {
                              BlockState state = mc.level.getBlockState(adjacent);
                              this.wantedPoses.add(adjacent.immutable());
                              if (!this.tryFirework(adjacent, now, fireworkPlacePoses) && (state.isAir() || state.canBeReplaced())) {
                                 placePoses.add(adjacent);
                              }

                              if (this.isHot(adjacent, now)) {
                                 rebreakSides.add(adjacent.immutable());
                              }

                              if (this.selfTrap.getValue() && this.selfTrapMode.getValue() != SurroundModule.SelfTrapMode.None) {
                                 this.checkSelfTrap(adjacent, now, placePoses);
                              }

                              if (this.cachedFireworkSlot >= 0) {
                                 BlockPos extendPos = feetPos.relative(dir, 2);
                                 this.extendPoses.add(extendPos.immutable());
                                 this.tryFirework(extendPos, now, fireworkPlacePoses);
                              }
                           }
                        }

                        BlockPos below = feetPos.below();
                        BlockState belowState = mc.level.getBlockState(below);
                        this.wantedPoses.add(below.immutable());
                        if (belowState.isAir() || belowState.canBeReplaced()) {
                           placePoses.add(below);
                        }
                     }
                  }

                  if (this.extend.getValue() && this.extendMode.getValue() != SurroundModule.ExtendMode.None) {
                     this.computeExtend(feetPositions, placePoses, now);
                  }

                  if (this.mineProtect.getValue()) {
                     this.applyMineProtect(feetPositions, placePoses);
                  }

                  if (this.fireworks.getValue()) {
                     for (BlockPos rebreak : rebreakSides) {
                        for (Direction dirx : HORIZONTALS) {
                           BlockPos neighbor = rebreak.relative(dirx);
                           if ((
                                 neighbor.getY() != feetY
                                    || neighbor.getX() < minX
                                    || neighbor.getX() > maxX
                                    || neighbor.getZ() < minZ
                                    || neighbor.getZ() > maxZ
                              )
                              && !this.wantedPoses.contains(neighbor)) {
                              this.wantedPoses.add(neighbor.immutable());
                              BlockState statex = mc.level.getBlockState(neighbor);
                              if (statex.isAir() || statex.canBeReplaced()) {
                                 rebreakPlacePoses.add(neighbor);
                              }
                           }
                        }
                     }
                  }

                  if (this.selfTrap.getValue() && (this.selfTrapHead.getValue() || this.antiPiston.getValue())) {
                     boolean prone = this.crawlTrap.getValue() && (mc.player.isVisuallyCrawling() || mc.player.isFallFlying());
                     BlockPos head = mc.player.blockPosition().above(prone ? 1 : 2);
                     if (this.selfTrapHead.getValue()) {
                        this.wantedPoses.add(head);
                        placePoses.add(head);
                     }

                     if (this.antiPiston.getValue()) {
                        for (Direction direction : HORIZONTALS) {
                           BlockPos cardinal = head.relative(direction);
                           this.wantedPoses.add(cardinal);
                           placePoses.add(cardinal);
                        }
                     }
                  }

                  if (!this.ownedQueued.isEmpty()) {
                     KetamineClub.placementManager.removeQueuedFor(this.airRefillListener, p -> !this.wantedPoses.contains(p));
                     this.ownedQueued.removeIf(p -> !this.wantedPoses.contains(p));
                  }

                  this.urgentRepairs.retainAll(this.wantedPoses);
                  Set<Long> watched = new HashSet<>();

                  for (BlockPos pos : this.wantedPoses) {
                     watched.add(pos.asLong());
                     if (!mc.level.getBlockState(pos).canBeReplaced()) {
                        this.repairState.blockUpdate(pos.asLong(), false);
                     }
                  }

                  for (BlockPos posx : this.extendPoses) {
                     watched.add(posx.asLong());
                  }

                  this.repairState.retain(watched);
                  List<BlockPos> fireworkUsePoses = new ArrayList<>();

                  for (BlockPos posx : fireworkPlacePoses) {
                     if (PlaceUtil.canPlace(posx)) {
                        fireworkUsePoses.add(posx);
                     }
                  }

                  if (this.debugAction(
                     "fireworks", () -> KetamineClub.placementManager.placeFireworksAlt(fireworkUsePoses, Direction.DOWN, this.cachedFireworkSlot)
                  )) {
                     this.debugLog("fireworks accepted slot={} cells={}", this.cachedFireworkSlot, fireworkUsePoses);

                     for (BlockPos posxx : fireworkUsePoses) {
                        this.fireworkDeployedAt.put(posxx.immutable(), now);
                        this.rocketRenderMap.put(posxx.immutable(), now);
                        this.breakCounts.remove(posxx);
                     }
                  } else if (!fireworkUsePoses.isEmpty()) {
                     this.debugBlocked("firework-send-rejected", null);
                  }

                  OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
                  if (offhand != null && offhand.shouldDeferForEat()) {
                     this.debugBlocked("offhand-eating", null);
                     this.clearOwnedQueue();
                  } else {
                     Vec3 predicted = mc.player.position().add(mc.player.getDeltaMovement().scale(0.5));
                     placePoses.sort(
                        Comparator.comparingInt(this::placementPriority).reversed().thenComparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(predicted))
                     );
                     if (this.fireworks.getValue()) {
                        rebreakPlacePoses.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(predicted)));
                        placePoses.addAll(rebreakPlacePoses);
                     }

                     if (this.attack.getValue()) {
                        EndCrystal crystal = this.findThreateningCrystal();
                        if (crystal != null) {
                           this.attackAndRepair(crystal, "pretick");
                        }
                     }

                     this.computeHelpBlocked(minX, maxX, minZ, maxZ, feetY);

                     for (BlockPos posxx : placePoses) {
                        if (this.helpBlockedPoses.contains(posxx)) {
                           this.debugBlocked("avoid-helping-opponent", posxx);
                        } else if (this.speedMineClaims(posxx)) {
                           this.debugBlocked("speedmine-claimed", posxx);
                        } else if (!PlaceUtil.canPlace(posxx)) {
                           this.debugUnplaceable(posxx);
                        } else if (!this.repairState.ready(posxx.asLong(), now)) {
                           this.debugBlocked("awaiting-repair-confirmation", posxx);
                        } else if (!this.queuePlacement(posxx, obsSlot, "pretick")) {
                           this.debugBlocked("enqueue-cooldown-or-already-queued", posxx);
                        }
                     }

                     this.debugAction("queue-flush", () -> {
                        KetamineClub.placementManager.flushQueue();
                        return true;
                     });
                     this.debugStatus(now);
                     long fadeMs = (long)(this.fadeTime.getValue() * 1000.0F);
                     this.renderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
                     this.rocketRenderMap.entrySet().removeIf(e -> now - e.getValue() > fadeMs);
                     this.fireworkDeployedAt.entrySet().removeIf(e -> now - e.getValue() > 500L);
                     this.breakTimes.entrySet().removeIf(e -> {
                        synchronized ((Deque)e.getValue()) {
                           Long newest = e.getValue().peekLast();
                           return newest == null || now - newest > 1000L;
                        }
                     });
                  }
               } else {
                  this.debugBlocked("no-usable-obsidian", null);
                  this.cachedObsSlot = -1;
                  this.clearOwnedQueue();
                  this.wantedPoses.clear();
                  this.helpBlockedPoses.clear();
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck()) {
         this.serviceRepairs("frame-retry");
         if (this.render.getValue()) {
            long now = System.currentTimeMillis();
            double fadeMs = this.fadeTime.getValue().floatValue() * 1000.0;

            for (Entry<BlockPos, Long> entry : this.renderMap.entrySet()) {
               long age = now - entry.getValue();
               if (!(age > fadeMs)) {
                  double t = age / fadeMs;
                  Color fc = this.fillColor.getValue();
                  Color oc = this.outlineColor.getValue();
                  RenderUtil.drawBoxFilled(event.getMatrix(), entry.getKey(), withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
                  RenderUtil.drawBox(event.getMatrix(), entry.getKey(), withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
               }
            }

            for (Entry<BlockPos, Long> entryx : this.rocketRenderMap.entrySet()) {
               long age = now - entryx.getValue();
               if (!(age > fadeMs)) {
                  double t = age / fadeMs;
                  Color fc = this.rocketFillColor.getValue();
                  Color oc = this.rocketOutlineColor.getValue();
                  RenderUtil.drawBoxFilled(event.getMatrix(), entryx.getKey(), withAlpha(fc, (int)(fc.getAlpha() * (1.0 - t))));
                  RenderUtil.drawBox(event.getMatrix(), entryx.getKey(), withAlpha(oc, (int)(oc.getAlpha() * (1.0 - t))), 1.0F);
               }
            }
         }
      }
   }

   private boolean speedMineClaims(BlockPos pos) {
      SpeedMineController mine = KetamineClub.moduleManager.getSpeedMine();
      return mine != null && mine.isEnabled() && mine.alreadyBreaking(pos);
   }

   private boolean intersectsCrystal(BlockPos pos) {
      Vec3 center = Vec3.atCenterOf(pos);
      AABB box = new AABB(center.x - 0.05, center.y - 0.05, center.z - 0.05, center.x + 0.05, center.y + 0.05, center.z + 0.05);
      return !mc.level.getEntitiesOfClass(EndCrystal.class, box).isEmpty();
   }

   private EndCrystal findThreateningCrystal() {
      Vec3 eye = mc.player.getEyePosition(1.0F);
      AABB search = mc.player.getBoundingBox().inflate(6.0);
      EndCrystal best = null;
      double bestSq = Double.MAX_VALUE;

      for (EndCrystal c : mc.level.getEntitiesOfClass(EndCrystal.class, search, Entity::isAlive)) {
         if (this.threatensSurround(c)) {
            Long previous = this.attackedCrystals.get(c.getId());
            if (previous == null || System.currentTimeMillis() - previous >= this.confirmationMs()) {
               AABB box = c.getBoundingBox();
               double d = distSqToBox(eye, box);
               if (!(d >= bestSq) && !(d > 36.0)) {
                  bestSq = d;
                  best = c;
               }
            }
         }
      }

      return best;
   }

   public boolean onCrystalAdded(EndCrystal crystal) {
      if (!this.canReact()) {
         return false;
      } else if (!this.attack.getValue()) {
         return this.debugBlocked("crystal-attack-disabled", crystal.blockPosition());
      } else if (crystal.isAlive() && this.threatensSurround(crystal)) {
         this.debugLog("crystal-spawn threat id={} pos={}", crystal.getId(), crystal.position());
         return this.attackAndRepair(crystal, "crystal-spawn");
      } else {
         return false;
      }
   }

   private boolean attackAndRepair(EndCrystal crystal, String origin) {
      if (!this.canReact()) {
         return false;
      } else {
         long now = System.currentTimeMillis();
         Long attackedAt = this.attackedCrystals.get(crystal.getId());
         List<BlockPos> repairCells = this.crystalRepairCells(crystal);
         Set<BlockPos> tracked = this.crystalRepairs.computeIfAbsent(crystal.getId(), ignored -> new HashSet<>());

         for (BlockPos pos : repairCells) {
            if (tracked.add(pos)) {
               this.repairState.crystalBlocking(pos.asLong());
            }

            this.urgentRepairs.add(pos);
         }

         if (attackedAt != null && now - attackedAt < this.confirmationMs()) {
            this.debugBlocked("crystal-attack-awaiting-confirmation", crystal.blockPosition());
         } else {
            this.debugLog("crystal-attack request origin={} id={} pos={} repairCells={}", origin, crystal.getId(), crystal.position(), repairCells);
            boolean accepted = this.debugAction("crystal-break", () -> this.breakCrystal(crystal, !repairCells.isEmpty()));
            this.debugLog("crystal-attack result id={} accepted={}", crystal.getId(), accepted);
            if (!accepted) {
               return false;
            }

            this.attackedCrystals.put(crystal.getId(), now);
         }

         this.repairCrystalCells(repairCells, now, origin);
         return true;
      }
   }

   private List<BlockPos> crystalRepairCells(EndCrystal crystal) {
      Set<BlockPos> cells = new LinkedHashSet<>();
      AABB crystalBox = crystal.getBoundingBox();

      for (BlockPos pos : this.wantedPoses) {
         if (crystalBox.intersects(new AABB(pos))) {
            cells.add(pos.immutable());
         }
      }

      for (BlockPos posx : this.extendPoses) {
         if (crystalBox.intersects(new AABB(posx))) {
            cells.add(posx.immutable());
         }
      }

      for (BlockPos posxx : BlockPos.betweenClosed(mc.player.blockPosition().offset(-2, -1, -2), mc.player.blockPosition().offset(2, 0, 2))) {
         if (this.isCoreCell(posxx) && crystalBox.intersects(new AABB(posxx))) {
            cells.add(posxx.immutable());
         }
      }

      return cells.stream()
         .filter(posxxx -> mc.level.getBlockState(posxxx).canBeReplaced())
         .filter(posxxx -> !mc.player.getBoundingBox().intersects(new AABB(posxxx)))
         .filter(posxxx -> !this.helpBlockedPoses.contains(posxxx) && !this.speedMineClaims(posxxx))
         .sorted(Comparator.comparingDouble(posxxx -> Vec3.atCenterOf(posxxx).distanceToSqr(mc.player.position())))
         .toList();
   }

   private void repairCrystalCells(List<BlockPos> cells, long now, String origin) {
      if (this.canReact()) {
         int slot = this.cachedObsSlot;
         if (slot < 0 || !mc.player.getInventory().getItem(slot).is(Items.OBSIDIAN)) {
            Result obsidian = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.PLACE_SCOPE);
            if (!obsidian.found() || obsidian.type() == ResultType.OFFHAND) {
               this.debugBlocked("repair-no-obsidian", null);
               return;
            }

            slot = obsidian.slot();
            this.cachedObsSlot = slot;
         }

         int repairSlot = slot;

         for (BlockPos pos : cells) {
            if (this.helpBlockedPoses.contains(pos)) {
               this.debugBlocked("avoid-helping-opponent", pos);
            } else if (this.speedMineClaims(pos)) {
               this.debugBlocked("speedmine-claimed", pos);
            } else if (this.fireworkPoses.contains(pos)) {
               this.debugBlocked("firework-reserved", pos);
            } else if (!mc.level.getBlockState(pos).canBeReplaced()) {
               this.debugBlocked("repair-cell-solid", pos);
            } else if (mc.player.getBoundingBox().intersects(new AABB(pos))) {
               this.debugBlocked("own-hitbox", pos);
            } else if (!this.repairState.ready(pos.asLong(), now)) {
               this.debugBlocked("awaiting-repair-confirmation", pos);
            } else if (!KetamineClub.placementManager.repairPlacementWindowAvailable(1)) {
               this.debugBlocked("repair-window-full", pos);
               if (!this.hasBlockingCrystal(pos)) {
                  this.queuePlacement(pos, slot, origin);
               }
            } else {
               if (this.debug.getValue()) {
                  this.debugLog("repair request origin={} pos={} slot={} prior={}", origin, pos, slot, this.repairState.snapshot(pos.asLong(), now));
               }

               KetamineClub.placementManager.forceResetPlaceCooldown(pos);
               if (this.debugAction(
                  "immediate-repair", () -> KetamineClub.placementManager.placeSurroundRepair(pos, repairSlot, this.attackedCrystals.keySet())
               )) {
                  this.debugLog("repair dispatched origin={} pos={} slot={}", origin, pos, slot);
                  KetamineClub.placementManager.removeQueuedFor(this.airRefillListener, pos::equals);
                  this.queuedPlacementSent(pos, System.currentTimeMillis());
               } else {
                  this.debugBlocked("immediate-repair-rejected", pos);
                  if (this.debug.getValue()) {
                     this.debugLog(
                        "repair-veto pos={} reason={}", pos, KetamineClub.placementManager.surroundRepairBlocker(pos, slot, this.attackedCrystals.keySet())
                     );
                  }

                  if (this.hasBlockingCrystal(pos)) {
                     this.debugBlocked("crystal-still-blocking", pos);
                  } else if (!this.queuePlacement(pos, slot, origin)) {
                     this.debugBlocked("enqueue-cooldown-or-already-queued", pos);
                  }
               }
            }
         }

         this.flushRepairs();
      }
   }

   private void flushRepairs() {
      if (!this.ownedQueued.stream().noneMatch(pos -> this.repairState.isQueued(pos.asLong()))) {
         this.debugAction("repair-flush", () -> {
            KetamineClub.placementManager.flushQueue();
            return true;
         });
      }
   }

   private boolean threatensSurround(EndCrystal crystal) {
      BlockPos cell = crystal.blockPosition();
      return this.nearSurroundCell(cell)
         || this.nearSurroundCell(cell.below())
         || this.nearFootprintPerimeter(cell)
         || this.nearFootprintPerimeter(cell.below());
   }

   private boolean nearSurroundCell(BlockPos p) {
      if (!this.wantedPoses.contains(p) && !this.extendPoses.contains(p)) {
         for (Direction dir : HORIZONTALS) {
            BlockPos n = p.relative(dir);
            if (this.wantedPoses.contains(n) || this.extendPoses.contains(n)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private boolean nearFootprintPerimeter(BlockPos p) {
      AABB bounds = mc.player.getBoundingBox();
      int feetY = mc.player.blockPosition().getY();
      if (Math.abs(p.getY() - feetY) > 2) {
         return false;
      } else {
         int minX = PlaceUtil.minCell(bounds.minX);
         int maxX = PlaceUtil.maxCell(bounds.maxX);
         int minZ = PlaceUtil.minCell(bounds.minZ);
         int maxZ = PlaceUtil.maxCell(bounds.maxZ);
         return p.getX() >= minX - 2
            && p.getX() <= maxX + 2
            && p.getZ() >= minZ - 2
            && p.getZ() <= maxZ + 2
            && (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ);
      }
   }

   private boolean breakCrystal(EndCrystal crystal, boolean blocksRepair) {
      AutoCrystalModule autoCrystal = KetamineClub.moduleManager.getModuleByClass(AutoCrystalModule.class);
      if (autoCrystal == null) {
         this.debugBlocked("autocrystal-unavailable", crystal.blockPosition());
      }

      return autoCrystal != null && autoCrystal.breakCrystalForSurround(crystal, blocksRepair);
   }

   private boolean hasBlockingCrystal(BlockPos pos) {
      AABB box = new AABB(pos);
      return !mc.level.getEntitiesOfClass(EndCrystal.class, box, Entity::isAlive).isEmpty();
   }

   private void clearOwnedQueue() {
      if (!this.ownedQueued.isEmpty()) {
         this.debugLog("clear-owned-queue cells={}", this.ownedQueued);
         KetamineClub.placementManager.removeQueuedFor(this.airRefillListener, pos -> true);
         this.ownedQueued.clear();
      }
   }

   @Subscribe(
      priority = Integer.MIN_VALUE
   )
   private void onOutgoingPacket(PacketEvent.Send event) {
      if (this.debug.getValue() && this.debugScope != null && mc.player != null) {
         Packet<?> packet = event.getPacket();
         if (packet instanceof ServerboundUseItemOnPacket use) {
            BlockHitResult hit = use.getHitResult();
            int slot = KetamineClub.swapManager.serverSlot();
            Object held = use.getHand() == InteractionHand.OFF_HAND
               ? mc.player.getOffhandItem().getItem()
               : (slot >= 0 && slot < 36 ? mc.player.getInventory().getItem(slot).getItem() : "unknown");
            this.debugLog(
               "packet-use scope={} clicked={} face={} hit={} hand={} sequence={} serverSlot={} item={} cancelled={}",
               this.debugScope,
               hit.getBlockPos(),
               hit.getDirection(),
               hit.getLocation(),
               use.getHand(),
               use.getSequence(),
               slot,
               held,
               event.isCancelled()
            );
         } else if (packet instanceof ServerboundAttackPacket attack) {
            this.debugLog("packet-attack scope={} entity={} cancelled={}", this.debugScope, attack.entityId(), event.isCancelled());
         } else if (packet instanceof ServerboundContainerClickPacket click) {
            this.debugLog(
               "packet-inventory scope={} container={} slot={} button={} cancelled={}",
               this.debugScope,
               click.containerId(),
               click.slotNum(),
               click.buttonNum(),
               event.isCancelled()
            );
         } else if (packet instanceof ServerboundSetCarriedItemPacket carried) {
            this.debugLog("packet-held-slot scope={} slot={} cancelled={}", this.debugScope, carried.getSlot(), event.isCancelled());
         }
      }
   }

   private <T> T debugAction(String scope, Supplier<T> action) {
      if (!this.debug.getValue()) {
         return action.get();
      } else {
         String previous = this.debugScope;
         this.debugScope = scope;

         Object var4;
         try {
            var4 = action.get();
         } finally {
            this.debugScope = previous;
         }

         return (T)var4;
      }
   }

   private void debugLog(String message, Object... args) {
      if (this.debug.getValue()) {
         KetamineClub.LOGGER
            .info("[Surround] tick=" + (mc.player == null ? -1 : mc.player.tickCount) + " timeMs=" + System.currentTimeMillis() + " " + message, args);
      }
   }

   private boolean debugBlocked(String reason, BlockPos pos) {
      if (!this.debug.getValue()) {
         return false;
      } else {
         long now = System.currentTimeMillis();
         SurroundModule.DebugKey key = new SurroundModule.DebugKey(reason, pos == null ? null : pos.immutable());
         Long previous = this.debugSkips.get(key);
         if (previous != null && now - previous < 1000L) {
            return false;
         } else {
            if (this.debugSkips.size() >= 512) {
               this.debugSkips.clear();
            }

            this.debugSkips.put(key, now);
            this.debugLog(
               "skip reason={} pos={} repair={} obsSlot={} managerPending={} managerPlacing={} swapBusy={} clickPairLimited={} placementWindowReady={} repairWindowReady={}",
               reason,
               pos,
               pos == null ? null : this.repairState.snapshot(pos.asLong(), now),
               this.cachedObsSlot,
               KetamineClub.placementManager.hasPending(),
               KetamineClub.placementManager.isPlacing(),
               KetamineClub.swapManager.isHotbarBusy(),
               KetamineClub.swapManager.willClickSlotBeLimited(2),
               KetamineClub.placementManager.placementWindowAvailable(1),
               KetamineClub.placementManager.repairPlacementWindowAvailable(1)
            );
            return false;
         }
      }
   }

   private void debugUnplaceable(BlockPos pos) {
      if (this.debug.getValue()) {
         String reason;
         if (mc.level.isOutsideBuildHeight(pos)) {
            reason = "outside-build-height";
         } else if (!mc.level.getBlockState(pos).canBeReplaced()) {
            reason = "cell-solid";
         } else if (Vec3.atCenterOf(pos).distanceTo(mc.player.getEyePosition()) > 5.154) {
            reason = "outside-place-range";
         } else if (mc.player.getBoundingBox().intersects(new AABB(pos))) {
            reason = "own-hitbox";
         } else {
            reason = "entity-collision";
         }

         this.debugBlocked(reason, pos);
      }
   }

   private void debugStatus(long now) {
      if (this.debug.getValue() && now - this.lastDebugStatus >= 1000L) {
         this.lastDebugStatus = now;
         this.debugSkips.entrySet().removeIf(entry -> now - entry.getValue() > 3000L);
         this.debugLog(
            "status pos={} velocity={} onGround={} crawling={} usingItem={} selectedSlot={} serverSlot={} obsSlot={} fireworkSlot={} watched={} extend={} ownedRequests={} crystalRepairs={} fireworkCells={} helpBlocked={} managerPending={} confirmationMs={}",
            mc.player.position(),
            mc.player.getDeltaMovement(),
            mc.player.onGround(),
            mc.player.isVisuallyCrawling(),
            mc.player.isUsingItem(),
            InventoryUtil.selected(),
            KetamineClub.swapManager.serverSlot(),
            this.cachedObsSlot,
            this.cachedFireworkSlot,
            this.wantedPoses.size(),
            this.extendPoses.size(),
            this.ownedQueued.size(),
            this.crystalRepairs.size(),
            this.fireworkPoses.size(),
            this.helpBlockedPoses.size(),
            KetamineClub.placementManager.hasPending(),
            this.confirmationMs()
         );
      }
   }

   private static double distSqToBox(Vec3 p, AABB box) {
      double dx = p.x < box.minX ? box.minX - p.x : (p.x > box.maxX ? p.x - box.maxX : 0.0);
      double dy = p.y < box.minY ? box.minY - p.y : (p.y > box.maxY ? p.y - box.maxY : 0.0);
      double dz = p.z < box.minZ ? box.minZ - p.z : (p.z > box.maxZ ? p.z - box.maxZ : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private void checkSelfTrap(BlockPos adjacent, long now, List<BlockPos> placePoses) {
      BlockPos face = adjacent.above();
      boolean should = this.selfTrapMode.getValue() == SurroundModule.SelfTrapMode.Always;
      if (this.selfTrapMode.getValue() == SurroundModule.SelfTrapMode.Smart) {
         if (this.intersectsCrystal(face)) {
            this.lastCrystalNearHead = now;
         }

         if (now - this.lastCrystalNearHead < 1000L) {
            should = true;
         }
      }

      if (should) {
         this.wantedPoses.add(face.immutable());
         BlockState state = mc.level.getBlockState(face);
         if (state.isAir() || state.canBeReplaced()) {
            placePoses.add(face);
         }
      }
   }

   private void computeExtend(List<BlockPos> feetPositions, List<BlockPos> placePoses, long now) {
      if (this.extendMode.getValue() == SurroundModule.ExtendMode.Always) {
         for (BlockPos feet : feetPositions) {
            for (Direction dir : HORIZONTALS) {
               this.placeExtendBlocks(placePoses, feet, new BlockPos(dir.getStepX(), 0, dir.getStepZ()));
            }
         }
      } else {
         EndCrystal crystal = this.findExtendCrystal();
         if (crystal != null) {
            this.lastExtendOffset = crystal.blockPosition().subtract(mc.player.blockPosition());
            this.lastExtendThreat = now;
         }

         if (this.lastExtendOffset != null && now - this.lastExtendThreat < 1000L) {
            for (BlockPos feet : feetPositions) {
               this.placeExtendBlocks(placePoses, feet, this.lastExtendOffset);
            }
         }
      }
   }

   private EndCrystal findExtendCrystal() {
      AABB box = mc.player.getBoundingBox().inflate(1.5, 0.5, 1.5).move(0.0, 1.0, 0.0);
      Vec3 eye = mc.player.getEyePosition();
      EndCrystal closest = null;
      double bestSq = Double.MAX_VALUE;

      for (EndCrystal c : mc.level.getEntitiesOfClass(EndCrystal.class, box)) {
         double d = c.distanceToSqr(eye);
         if (d < bestSq) {
            bestSq = d;
            closest = c;
         }
      }

      return closest;
   }

   private void placeExtendBlocks(List<BlockPos> out, BlockPos feetPos, BlockPos crystalOffset) {
      if (crystalOffset != null) {
         int normDx = Integer.signum(crystalOffset.getX());
         int normDz = Integer.signum(crystalOffset.getZ());
         boolean isDiagonal = normDx != 0 && normDz != 0;
         boolean isCardinal = normDx != 0 ^ normDz != 0;
         if (isDiagonal) {
            this.addExtendIfReplaceable(out, feetPos.offset(normDx, 0, normDz));
            this.addExtendIfReplaceable(out, feetPos.offset(normDx * 2, 0, 0));
            this.addExtendIfReplaceable(out, feetPos.offset(0, 0, normDz * 2));
         } else if (isCardinal) {
            BlockPos diagonal1;
            BlockPos diagonal2;
            BlockPos straightBlock;
            if (normDx != 0) {
               diagonal1 = feetPos.offset(normDx, 0, 1);
               diagonal2 = feetPos.offset(normDx, 0, -1);
               straightBlock = feetPos.offset(normDx * 2, 0, 0);
            } else {
               diagonal1 = feetPos.offset(1, 0, normDz);
               diagonal2 = feetPos.offset(-1, 0, normDz);
               straightBlock = feetPos.offset(0, 0, normDz * 2);
            }

            this.addExtendIfReplaceable(out, diagonal1);
            this.addExtendIfReplaceable(out, diagonal2);
            this.addExtendIfReplaceable(out, straightBlock);
         }
      }
   }

   private void applyMineProtect(List<BlockPos> feetPositions, List<BlockPos> placePoses) {
      BreakIndicatorsModule indicators = KetamineClub.moduleManager.getModuleByClass(BreakIndicatorsModule.class);
      if (indicators != null && indicators.isEnabled()) {
         for (BreakIndicatorsModule.BreakInfo info : indicators.getActiveBreaksSnapshot().values()) {
            Player player = info.player();
            if (player != null && player != mc.player && !KetamineClub.friendManager.isFriend(player)) {
               this.applyMineProtectBlock(feetPositions, placePoses, info.pos());
            }
         }
      }
   }

   private void applyMineProtectBlock(List<BlockPos> feetPositions, List<BlockPos> placePoses, BlockPos mined) {
      if (mined != null) {
         for (BlockPos feet : feetPositions) {
            if (mined.getY() == feet.getY()) {
               int dx = mined.getX() - feet.getX();
               int dz = mined.getZ() - feet.getZ();
               if (Math.abs(dx) + Math.abs(dz) == 1) {
                  this.addMineProtectIfReplaceable(placePoses, mined.above());
                  this.addMineProtectIfReplaceable(placePoses, mined.below());
                  if (dx != 0) {
                     this.addMineProtectIfReplaceable(placePoses, feet.offset(dx, 0, 1));
                     this.addMineProtectIfReplaceable(placePoses, feet.offset(dx, 0, -1));
                     this.addMineProtectIfReplaceable(placePoses, feet.offset(dx * 2, 0, 0));
                  } else {
                     this.addMineProtectIfReplaceable(placePoses, feet.offset(1, 0, dz));
                     this.addMineProtectIfReplaceable(placePoses, feet.offset(-1, 0, dz));
                     this.addMineProtectIfReplaceable(placePoses, feet.offset(0, 0, dz * 2));
                  }
               }
            }
         }
      }
   }

   private void addMineProtectIfReplaceable(List<BlockPos> out, BlockPos pos) {
      BlockState state = mc.level.getBlockState(pos);
      if (state.isAir() || state.canBeReplaced()) {
         this.wantedPoses.add(pos.immutable());
         this.extendPoses.add(pos.immutable());
         out.add(pos);
      }
   }

   private void addExtendIfReplaceable(List<BlockPos> out, BlockPos pos) {
      BlockState below = mc.level.getBlockState(pos.below());
      if (below.is(Blocks.OBSIDIAN) || below.is(Blocks.BEDROCK)) {
         this.wantedPoses.add(pos.immutable());
         this.extendPoses.add(pos.immutable());
         if (!this.fireworkPoses.contains(pos)) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced()) {
               out.add(pos);
            }
         }
      }
   }

   private void computeHelpBlocked(int minX, int maxX, int minZ, int maxZ, int feetY) {
      this.opponentSurroundPoses.clear();
      this.helpBlockedPoses.clear();
      if (this.avoidHelping.getValue()) {
         this.buildOpponentSurroundPoses();
         if (!this.opponentSurroundPoses.isEmpty()) {
            boolean singleFootCell = minX == maxX && minZ == maxZ;
            boolean lowHealth = mc.player.getHealth() < 10.0F;
            if (!singleFootCell && !lowHealth) {
               Set<BlockPos> myFootCells = new HashSet<>();

               for (int x = minX; x <= maxX; x++) {
                  for (int z = minZ; z <= maxZ; z++) {
                     myFootCells.add(new BlockPos(x, feetY, z));
                  }
               }

               for (BlockPos pos : this.opponentSurroundPoses) {
                  if (!this.isNearMyPerimeter(pos, myFootCells)) {
                     this.helpBlockedPoses.add(pos.immutable());
                  }
               }
            }
         }
      }
   }

   private void buildOpponentSurroundPoses() {
      BlockPos self = mc.player.blockPosition();

      for (Player p : mc.level.players()) {
         if (p != mc.player && !KetamineClub.friendManager.isFriend(p)) {
            BlockPos oFeet = p.blockPosition();
            double dx = oFeet.getX() - self.getX();
            double dz = oFeet.getZ() - self.getZ();
            if (!(Math.sqrt(dx * dx + dz * dz) > 5.0) && !this.isEntityPhased(p)) {
               AABB pb = p.getBoundingBox();
               int oy = oFeet.getY();
               int oMinX = PlaceUtil.minCell(pb.minX);
               int oMaxX = PlaceUtil.maxCell(pb.maxX);
               int oMinZ = PlaceUtil.minCell(pb.minZ);
               int oMaxZ = PlaceUtil.maxCell(pb.maxZ);

               for (int x = oMinX; x <= oMaxX; x++) {
                  for (int z = oMinZ; z <= oMaxZ; z++) {
                     BlockPos cell = new BlockPos(x, oy, z);

                     for (Direction dir : HORIZONTALS) {
                        BlockPos adj = cell.relative(dir);
                        if (adj.getX() < oMinX || adj.getX() > oMaxX || adj.getZ() < oMinZ || adj.getZ() > oMaxZ) {
                           this.opponentSurroundPoses.add(adj.immutable());
                        }
                     }

                     this.opponentSurroundPoses.add(cell.below().immutable());
                     this.opponentSurroundPoses.add(cell.immutable());
                  }
               }
            }
         }
      }
   }

   private boolean isNearMyPerimeter(BlockPos pos, Set<BlockPos> myFootCells) {
      for (BlockPos cell : myFootCells) {
         if (pos.equals(cell)
            || pos.equals(cell.above())
            || pos.equals(cell.below())
            || pos.equals(cell.north())
            || pos.equals(cell.south())
            || pos.equals(cell.east())
            || pos.equals(cell.west())) {
            return true;
         }
      }

      return false;
   }

   private boolean isEntityPhased(Player p) {
      BlockState state = mc.level.getBlockState(p.blockPosition());
      return !state.isAir() && !state.canBeReplaced();
   }

   private boolean tryFirework(BlockPos pos, long now, List<BlockPos> out) {
      if (this.cachedFireworkSlot < 0) {
         return false;
      } else if (this.hasLiveFireworkAt(pos)) {
         this.fireworkPoses.add(pos.immutable());
         this.debugBlocked("live-firework-reserves-cell", pos);
         return true;
      } else {
         Long last = this.fireworkDeployedAt.get(pos);
         if (last != null && now - last < 500L) {
            this.fireworkPoses.add(pos.immutable());
            this.debugBlocked("firework-redeploy-cooldown", pos);
            return true;
         } else if (!this.canRocket(pos, now)) {
            return false;
         } else if (this.speedMineClaims(pos)) {
            return false;
         } else {
            this.fireworkPoses.add(pos.immutable());
            out.add(pos);
            this.debugLog("firework request pos={} slot={} safeMode={}", pos, this.cachedFireworkSlot, this.safeRocket.getValue());
            return true;
         }
      }
   }

   private boolean canRocket(BlockPos pos, long now) {
      return (this.isHot(pos, now) || this.crystalProtect.getValue() && this.hasCrystalAt(pos))
         && this.isFullBlock(pos.above())
         && this.isCrystalBase(pos.below())
         && this.hasFireworkCornerSupport(pos);
   }

   private boolean isCrystalBase(BlockPos pos) {
      BlockState state = mc.level.getBlockState(pos);
      return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
   }

   private boolean hasCrystalAt(BlockPos pos) {
      AABB box = new AABB(pos).inflate(0.5, 1.0, 0.5);
      return !mc.level.getEntitiesOfClass(EndCrystal.class, box).isEmpty();
   }

   private boolean hasFireworkCornerSupport(BlockPos pos) {
      AABB bounds = mc.player.getBoundingBox();
      int minX = PlaceUtil.minCell(bounds.minX);
      int maxX = PlaceUtil.maxCell(bounds.maxX);
      int minZ = PlaceUtil.minCell(bounds.minZ);
      int maxZ = PlaceUtil.maxCell(bounds.maxZ);
      int x = pos.getX();
      int z = pos.getZ();
      if (z < minZ && x >= minX && x <= maxX) {
         return this.isFullBlock(pos.west()) && this.isFullBlock(pos.east());
      } else if (z > maxZ && x >= minX && x <= maxX) {
         return this.isFullBlock(pos.west()) && this.isFullBlock(pos.east());
      } else if (x < minX && z >= minZ && z <= maxZ) {
         return this.isFullBlock(pos.north()) && this.isFullBlock(pos.south());
      } else {
         return x > maxX && z >= minZ && z <= maxZ ? this.isFullBlock(pos.north()) && this.isFullBlock(pos.south()) : false;
      }
   }

   private void recordBreak(BlockPos pos, long now) {
      Deque<Long> times = this.breakTimes.computeIfAbsent(pos.immutable(), p -> new ArrayDeque<>());
      synchronized (times) {
         times.addLast(now);

         while (!times.isEmpty() && now - times.peekFirst() > 1000L) {
            times.pollFirst();
         }

         this.debugLog("break-edge pos={} breaksInWindow={} windowMs={}", pos, times.size(), 1000L);
      }
   }

   private boolean isHot(BlockPos pos, long now) {
      if (this.safeRocket.getValue()) {
         return this.breakCounts.getOrDefault(pos, 0) >= 3;
      } else {
         Deque<Long> times = this.breakTimes.get(pos);
         if (times == null) {
            return false;
         } else {
            int count = 0;
            synchronized (times) {
               for (long t : times) {
                  if (now - t <= 1000L) {
                     count++;
                  }
               }
            }

            return count >= 3;
         }
      }
   }

   private boolean isFullBlock(BlockPos pos) {
      return Block.isShapeFullBlock(mc.level.getBlockState(pos).getCollisionShape(mc.level, pos));
   }

   private boolean hasLiveFireworkAt(BlockPos pos) {
      AABB box = new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2, pos.getX() + 0.8, pos.getY() + 4.0, pos.getZ() + 0.8);

      for (FireworkRocketEntity fw : mc.level.getEntitiesOfClass(FireworkRocketEntity.class, box)) {
         if (fw.isAlive()) {
            return true;
         }
      }

      return false;
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), Mth.clamp(a, 0, 255));
   }

   private record DebugKey(String reason, BlockPos pos) {
   }

   public static enum ExtendMode {
      None,
      Smart,
      Always;

      private ExtendMode() {
      }
   }

   public record PingBypassRenderEntry(BlockPos pos, boolean rocket, long remainingMs) {
   }

   public static enum SelfTrapMode {
      None,
      Smart,
      Always;

      private SelfTrapMode() {
      }
   }
}
