package we.ketamine.club.pingbypass;

import java.util.NavigableMap;
import java.util.TreeMap;

final class PredictionSequenceMap {
   private static final int MAX_PENDING = 8192;
   private final NavigableMap<Integer, Integer> pending = new TreeMap<>();
   private int workerAcknowledged = -1;
   private int viewerAcknowledged = -1;

   PredictionSequenceMap() {
   }

   synchronized int register(int workerSequence, int viewerSequence) {
      if (workerSequence >= 0 && viewerSequence >= 0) {
         if (workerSequence <= this.workerAcknowledged) {
            this.viewerAcknowledged = Math.max(this.viewerAcknowledged, viewerSequence);
         } else {
            if (this.pending.size() >= 8192 && !this.pending.containsKey(workerSequence)) {
               throw new IllegalStateException("Too many unacknowledged PingBypass actions");
            }

            this.pending.merge(workerSequence, viewerSequence, Math::max);
         }

         return this.viewerAcknowledged;
      } else {
         throw new IllegalArgumentException("Prediction sequences must be nonnegative");
      }
   }

   synchronized int acknowledge(int workerSequence) {
      this.workerAcknowledged = Math.max(this.workerAcknowledged, workerSequence);

      while (!this.pending.isEmpty() && this.pending.firstKey() <= this.workerAcknowledged) {
         this.viewerAcknowledged = Math.max(this.viewerAcknowledged, this.pending.pollFirstEntry().getValue());
      }

      return this.viewerAcknowledged;
   }

   synchronized void reset() {
      this.pending.clear();
      this.workerAcknowledged = -1;
      this.viewerAcknowledged = -1;
   }
}
