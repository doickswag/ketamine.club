package we.ketamine.club.event.impl.network;

import we.ketamine.club.event.Event;

public class ChatEvent extends Event {
   private final String content;

   public ChatEvent(String content) {
      this.content = content;
   }

   public String getMessage() {
      return this.content;
   }
}
