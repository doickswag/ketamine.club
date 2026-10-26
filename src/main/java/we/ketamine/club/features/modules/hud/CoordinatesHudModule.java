package we.ketamine.club.features.modules.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.Level;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.features.modules.client.HudClientModule;
import we.ketamine.club.features.modules.client.HudModule;
import we.ketamine.club.features.modules.client.HudPosition;

public class CoordinatesHudModule extends HudModule {
   private static final int WHITE = -1;
   private static final int GRAY = -5592406;

   public CoordinatesHudModule() {
      super("Coordinates");
   }

   @Override
   public void render(Render2DEvent event) {
      GuiGraphicsExtractor ctx = event.getContext();
      int x = (int)Math.floor(mc.player.getX());
      int y = (int)Math.floor(mc.player.getY());
      int z = (int)Math.floor(mc.player.getZ());
      boolean nether = mc.level.dimension().equals(Level.NETHER);
      boolean end = mc.level.dimension().equals(Level.END);
      String main = x + ", " + y + ", " + z + (end ? "" : " ");
      String other;
      if (end) {
         other = "";
      } else if (nether) {
         other = "[" + x * 8 + ", " + z * 8 + "]";
      } else {
         other = "[" + Math.floorDiv(x, 8) + ", " + Math.floorDiv(z, 8) + "]";
      }

      int totalWidth = mc.font.width(main) + mc.font.width(other);
      HudClientModule hudClient = KetamineClub.moduleManager.getModuleByClass(HudClientModule.class);
      HudPosition pos = hudClient != null ? hudClient.positionOf(this) : HudPosition.BOTTOM_RIGHT;
      int linesBelow = hudClient != null ? hudClient.linesBelow(this) : 0;
      int rx = this.lineX(pos, totalWidth);
      int ry = this.blockTop(pos, 1, linesBelow, 0);
      ctx.text(mc.font, main, rx, ry, -1);
      if (!other.isEmpty()) {
         ctx.text(mc.font, other, rx + mc.font.width(main), ry, -5592406);
      }
   }
}
