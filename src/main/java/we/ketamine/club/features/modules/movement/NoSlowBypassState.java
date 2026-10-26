package we.ketamine.club.features.modules.movement;

import java.util.Objects;

final class NoSlowBypassState {
   private Object session;
   private int startTick;
   private int lastTick;
   private int lastRemaining;

   NoSlowBypassState() {
   }

   boolean cancelSlowdown(Object session, int tick, int remaining) {
      Objects.requireNonNull(session);
      if (!session.equals(this.session) || tick < this.lastTick || (long)tick - this.lastTick > 1L || remaining > this.lastRemaining) {
         this.session = session;
         this.startTick = tick;
      }

      this.lastTick = tick;
      this.lastRemaining = remaining;
      return (tick - this.startTick & 1) != 0;
   }

   void reset() {
      this.session = null;
      this.startTick = this.lastTick = this.lastRemaining = 0;
   }
}
