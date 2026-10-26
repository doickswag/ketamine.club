package we.ketamine.club.util.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import we.ketamine.club.util.traits.Util;

public final class MatrixCapture {
   public static Matrix4f projection;
   public static Matrix4f view;
   private static Matrix4f combined;

   public MatrixCapture() {
   }

   public static void capture(Matrix4f newProjection, Matrix4f newView) {
      projection = newProjection;
      view = newView;
      combined = projection.mul(view, new Matrix4f());
   }

   public static float[] worldToScreen(double worldX, double worldY, double worldZ) {
      if (combined == null) {
         return null;
      } else {
         Vec3 camPos = Util.mc.gameRenderer.mainCamera().position();
         Vector4f point = new Vector4f((float)(worldX - camPos.x()), (float)(worldY - camPos.y()), (float)(worldZ - camPos.z()), 1.0F);
         combined.transform(point);
         if (point.w <= 0.0F) {
            return null;
         } else {
            float ndcX = point.x / point.w;
            float ndcY = point.y / point.w;
            if (!(ndcX < -1.1F) && !(ndcX > 1.1F) && !(ndcY < -1.1F) && !(ndcY > 1.1F)) {
               int guiW = Util.mc.getWindow().getGuiScaledWidth();
               int guiH = Util.mc.getWindow().getGuiScaledHeight();
               return new float[]{(ndcX + 1.0F) / 2.0F * guiW, (1.0F - ndcY) / 2.0F * guiH};
            } else {
               return null;
            }
         }
      }
   }
}
