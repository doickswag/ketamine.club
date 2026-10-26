package we.ketamine.club.features.modules.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.modules.world.SpeedMineController;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;
import we.ketamine.club.util.render.RenderUtil;

public class AutoTrapModule extends Module {
   private final Setting<Double> range = this.num("TargetRange", 8.0, 1.0, 16.0).setPage("General");
   private final Setting<Double> reach = this.num("Reach", 5.5, 3.0, 6.0).setPage("General");
   private final Setting<Double> flightLead = this.register(
         new Setting<>("FlightLead", 1.0, 0.0, 4.0, "Extra ticks ahead of placement arrival when intercepting airborne players.")
      )
      .setPage("General");
   private final Setting<Boolean> selfToggle = this.bool("SelfToggle", true).setPage("General");
   private final Setting<Boolean> debug = this.bool("Debug", false).setPage("General");
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> lineWidth = this.num("LineWidth", 1.0F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 255, 50, 50, 45).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 255, 50, 50, 160).setPage("Render");
   private static final long RETRY_RESET_MS = 1500L;
   private static final int MAX_SEND_ATTEMPTS = 4;
   private final Set<BlockPos> wanted = new LinkedHashSet<>();
   private final Set<BlockPos> pendingWall = new HashSet<>();
   private long wallSentAt;
   private final Map<BlockPos, Long> sentAt = new HashMap<>();
   private final Map<BlockPos, Integer> sendAttempts = new HashMap<>();
   private final Map<Player, AutoTrapPrediction> predictions = new HashMap<>();
   private Player trackedTarget;
   private Level sessionLevel;
   private Player sessionPlayer;

   public AutoTrapModule() {
      super("AutoTrap", "Predicts a target's path and encases them with airplaced blocks.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.resetTracking();
   }

   @Override
   public void onDisable() {
      this.resetTracking();
   }

   @Subscribe
   private void onPacketApplied(PacketEvent.ReceivePost event) {
      if (!nullCheck() && this.sessionLevel == mc.level && this.sessionPlayer == mc.player) {
         Map<Player, AutoTrapModule.MovementUpdate> updates = new HashMap<>();
         this.collectMovementUpdates(event.getPacket(), updates);
         long now = System.nanoTime();

         for (Entry<Player, AutoTrapModule.MovementUpdate> entry : updates.entrySet()) {
            Player player = entry.getKey();
            AutoTrapModule.MovementUpdate update = entry.getValue();
            AutoTrapPrediction tracker = this.predictions.computeIfAbsent(player, ignored -> new AutoTrapPrediction());
            if (tracker.observe(update.position(), now, update.teleport()) && player == this.trackedTarget) {
               this.clearPlacements();
            }
         }
      }
   }

   private void collectMovementUpdates(Packet<?> packet, Map<Player, AutoTrapModule.MovementUpdate> updates) {
      if (packet instanceof ClientboundBundlePacket bundle) {
         for (Packet<?> child : bundle.subPackets()) {
            this.collectMovementUpdates(child, updates);
         }
      } else {
         boolean teleport = false;
         Entity entity;
         if (packet instanceof ClientboundMoveEntityPacket move) {
            entity = move.getEntity(mc.level);
         } else if (packet instanceof ClientboundEntityPositionSyncPacket sync) {
            entity = mc.level.getEntity(sync.id());
         } else {
            if (!(packet instanceof ClientboundTeleportEntityPacket move)) {
               return;
            }

            entity = mc.level.getEntity(move.id());
            teleport = true;
         }

         if (entity instanceof Player player && player != mc.player && !(mc.player.distanceToSqr(player) > this.trackingRangeSquared())) {
            Vec3 position = teleport ? serverPosition(player) : player.getPositionCodec().getBase();
            AutoTrapModule.MovementUpdate previous = updates.get(player);
            updates.put(player, new AutoTrapModule.MovementUpdate(position, teleport || previous != null && previous.teleport()));
         }
      }
   }

   @Subscribe(
      priority = 1
   )
   private void onTick(TickEvent event) {
      if (!nullCheck() && !mc.player.isDeadOrDying() && !PingBypassInput.isScreenOpen()) {
         if (this.sessionLevel != mc.level || this.sessionPlayer != mc.player) {
            this.resetTracking();
            this.sessionLevel = mc.level;
            this.sessionPlayer = mc.player;
         }

         this.predictions
            .keySet()
            .removeIf(player -> mc.level.getEntity(player.getId()) != player || mc.player.distanceToSqr(player) > this.trackingRangeSquared());
         int slot = this.resolveSlot();
         if (slot < 0) {
            this.clearPlacements();
         } else {
            Player target = this.findTarget();
            if (target == null) {
               this.clearPlacements();
               this.trackedTarget = null;
               if (this.selfToggle.getValue()) {
                  this.disable();
               }
            } else {
               if (target != this.trackedTarget) {
                  this.clearPlacements();
                  this.trackedTarget = target;
               }

               OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
               if (offhand != null && offhand.shouldDeferForEat()) {
                  this.wanted.clear();
               } else {
                  long now = System.currentTimeMillis();
                  this.sentAt.entrySet().removeIf(entry -> now - entry.getValue() >= 1500L);
                  this.sendAttempts.keySet().retainAll(this.sentAt.keySet());
                  AutoTrapPrediction tracker = this.predictions.computeIfAbsent(target, player -> {
                     AutoTrapPrediction created = new AutoTrapPrediction();
                     created.reset(serverPosition(player), System.nanoTime());
                     return created;
                  });
                  AutoTrapPrediction.Motion motion = tracker.motion(System.nanoTime());
                  boolean airborne = target.isFallFlying()
                     || target.getAbilities().flying
                     || !target.onGround() && !target.isInWater() && !target.isPassenger();
                  if (airborne && !motion.reliable()) {
                     this.wanted.clear();
                  } else {
                     int ping = this.latencyMillis();
                     double arrival = AutoTrapPrediction.arrivalTicks(motion, ping);
                     double duration = airborne ? Math.min(14.0, arrival + this.flightLead.getValue() + 3.0) : Math.min(3.0, arrival + 1.0);
                     AABB startBox = target.getBoundingBox().move(motion.position().subtract(target.position()));
                     AutoTrapPrediction.Path path = AutoTrapPrediction.trace(
                        motion, startBox, duration, target.onGround(), (box, movement) -> Entity.collideBoundingBox(target, movement, box, mc.level, List.of())
                     );
                     long resend = Math.max(100L, Math.min(500L, ping + 75L));
                     if (!airborne || !this.awaitingWall(path, motion.ageTicks() + ping / 100.0, now, resend)) {
                        this.pendingWall.clear();
                        double padding = this.reach.getValue() - mc.player.blockInteractionRange();
                        AutoTrapModule.Plan plan = airborne && AutoTrapPrediction.isMoving(motion.velocity())
                           ? this.intercept(path, arrival, padding)
                           : this.cage(path, airborne ? arrival : duration, padding, target.onGround());
                        if (plan == null && path.collided() && !AutoTrapPrediction.isMoving(path.steps().getLast().velocity())) {
                           plan = this.cage(path, path.steps().getLast().time(), padding, target.onGround());
                        }

                        this.wanted.clear();
                        if (plan != null) {
                           this.wanted.addAll(plan.places());
                           List<BlockPos> ready = new ArrayList<>();

                           for (BlockPos pos : plan.places()) {
                              Long last = this.sentAt.get(pos);
                              if ((last == null || now - last >= resend) && this.sendAttempts.getOrDefault(pos, 0) < 4) {
                                 ready.add(pos);
                              }
                           }

                           List<BlockPos> sent = KetamineClub.placementManager.placeBatchExtendedReach(ready, slot);

                           for (BlockPos posx : sent) {
                              this.sentAt.put(posx, now);
                              this.sendAttempts.merge(posx, 1, Integer::sum);
                           }

                           if (plan.wall()
                              && !sent.isEmpty()
                              && plan.places().stream().allMatch(posx -> this.sentAt.containsKey(posx) && now - this.sentAt.get(posx) < resend)) {
                              this.pendingWall.addAll(plan.places());
                              this.wallSentAt = now;
                           }

                           if (this.debug.getValue()) {
                              KetamineClub.LOGGER
                                 .info(
                                    "[AutoTrap] airborne={} reliable={} speed={} accel={} sampleAge={} arrival={} impact={} wall={} target=[{}] aim=[{}] cells={} solid={} sent={}",
                                    new Object[]{
                                       airborne,
                                       motion.reliable(),
                                       motion.velocity().length(),
                                       motion.acceleration().length(),
                                       motion.ageTicks(),
                                       arrival,
                                       plan.time(),
                                       plan.wall(),
                                       fmt(motion.position()),
                                       fmt(plan.feet()),
                                       plan.places().size(),
                                       plan.solid(),
                                       sent.size()
                                    }
                                 );
                           }
                        }
                     }
                  }
               }
            }
         }
      } else {
         this.resetTracking();
      }
   }

   private boolean awaitingWall(AutoTrapPrediction.Path path, double currentTime, long now, long timeout) {
      if (this.pendingWall.isEmpty() || now - this.wallSentAt >= timeout) {
         return false;
      } else if (this.pendingWall.stream().allMatch(pos -> !mc.level.getBlockState(pos).canBeReplaced())) {
         return false;
      } else if (!AutoTrapPrediction.crossesAfter(path, this.pendingWall, currentTime)) {
         return false;
      } else {
         this.wanted.clear();
         this.wanted.addAll(this.pendingWall);
         return true;
      }
   }

   private AutoTrapModule.Plan intercept(AutoTrapPrediction.Path path, double arrival, double padding) {
      Set<BlockPos> retained = new HashSet<>(this.wanted);
      retained.addAll(this.sentAt.keySet());
      AutoTrapPrediction.Interception interception = AutoTrapPrediction.intercept(path, arrival, this.flightLead.getValue(), retained, (step, cells) -> {
         AutoTrapModule.Evaluation evaluation = this.evaluate(cells, padding, step.box(), path.at(arrival).box());
         boolean available = KetamineClub.placementManager.placementWindowAvailable(evaluation.places().size());
         return new AutoTrapPrediction.Cells(evaluation.places(), evaluation.solid(), evaluation.complete() && available);
      });
      return interception == null
         ? null
         : new AutoTrapModule.Plan(interception.step().feet(), interception.step().time(), true, interception.solid(), interception.places());
   }

   private AutoTrapModule.Plan cage(AutoTrapPrediction.Path path, double time, double padding, boolean grounded) {
      AutoTrapModule.Plan best = null;
      int bestCoverage = -1;

      for (double fraction : new double[]{1.0, 0.5, 0.0}) {
         AutoTrapPrediction.Step step = path.at(time * fraction);
         List<BlockPos> cells = this.cageCells(step.box(), grounded);
         AutoTrapModule.Evaluation evaluation = this.evaluate(cells, padding, null, path.steps().getFirst().box());
         int coverage = evaluation.places().size() + evaluation.solid();
         if (!evaluation.places().isEmpty() && coverage > bestCoverage) {
            bestCoverage = coverage;
            best = new AutoTrapModule.Plan(step.feet(), step.time(), false, evaluation.solid(), evaluation.places());
         }
      }

      return best;
   }

   private List<BlockPos> cageCells(AABB box, boolean grounded) {
      int minX = Mth.floor(box.minX + 1.0E-6);
      int maxX = Mth.floor(box.maxX - 1.0E-6);
      int minY = Mth.floor(box.minY + 1.0E-6);
      int maxY = Mth.floor(box.maxY - 1.0E-6);
      int minZ = Mth.floor(box.minZ + 1.0E-6);
      int maxZ = Mth.floor(box.maxZ - 1.0E-6);
      Set<BlockPos> cells = new LinkedHashSet<>();

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            if (!grounded) {
               cells.add(new BlockPos(x, minY - 1, z));
            }

            cells.add(new BlockPos(x, maxY + 1, z));
            int wallMinY = grounded && maxY > minY ? minY + 1 : minY;

            for (int y = wallMinY; y <= maxY; y++) {
               for (Direction direction : Plane.HORIZONTAL) {
                  int nx = x + direction.getStepX();
                  int nz = z + direction.getStepZ();
                  if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
                     cells.add(new BlockPos(nx, y, nz));
                  }
               }
            }
         }
      }

      return new ArrayList<>(cells);
   }

   private AutoTrapModule.Evaluation evaluate(List<BlockPos> cells, double padding, AABB requiredCollision, AABB occupied) {
      List<BlockPos> places = new ArrayList<>();
      int solid = 0;
      boolean complete = true;

      for (BlockPos pos : cells) {
         if (this.speedMineClaims(pos)) {
            complete = false;
         } else {
            BlockState state = mc.level.getBlockState(pos);
            if (!state.canBeReplaced()) {
               boolean blocks = state.getCollisionShape(mc.level, pos)
                  .toAabbs()
                  .stream()
                  .map(shape -> shape.move(pos.getX(), pos.getY(), pos.getZ()))
                  .anyMatch(shape -> requiredCollision == null || shape.intersects(requiredCollision));
               if (blocks) {
                  solid++;
               } else {
                  complete = false;
               }
            } else if (!mc.level.isOutsideBuildHeight(pos)
               && mc.player.isWithinBlockInteractionRange(pos, padding)
               && !mc.player.getBoundingBox().intersects(new AABB(pos))
               && !occupied.intersects(new AABB(pos))
               && PlaceUtil.noEntityCollision(pos)) {
               places.add(pos);
            } else {
               complete = false;
            }
         }
      }

      return new AutoTrapModule.Evaluation(places, solid, complete);
   }

   private boolean speedMineClaims(BlockPos pos) {
      SpeedMineController mine = KetamineClub.moduleManager.getSpeedMine();
      return mine != null && mine.isEnabled() && mine.alreadyBreaking(pos);
   }

   private void clearPlacements() {
      this.wanted.clear();
      this.pendingWall.clear();
      this.wallSentAt = 0L;
      this.sentAt.clear();
      this.sendAttempts.clear();
   }

   private void resetTracking() {
      this.clearPlacements();
      this.trackedTarget = null;
      this.sessionLevel = null;
      this.sessionPlayer = null;
      this.predictions.clear();
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (this.render.getValue()) {
         for (BlockPos pos : this.wanted) {
            RenderUtil.drawBoxFilled(event.getMatrix(), pos, this.fillColor.getValue());
            RenderUtil.drawBox(event.getMatrix(), pos, this.outlineColor.getValue(), this.lineWidth.getValue());
         }
      }
   }

   private Player findTarget() {
      TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
      double maxSq = this.range.getValue() * this.range.getValue();
      Player best = null;
      double bestSq = Double.MAX_VALUE;

      for (Player player : mc.level.players()) {
         if (player != mc.player && player.isAlive() && !player.isRemoved() && (targets == null || targets.isValidPlayerTarget(player))) {
            double distance = mc.player.distanceToSqr(player);
            if (!(distance > maxSq) && !(distance >= bestSq)) {
               bestSq = distance;
               best = player;
            }
         }
      }

      return best;
   }

   private double trackingRangeSquared() {
      double trackingRange = this.range.getValue() + 16.0;
      return trackingRange * trackingRange;
   }

   private int resolveSlot() {
      Result result = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.PLACE_SCOPE);
      return result.found() && result.type() != ResultType.OFFHAND ? result.slot() : -1;
   }

   private int latencyMillis() {
      PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
      return info == null ? 0 : Math.max(0, info.getLatency());
   }

   private static Vec3 serverPosition(Player player) {
      InterpolationHandler interpolation = player.getInterpolation();
      return interpolation == null ? player.position() : interpolation.position();
   }

   private static String fmt(Vec3 value) {
      return String.format("%.2f,%.2f,%.2f", value.x, value.y, value.z);
   }

   private record Evaluation(List<BlockPos> places, int solid, boolean complete) {
   }

   private record MovementUpdate(Vec3 position, boolean teleport) {
   }

   private record Plan(Vec3 feet, double time, boolean wall, int solid, List<BlockPos> places) {
   }
}
