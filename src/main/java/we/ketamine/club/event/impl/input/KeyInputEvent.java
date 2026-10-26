package we.ketamine.club.event.impl.input;

import we.ketamine.club.event.Event;

public class KeyInputEvent extends Event {
   private final int key;
   private final int action;

   public KeyInputEvent(int key, int action) {
      this.key = key;
      this.action = action;
   }

   public int getKey() {
      return this.key;
   }

   public int getAction() {
      return this.action;
   }
}
