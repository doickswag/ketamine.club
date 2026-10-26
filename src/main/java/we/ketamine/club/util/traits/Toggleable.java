package we.ketamine.club.util.traits;

public interface Toggleable {
   boolean isToggled();

   void enable();

   void disable();

   default void toggle() {
      if (this.isToggled()) {
         this.disable();
      } else {
         this.enable();
      }
   }

   default void setToggled(boolean toggled) {
      if (this.isToggled() != toggled) {
         this.toggle();
      }
   }
}
