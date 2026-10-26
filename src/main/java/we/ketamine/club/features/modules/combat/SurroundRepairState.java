package we.ketamine.club.features.modules.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class SurroundRepairState {
   private final Set<Long> solid = new HashSet<>();
   private final Map<Long, SurroundRepairState.Attempt> pending = new HashMap<>();
   private final Set<Long> queued = new HashSet<>();

   SurroundRepairState() {
   }

   boolean blockUpdate(long pos, boolean air) {
      if (!air) {
         this.solid.add(pos);
         this.pending.remove(pos);
         this.queued.remove(pos);
         return false;
      } else if (this.solid.remove(pos)) {
         this.pending.remove(pos);
         return true;
      } else {
         SurroundRepairState.Attempt attempt = this.pending.get(pos);
         if (attempt != null) {
            attempt.rejected = true;
         }

         return false;
      }
   }

   boolean ready(long pos, long now) {
      SurroundRepairState.Attempt attempt = this.pending.get(pos);
      return attempt == null || now >= attempt.retryAt;
   }

   void sent(long pos, long now, long confirmationMs) {
      this.queued.remove(pos);
      this.pending.put(pos, new SurroundRepairState.Attempt(now, now + confirmationMs));
   }

   void queued(long pos) {
      this.queued.add(pos);
   }

   void discarded(long pos) {
      this.queued.remove(pos);
   }

   boolean isQueued(long pos) {
      return this.queued.contains(pos);
   }

   boolean hasPending(long pos) {
      return this.pending.containsKey(pos);
   }

   void unblockedAirCorrection(long pos) {
      SurroundRepairState.Attempt attempt = this.pending.get(pos);
      if (attempt != null && attempt.rejected) {
         attempt.retryAt = Math.min(attempt.retryAt, attempt.sentAt + 25L);
      }
   }

   void crystalRemoved(long pos) {
      SurroundRepairState.Attempt attempt = this.pending.get(pos);
      if (attempt != null && attempt.rejected) {
         this.pending.remove(pos);
      }
   }

   void crystalBlocking(long pos) {
      SurroundRepairState.Attempt attempt = this.pending.get(pos);
      if (attempt != null) {
         attempt.rejected = true;
         attempt.retryAt = Math.min(attempt.retryAt, attempt.sentAt + 25L);
      }
   }

   void retain(Set<Long> positions) {
      this.solid.retainAll(positions);
      this.pending.keySet().retainAll(positions);
      this.queued.retainAll(positions);
   }

   void reset() {
      this.solid.clear();
      this.pending.clear();
      this.queued.clear();
   }

   SurroundRepairState.Snapshot snapshot(long pos, long now) {
      SurroundRepairState.Attempt attempt = this.pending.get(pos);
      return new SurroundRepairState.Snapshot(
         this.solid.contains(pos),
         this.queued.contains(pos),
         attempt != null,
         attempt != null && attempt.rejected,
         attempt == null ? -1L : Math.max(0L, now - attempt.sentAt),
         attempt == null ? 0L : Math.max(0L, attempt.retryAt - now)
      );
   }

   private static final class Attempt {
      final long sentAt;
      long retryAt;
      boolean rejected;

      Attempt(long sentAt, long retryAt) {
         this.sentAt = sentAt;
         this.retryAt = retryAt;
      }
   }

   record Snapshot(boolean solid, boolean queued, boolean pending, boolean rejected, long ageMs, long retryInMs) {
   }
}
