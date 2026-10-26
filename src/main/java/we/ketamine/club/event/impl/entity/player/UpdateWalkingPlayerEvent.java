package we.ketamine.club.event.impl.entity.player;

import we.ketamine.club.event.Event;
import we.ketamine.club.event.Stage;

public class UpdateWalkingPlayerEvent extends Event {
   private final Stage stage;

   public UpdateWalkingPlayerEvent(Stage stage) {
      this.stage = stage;
   }

   public Stage getStage() {
      return this.stage;
   }
}
