package we.ketamine.club.event.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import we.ketamine.club.event.Event;

public class Render3DEvent extends Event {
   private final PoseStack matrix;
   private final float delta;

   public Render3DEvent(PoseStack matrix, float delta) {
      this.matrix = matrix;
      this.delta = delta;
   }

   public PoseStack getMatrix() {
      return this.matrix;
   }

   public float getDelta() {
      return this.delta;
   }
}
