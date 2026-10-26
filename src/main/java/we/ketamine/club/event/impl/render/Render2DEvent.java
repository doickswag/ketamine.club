package we.ketamine.club.event.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import we.ketamine.club.event.Event;

public class Render2DEvent extends Event {
   private final GuiGraphicsExtractor context;
   private final float delta;

   public Render2DEvent(GuiGraphicsExtractor context, float delta) {
      this.context = context;
      this.delta = delta;
   }

   public GuiGraphicsExtractor getContext() {
      return this.context;
   }

   public float getDelta() {
      return this.delta;
   }
}
