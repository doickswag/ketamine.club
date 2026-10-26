package we.ketamine.club.features.modules.client;

public enum HudPosition {
   TOP_RIGHT,
   TOP_LEFT,
   BOTTOM_RIGHT,
   BOTTOM_LEFT,
   CENTER_RIGHT,
   CENTER_LEFT;

   private HudPosition() {
   }

   public boolean isLeft() {
      return this == TOP_LEFT || this == BOTTOM_LEFT || this == CENTER_LEFT;
   }

   public boolean isBottom() {
      return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
   }

   public boolean isTop() {
      return this == TOP_LEFT || this == TOP_RIGHT;
   }
}
