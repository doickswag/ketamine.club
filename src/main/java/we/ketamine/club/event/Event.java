package we.ketamine.club.event;

public class Event {
   private boolean cancelled;

   public Event() {
   }

   public boolean isCancelled() {
      return this.cancelled;
   }

   public void cancel() {
      this.cancelled = true;
   }
}
