package we.ketamine.club.pingbypass;

import net.minecraft.world.phys.Vec3;

final class MotionInterpolator {
   private static final long BLEND_NANOS = 50000000L;
   private static final long STALE_NANOS = 250000000L;
   private static final double MAX_STEP_SQUARED = 64.0;
   private Vec3 from;
   private Vec3 target;
   private long started;
   private long received;

   MotionInterpolator() {
   }

   void update(Vec3 previous, Vec3 next, long now) {
      Vec3 origin = this.target == null ? previous : this.sample(now);
      boolean snap = this.target != null && now - this.received > 250000000L || next.distanceToSqr(this.target == null ? previous : this.target) > 64.0;
      this.received = now;
      if (snap || !next.equals(this.target)) {
         this.from = snap ? next : origin;
         this.target = next;
         this.started = now;
      }
   }

   Vec3 sample(long now) {
      if (this.target == null) {
         return null;
      } else {
         double alpha = Math.clamp((now - this.started) / 5.0E7, 0.0, 1.0);
         return this.from.lerp(this.target, alpha);
      }
   }

   void reset() {
      this.from = this.target = null;
      this.started = this.received = 0L;
   }
}
