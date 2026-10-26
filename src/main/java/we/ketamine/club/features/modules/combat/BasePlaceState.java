package we.ketamine.club.features.modules.combat;

import net.minecraft.core.BlockPos;

final class BasePlaceState {
   private BasePlaceState.Pending pending;
   private BlockPos activeBase;
   private long lastSend = Long.MIN_VALUE;

   BasePlaceState() {
   }

   BasePlaceState.Pending pending() {
      return this.pending;
   }

   BlockPos activeBase() {
      return this.activeBase;
   }

   void activeBase(BlockPos pos) {
      this.activeBase = pos == null ? null : pos.immutable();
   }

   boolean update(long now, boolean confirmed) {
      if (this.pending == null) {
         return false;
      } else {
         if (confirmed) {
            if (this.pending.obsidian()) {
               this.activeBase = this.pending.pos();
            }

            this.pending = null;
         } else if (now >= this.pending.deadline()) {
            this.pending = null;
         }

         return this.pending != null;
      }
   }

   boolean ready(long now, long delay) {
      return this.pending == null && (this.lastSend == Long.MIN_VALUE || now - this.lastSend >= delay);
   }

   void sent(BlockPos pos, boolean obsidian, long now, long timeout) {
      if (this.pending != null) {
         throw new IllegalStateException("A support placement is already pending");
      } else {
         this.pending = new BasePlaceState.Pending(pos.immutable(), obsidian, now + timeout);
         this.lastSend = now;
      }
   }

   void reset() {
      this.pending = null;
      this.activeBase = null;
      this.lastSend = Long.MIN_VALUE;
   }

   record Pending(BlockPos pos, boolean obsidian, long deadline) {
   }
}
