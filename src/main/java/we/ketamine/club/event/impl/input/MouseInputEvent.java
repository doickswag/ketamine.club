package we.ketamine.club.event.impl.input;

import we.ketamine.club.event.Event;

public class MouseInputEvent extends Event {
   private final int button;
   private final int action;

   public MouseInputEvent(int button, int action) {
      this.button = button;
      this.action = action;
   }

   public int getButton() {
      return this.button;
   }

   public int getAction() {
      return this.action;
   }
}
