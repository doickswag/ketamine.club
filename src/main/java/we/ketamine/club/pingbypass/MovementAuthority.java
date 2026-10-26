package we.ketamine.club.pingbypass;

final class MovementAuthority {
   private boolean worker;
   private boolean awaitingViewer;
   private int generation;

   MovementAuthority() {
   }

   boolean update(boolean worker) {
      if (this.worker == worker) {
         return false;
      } else {
         this.worker = worker;
         this.awaitingViewer = !worker;
         this.generation++;
         return true;
      }
   }

   boolean worker() {
      return this.worker;
   }

   boolean acceptsViewerPosition() {
      return !this.worker && !this.awaitingViewer;
   }

   int generation() {
      return this.generation;
   }

   void acknowledge(int generation) {
      if (!this.worker && this.generation == generation) {
         this.awaitingViewer = false;
      }
   }

   void reset() {
      this.worker = false;
      this.awaitingViewer = false;
      this.generation = 0;
   }
}
