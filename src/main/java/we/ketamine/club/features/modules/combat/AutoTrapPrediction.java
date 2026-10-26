package we.ketamine.club.features.modules.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class AutoTrapPrediction {
   private static final double TICK_NANOS = 5.0E7;
   private static final long COALESCE_NANOS = 25000000L;
   private static final double MAX_SPEED = 12.0;
   private static final double MIN_SPEED = 0.05;
   private static final double SPEED_EPSILON = 1.0E-7;
   private static final double MAX_ACCELERATION = 0.6;
   private static final double MAX_PATH_DISTANCE = 24.0;
   private static final double EPSILON = 1.0E-6;
   private final List<AutoTrapPrediction.Sample> samples = new ArrayList<>(4);
   private Vec3 position = Vec3.ZERO;
   private long receivedAt;

   AutoTrapPrediction() {
   }

   void reset(Vec3 position, long now) {
      this.samples.clear();
      this.position = position;
      this.receivedAt = now;
   }

   boolean observe(Vec3 position, long now, boolean teleport) {
      if (!finite(position)) {
         return false;
      } else {
         boolean discontinuity = teleport;
         if (!this.samples.isEmpty()) {
            AutoTrapPrediction.Sample previous = this.samples.getLast();
            double elapsed = Math.max(1.0, (now - previous.time()) / 5.0E7);
            discontinuity = teleport | (now < previous.time() || elapsed > 10.0 || position.distanceTo(previous.position()) > 12.0000001 * elapsed);
            if (this.samples.size() >= 2) {
               AutoTrapPrediction.Sample older = this.samples.get(this.samples.size() - 2);
               double previousSpeed = previous.position().distanceTo(older.position()) / ticksBetween(older, previous);
               discontinuity |= position.distanceTo(previous.position()) / elapsed > Math.max(4.0, previousSpeed * 2.5) + 1.0E-7;
            }
         }

         if (discontinuity) {
            this.samples.clear();
         }

         this.position = position;
         this.receivedAt = now;
         if (!this.samples.isEmpty() && now - this.samples.getLast().time() < 25000000L) {
            AutoTrapPrediction.Sample previous = this.samples.getLast();
            this.samples.set(this.samples.size() - 1, new AutoTrapPrediction.Sample(position, previous.time()));
         } else {
            this.samples.add(new AutoTrapPrediction.Sample(position, now));
            if (this.samples.size() > 4) {
               this.samples.removeFirst();
            }
         }

         return discontinuity;
      }
   }

   AutoTrapPrediction.Motion motion(long now) {
      double age = Math.max(0.0, (now - this.receivedAt) / 5.0E7);
      if (this.samples.size() < 2) {
         return new AutoTrapPrediction.Motion(this.position, Vec3.ZERO, Vec3.ZERO, age, false);
      } else {
         AutoTrapPrediction.Sample last = this.samples.getLast();
         AutoTrapPrediction.Sample before = this.samples.get(this.samples.size() - 2);
         double interval = ticksBetween(before, last);
         if (age > Math.max(6.0, interval * 2.0 + 1.0)) {
            return new AutoTrapPrediction.Motion(this.position, Vec3.ZERO, Vec3.ZERO, age, false);
         } else {
            Vec3 velocity = last.position().subtract(before.position()).scale(1.0 / interval);
            Vec3 acceleration = Vec3.ZERO;
            if (velocity.lengthSqr() > 144.0000024) {
               return new AutoTrapPrediction.Motion(this.position, Vec3.ZERO, Vec3.ZERO, age, false);
            } else {
               if (this.samples.size() >= 3 && isMoving(velocity)) {
                  AutoTrapPrediction.Sample older = this.samples.get(this.samples.size() - 3);
                  double previousInterval = ticksBetween(older, before);
                  Vec3 previous = before.position().subtract(older.position()).scale(1.0 / previousInterval);
                  if (isMoving(previous) && velocity.normalize().dot(previous.normalize()) > 0.8) {
                     acceleration = limit(velocity.subtract(previous).scale(1.0 / ((interval + previousInterval) * 0.5)), 0.6);
                     velocity = velocity.scale(0.85).add(previous.scale(0.15));
                  }
               }

               velocity = isMoving(velocity) ? limit(velocity, 12.0) : Vec3.ZERO;
               return new AutoTrapPrediction.Motion(this.position, velocity, acceleration, age, true);
            }
         }
      }
   }

   static double arrivalTicks(AutoTrapPrediction.Motion motion, int pingMillis) {
      return motion.ageTicks() + Math.clamp((long)pingMillis, 0, 500) / 50.0 + 0.5;
   }

   static AutoTrapPrediction.Path trace(
      AutoTrapPrediction.Motion motion, AABB startBox, double duration, boolean grounded, AutoTrapPrediction.Collision collision
   ) {
      List<AutoTrapPrediction.Step> steps = new ArrayList<>();
      Vec3 velocity = grounded ? new Vec3(motion.velocity().x, 0.0, motion.velocity().z) : motion.velocity();
      Vec3 acceleration = grounded ? new Vec3(motion.acceleration().x, 0.0, motion.acceleration().z) : motion.acceleration();
      Vec3 feet = motion.position();
      AABB box = startBox;
      double time = 0.0;
      double distance = 0.0;
      boolean hit = false;
      steps.add(new AutoTrapPrediction.Step(time, feet, startBox, velocity));

      while (time < duration - 1.0E-6 && steps.size() < 256 && (!(velocity.lengthSqr() < 1.0E-6) || !(acceleration.lengthSqr() < 1.0E-6))) {
         double dt = Math.min(duration - time, Math.min(0.25, 0.25 / Math.max(1.0, velocity.length())));
         Vec3 a = acceleration.scale(Math.exp(-time / 1.5));
         Vec3 requested = velocity.scale(dt).add(a.scale(0.5 * dt * dt));
         Vec3 movement = collision.move(box, requested);
         boolean hitX = Math.abs(movement.x - requested.x) > 1.0E-6;
         boolean hitY = Math.abs(movement.y - requested.y) > 1.0E-6;
         boolean hitZ = Math.abs(movement.z - requested.z) > 1.0E-6;
         hit |= hitX || hitY || hitZ;
         Vec3 var24 = limit(velocity.add(a.scale(dt)), 12.0);
         velocity = new Vec3(hitX ? 0.0 : var24.x, hitY ? 0.0 : var24.y, hitZ ? 0.0 : var24.z);
         acceleration = new Vec3(hitX ? 0.0 : acceleration.x, hitY ? 0.0 : acceleration.y, hitZ ? 0.0 : acceleration.z);
         distance += movement.length();
         feet = feet.add(movement);
         box = box.move(movement);
         time += dt;
         steps.add(new AutoTrapPrediction.Step(time, feet, box, velocity));
         if (distance >= 24.0) {
            break;
         }
      }

      return new AutoTrapPrediction.Path(List.copyOf(steps), hit);
   }

   static List<BlockPos> wallCells(AutoTrapPrediction.Step step) {
      AABB box = step.box();
      Vec3 velocity = step.velocity();
      int minX = Mth.floor(box.minX + 1.0E-6);
      int maxX = Mth.floor(box.maxX - 1.0E-6);
      int minY = Mth.floor(box.minY + 1.0E-6);
      int maxY = Mth.floor(box.maxY - 1.0E-6);
      int minZ = Mth.floor(box.minZ + 1.0E-6);
      int maxZ = Mth.floor(box.maxZ - 1.0E-6);
      List<BlockPos> cells = new ArrayList<>(6);
      if (Math.abs(velocity.y) > Math.max(Math.abs(velocity.x), Math.abs(velocity.z))) {
         int y = velocity.y > 0.0 ? maxY : minY;

         for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
               cells.add(new BlockPos(x, y, z));
            }
         }
      } else if (Math.abs(velocity.x) >= Math.abs(velocity.z)) {
         int x = velocity.x > 0.0 ? maxX : minX;

         for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
               cells.add(new BlockPos(x, y, z));
            }
         }
      } else {
         int z = velocity.z > 0.0 ? maxZ : minZ;

         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
               cells.add(new BlockPos(x, y, z));
            }
         }
      }

      return cells;
   }

   static AutoTrapPrediction.Interception intercept(
      AutoTrapPrediction.Path path,
      double arrival,
      double lead,
      Set<BlockPos> retained,
      BiFunction<AutoTrapPrediction.Step, List<BlockPos>, AutoTrapPrediction.Cells> evaluate
   ) {
      AutoTrapPrediction.Interception best = null;
      double bestScore = Double.MAX_VALUE;
      Set<List<BlockPos>> considered = new HashSet<>();

      for (AutoTrapPrediction.Step step : path.steps()) {
         if (!(step.time() < arrival + lead) && isMoving(step.velocity())) {
            List<BlockPos> cells = wallCells(step);
            if (considered.add(cells) && !crossedBeforeArrival(cells, path, arrival)) {
               AutoTrapPrediction.Cells evaluation = evaluate.apply(step, cells);
               if (evaluation.complete() && !evaluation.places().isEmpty()) {
                  long reused = cells.stream().filter(retained::contains).count();
                  double score = step.time() + evaluation.places().size() * 0.04 - 0.35 * reused / cells.size();
                  if (!(score >= bestScore)) {
                     List<BlockPos> places = new ArrayList<>(evaluation.places());
                     places.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(step.box().getCenter())));
                     bestScore = score;
                     best = new AutoTrapPrediction.Interception(step, List.copyOf(places), evaluation.solid());
                  }
               }
            }
         }
      }

      return best;
   }

   private static boolean crossedBeforeArrival(List<BlockPos> cells, AutoTrapPrediction.Path path, double arrival) {
      List<AABB> obstacles = cells.stream().<AABB>map(AABB::new).toList();

      for (int i = 1; i < path.steps().size(); i++) {
         AutoTrapPrediction.Step from = path.steps().get(i - 1);
         if (from.time() > arrival) {
            break;
         }

         AutoTrapPrediction.Step to = path.steps().get(i);
         AABB end = to.time() > arrival ? path.at(arrival).box() : to.box();
         Vec3 movement = end.getCenter().subtract(from.box().getCenter());
         if (from.box().collidedAlongVector(movement, obstacles)) {
            return true;
         }

         if (to.time() >= arrival) {
            break;
         }
      }

      return false;
   }

   static boolean crossesAfter(AutoTrapPrediction.Path path, Set<BlockPos> cells, double time) {
      AutoTrapPrediction.Step last = path.steps().getLast();
      if (time > last.time() && isMoving(last.velocity())) {
         return false;
      } else {
         List<AABB> obstacles = cells.stream().<AABB>map(AABB::new).toList();
         AutoTrapPrediction.Step previous = path.at(time);

         for (BlockPos pos : cells) {
            if (previous.box().intersects(new AABB(pos))) {
               return true;
            }
         }

         for (AutoTrapPrediction.Step step : path.steps()) {
            if (!(step.time() <= time)) {
               if (previous.box().collidedAlongVector(step.feet().subtract(previous.feet()), obstacles)) {
                  return true;
               }

               previous = step;
            }
         }

         return false;
      }
   }

   private static double ticksBetween(AutoTrapPrediction.Sample from, AutoTrapPrediction.Sample to) {
      return Math.max(1.0, (to.time() - from.time()) / 5.0E7);
   }

   static boolean isMoving(Vec3 velocity) {
      return velocity.lengthSqr() >= 0.00249999000001;
   }

   private static Vec3 limit(Vec3 vector, double maximum) {
      return vector.lengthSqr() > maximum * maximum ? vector.normalize().scale(maximum) : vector;
   }

   private static boolean finite(Vec3 position) {
      return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
   }

   record Cells(List<BlockPos> places, int solid, boolean complete) {
   }

   @FunctionalInterface
   interface Collision {
      Vec3 move(AABB var1, Vec3 var2);
   }

   record Interception(AutoTrapPrediction.Step step, List<BlockPos> places, int solid) {
   }

   record Motion(Vec3 position, Vec3 velocity, Vec3 acceleration, double ageTicks, boolean reliable) {
   }

   record Path(List<AutoTrapPrediction.Step> steps, boolean collided) {
      AutoTrapPrediction.Step at(double time) {
         for (int i = 1; i < this.steps.size(); i++) {
            AutoTrapPrediction.Step after = this.steps.get(i);
            if (!(after.time() < time)) {
               AutoTrapPrediction.Step before = this.steps.get(i - 1);
               double fraction = (time - before.time()) / (after.time() - before.time());
               Vec3 feet = before.feet().lerp(after.feet(), fraction);
               return new AutoTrapPrediction.Step(time, feet, before.box().move(feet.subtract(before.feet())), after.velocity());
            }
         }

         return this.steps.getLast();
      }
   }

   private record Sample(Vec3 position, long time) {
   }

   record Step(double time, Vec3 feet, AABB box, Vec3 velocity) {
   }
}
