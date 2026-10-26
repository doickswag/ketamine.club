package we.ketamine.club.manager;

import java.util.ArrayDeque;

public final class PlacementWindow {
   private final int limit;
   private final long durationMs;
   private final ArrayDeque<Long> sent = new ArrayDeque<>();

   public PlacementWindow(int limit, long durationMs) {
      if (limit > 0 && durationMs > 0L) {
         this.limit = limit;
         this.durationMs = durationMs;
      } else {
         throw new IllegalArgumentException("Positive placement window required");
      }
   }

   public void prune(long now) {
      while (!this.sent.isEmpty() && now - this.sent.peekFirst() >= this.durationMs) {
         this.sent.removeFirst();
      }
   }

   public int available(long now, int reserved) {
      this.prune(now);
      return Math.max(0, this.limit - Math.clamp((long)reserved, 0, this.limit) - this.sent.size());
   }

   public boolean allows(long now, int count, int reserved) {
      return count >= 0 && count <= this.available(now, reserved);
   }

   public long waitMs(long now, int count, int reserved) {
      this.prune(now);
      int capacity = this.limit - Math.clamp((long)reserved, 0, this.limit);
      if (count >= 0 && count <= capacity) {
         int expire = this.sent.size() + count - capacity;
         if (expire <= 0) {
            return 0L;
         } else {
            for (long stamp : this.sent) {
               if (--expire == 0) {
                  return Math.max(1L, stamp + this.durationMs - now);
               }
            }

            return Long.MAX_VALUE;
         }
      } else {
         return Long.MAX_VALUE;
      }
   }

   public void record(long now) {
      this.sent.addLast(now);
   }

   public void clear() {
      this.sent.clear();
   }
}
