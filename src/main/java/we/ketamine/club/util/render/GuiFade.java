package we.ketamine.club.util.render;

public class GuiFade {
   public static float alpha = 1.0F;

   public GuiFade() {
   }

   public static int apply(int color) {
      int a = (int)((color >> 24 & 0xFF) * alpha);
      return color & 16777215 | a << 24;
   }
}
