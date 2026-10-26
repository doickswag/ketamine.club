package we.ketamine.club.features.modules.hud;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.features.modules.client.HudModule;

public class SpeedHudModule extends HudModule {
   private static final int LEFT_MARGIN = 2;
   private static final int BOTTOM_MARGIN = 2;
   private static final int WHITE = -1;
   private static final int GRAY = -5592406;
   private int lastPlayerTick = Integer.MIN_VALUE;
   private String cachedValue = "0.0";

   public SpeedHudModule() {
      super("Speed");
   }

   @Override
   public void render(Render2DEvent event) {
      GuiGraphicsExtractor ctx = event.getContext();
      if (this.lastPlayerTick != mc.player.tickCount) {
         this.lastPlayerTick = mc.player.tickCount;
         double dx = mc.player.getX() - mc.player.xo;
         double dz = mc.player.getZ() - mc.player.zo;
         double bps = Math.sqrt(dx * dx + dz * dz) * 20.0;
         this.cachedValue = String.format(Locale.ROOT, "%.1f", bps);
      }

      String value = this.cachedValue;
      String unit = " bps";
      int ry = this.bottomAnchor() - 2 - 9;
      ctx.text(mc.font, value, 2, ry, -5592406);
      ctx.text(mc.font, unit, 2 + mc.font.width(value), ry, -1);
   }
}
