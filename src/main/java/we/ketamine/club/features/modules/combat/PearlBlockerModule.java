package we.ketamine.club.features.modules.combat;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.PlaceUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;
import we.ketamine.club.util.render.RenderUtil;

public class PearlBlockerModule extends Module {
   private final Setting<Boolean> render = this.bool("Render", true).setPage("Render");
   private final Setting<Float> fadeTime = this.num("FadeTime", 1.0F, 0.05F, 2.0F).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 255, 0, 0, 44).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 255, 0, 0, 44).setPage("Render");
   private final Setting<Boolean> debug = this.bool("Debug", false).setPage("Extra");
   private final Set<Integer> handled = new HashSet<>();
   private final Map<BlockPos, Long> renderMap = new HashMap<>();
   private static final int MAX_FORWARD_TICKS = 60;
   private static final int MIN_PLACEMENT_LEAD_TICKS = 3;
   private static final int MIN_REMAINING_FLIGHT_TICKS = 2;
   private static final double GRAVITY = 0.03;
   private static final double AIR_DRAG = 0.99;
   private static final double WATER_DRAG = 0.8;

   public PearlBlockerModule() {
      super("PearlBlocker", "Catches enemy pearls by placing obsidian in their flight path.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      this.handled.clear();
      this.renderMap.clear();
   }

   @Override
   public void onDisable() {
      this.handled.clear();
      this.renderMap.clear();
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         Result obs = InventoryUtil.find(Items.OBSIDIAN, InventoryUtil.PLACE_SCOPE);
         if (obs.found() && obs.type() != ResultType.OFFHAND) {
            int obsSlot = obs.slot();
            long now = System.currentTimeMillis();
            List<PearlBlockerModule.Interception> interceptions = new ArrayList<>();

            for (Entity e : mc.level.entitiesForRendering()) {
               if (e instanceof ThrownEnderpearl pearl
                  && !pearl.isRemoved()
                  && pearl.getOwner() instanceof Player owner
                  && owner != mc.player
                  && !KetamineClub.friendManager.isFriend(owner)
                  && (!pearl.isInWater() || !(pearl.getDeltaMovement().length() < 0.015))) {
                  int id = pearl.getId();
                  if (!this.handled.contains(id)) {
                     PearlBlockerModule.PredictionTrace trace = this.debug.getValue() ? new PearlBlockerModule.PredictionTrace() : null;
                     PearlBlockerModule.Interception candidate = this.findInterception(pearl, trace);
                     if (trace != null) {
                        this.logPrediction(pearl, candidate, trace);
                     }

                     if (candidate != null) {
                        interceptions.add(candidate);
                     }
                  }
               }
            }

            interceptions.sort((a, b) -> {
               int remaining = Integer.compare(b.remainingTicks, a.remainingTicks);
               if (remaining != 0) {
                  return remaining;
               } else {
                  int arrival = Integer.compare(a.arrivalTicks, b.arrivalTicks);
                  return arrival != 0 ? arrival : Integer.compare(a.pearl.tickCount, b.pearl.tickCount);
               }
            });

            for (PearlBlockerModule.Interception interception : interceptions) {
               boolean sent = KetamineClub.placementManager.placeDirect(interception.pos, interception.face, obsSlot);
               if (this.debug.getValue()) {
                  KetamineClub.LOGGER
                     .info(
                        "[PearlBlocker] place id={} pos={} face={} arrival={} remaining={} sent={}",
                        interception.pearl.getId(),
                        interception.pos,
                        interception.face,
                        interception.arrivalTicks,
                        interception.remainingTicks,
                        sent
                     );
               }

               if (sent) {
                  this.handled.add(interception.pearl.getId());
                  this.renderMap.put(interception.pos, now);
                  break;
               }
            }

            this.handled.removeIf(id -> {
               Entity found = mc.level.getEntity(id);
               return found == null || found.isRemoved();
            });
            long fadeMs = (long)(this.fadeTime.getValue() * 1000.0F);
            this.renderMap.entrySet().removeIf(ex -> now - ex.getValue() > fadeMs);
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
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
      }
   }

   private PearlBlockerModule.Interception findInterception(ThrownEnderpearl pearl, PearlBlockerModule.PredictionTrace trace) {
      Vec3 pos = pearl.position();
      Vec3 vel = pearl.getDeltaMovement();
      boolean inWater = pearl.isInWater();
      PearlBlockerModule.Interception candidate = null;

      for (int t = 0; t < 60; t++) {
         vel = stepVelocity(vel, inWater);
         Vec3 next = pos.add(vel);
         HitResult collision = this.findCollision(pearl, pos, next, t == 0);
         Vec3 pathEnd = collision.getType() == Type.MISS ? next : collision.getLocation();
         int arrivalTicks = t + 1;
         if (candidate == null && arrivalTicks >= 3) {
            PearlBlockerModule.PlacementCell cell = this.findPlacementCell(pos, pathEnd, trace);
            if (cell != null) {
               candidate = new PearlBlockerModule.Interception(pearl, cell.pos, cell.face, arrivalTicks, 0);
               if (trace != null) {
                  trace.candidatePos = cell.pos;
                  trace.candidateFace = cell.face;
                  trace.candidateTick = arrivalTicks;
               }
            }
         }

         if (collision.getType() != Type.MISS) {
            if (trace != null) {
               trace.impactType = collision.getType();
               trace.impactPos = collision.getLocation();
               trace.impactTick = arrivalTicks;
            }

            if (candidate == null) {
               if (trace != null) {
                  trace.result = "no_candidate_before_impact";
               }

               return null;
            }

            int remainingTicks = arrivalTicks - candidate.arrivalTicks;
            if (remainingTicks < 2) {
               if (trace != null) {
                  trace.result = "candidate_too_close_to_impact";
               }

               return null;
            }

            if (trace != null) {
               trace.result = "ready";
            }

            return new PearlBlockerModule.Interception(pearl, candidate.pos, candidate.face, candidate.arrivalTicks, remainingTicks);
         }

         pos = next;
         inWater = mc.level.getFluidState(BlockPos.containing(next)).is(FluidTags.WATER);
         if (next.y < mc.level.getMinY() - 5) {
            if (trace != null) {
               trace.result = "below_world";
            }

            return null;
         }
      }

      if (trace != null) {
         trace.result = "no_impact_within_horizon";
      }

      return null;
   }

   private HitResult findCollision(ThrownEnderpearl pearl, Vec3 from, Vec3 to, boolean includeEntities) {
      HitResult blockHit = mc.level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, pearl));
      if (!includeEntities) {
         return blockHit;
      } else {
         AABB predictedBox = pearl.getBoundingBox().move(from.subtract(pearl.position()));
         HitResult entityHit = ProjectileUtil.getEntityHitResult(
            mc.level,
            pearl,
            from,
            to,
            predictedBox.expandTowards(to.subtract(from)).inflate(1.0),
            entity -> entity != pearl.getOwner() && entity.isAlive() && !entity.isSpectator() && entity.isPickable()
         );
         if (entityHit == null) {
            return blockHit;
         } else {
            return blockHit.getType() != Type.MISS && !(from.distanceToSqr(entityHit.getLocation()) < from.distanceToSqr(blockHit.getLocation()))
               ? blockHit
               : entityHit;
         }
      }
   }

   private PearlBlockerModule.PlacementCell findPlacementCell(Vec3 from, Vec3 to, PearlBlockerModule.PredictionTrace trace) {
      return (PearlBlockerModule.PlacementCell)BlockGetter.traverseBlocks(from, to, this, (module, pos) -> {
         if (trace != null) {
            trace.visitedCells++;
         }

         if (mc.level.isOutsideBuildHeight(pos)) {
            if (trace != null) {
               trace.outsideBuildHeight++;
            }

            return null;
         } else if (!mc.level.getBlockState(pos).canBeReplaced()) {
            if (trace != null) {
               trace.notReplaceable++;
            }

            return null;
         } else if (Vec3.atCenterOf(pos).distanceTo(mc.player.getEyePosition()) > 5.154) {
            if (trace != null) {
               trace.outOfReach++;
            }

            return null;
         } else if (mc.player.getBoundingBox().intersects(new AABB(pos))) {
            if (trace != null) {
               trace.playerCollision++;
            }

            return null;
         } else if (!PlaceUtil.canPlace(pos)) {
            if (trace != null) {
               trace.entityCollision++;
            }

            return null;
         } else if (!mc.level.getBlockState(pos.below()).getCollisionShape(mc.level, pos.below()).isEmpty()) {
            if (trace != null) {
               trace.grounded++;
            }

            return null;
         } else {
            Direction face = KetamineClub.placementManager.getPlaceSide(pos);
            if (face == Direction.DOWN) {
               if (trace != null) {
                  trace.floorSupport++;
               }

               return null;
            } else {
               if (trace != null) {
                  trace.validCells++;
                  if (face == null) {
                     trace.airPlaceCells++;
                  }
               }

               return new PearlBlockerModule.PlacementCell(pos.immutable(), face);
            }
         }
      }, module -> null);
   }

   private void logPrediction(ThrownEnderpearl pearl, PearlBlockerModule.Interception interception, PearlBlockerModule.PredictionTrace trace) {
      Vec3 pos = pearl.position();
      Vec3 vel = pearl.getDeltaMovement();
      KetamineClub.LOGGER
         .info(
            "[PearlBlocker] id={} owner={} age={} pos=({},{},{}) vel=({},{},{}) speed={} water={} result={} candidate={} face={} arrival={} impact={} impactPos={} impactTick={} remaining={} cells={} valid={} air={} reject[outside={},solid={},reach={},player={},entity={},ground={},floor={}]",
            new Object[]{
               pearl.getId(),
               pearl.getOwner() == null ? "unknown" : pearl.getOwner().getName().getString(),
               pearl.tickCount,
               format(pos.x),
               format(pos.y),
               format(pos.z),
               format(vel.x),
               format(vel.y),
               format(vel.z),
               format(vel.length()),
               pearl.isInWater(),
               trace.result,
               trace.candidatePos,
               trace.candidateFace,
               trace.candidateTick,
               trace.impactType,
               trace.impactPos == null ? null : formatVec(trace.impactPos),
               trace.impactTick,
               interception == null ? -1 : interception.remainingTicks,
               trace.visitedCells,
               trace.validCells,
               trace.airPlaceCells,
               trace.outsideBuildHeight,
               trace.notReplaceable,
               trace.outOfReach,
               trace.playerCollision,
               trace.entityCollision,
               trace.grounded,
               trace.floorSupport
            }
         );
   }

   private static String formatVec(Vec3 vec) {
      return "(" + format(vec.x) + "," + format(vec.y) + "," + format(vec.z) + ")";
   }

   private static String format(double value) {
      return String.format(Locale.ROOT, "%.3f", value);
   }

   private static Vec3 stepVelocity(Vec3 vel, boolean inWater) {
      double drag = inWater ? 0.8 : 0.99;
      return new Vec3(vel.x * drag, (vel.y - 0.03) * drag, vel.z * drag);
   }

   private static Color withAlpha(Color c, int a) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), Mth.clamp(a, 0, 255));
   }

   private record Interception(ThrownEnderpearl pearl, BlockPos pos, Direction face, int arrivalTicks, int remainingTicks) {
   }

   private record PlacementCell(BlockPos pos, Direction face) {
   }

   private static final class PredictionTrace {
      private String result = "searching";
      private BlockPos candidatePos;
      private Direction candidateFace;
      private int candidateTick = -1;
      private Type impactType = Type.MISS;
      private Vec3 impactPos;
      private int impactTick = -1;
      private int visitedCells;
      private int validCells;
      private int airPlaceCells;
      private int outsideBuildHeight;
      private int notReplaceable;
      private int outOfReach;
      private int playerCollision;
      private int entityCollision;
      private int grounded;
      private int floorSupport;

      private PredictionTrace() {
      }
   }
}
