package we.ketamine.club.features.modules.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.client.HudClientModule;
import we.ketamine.club.features.modules.client.HudModule;

public class MoveHudModule extends HudModule {
   private static final int LEFT_MARGIN = 2;
   private static final int BOTTOM_MARGIN = 2;
   private static final int WHITE = -1;
   private static final int GRAY = -5592406;
   private static final long WINDOW_MS = 1000L;
   private static final int EXPECTED_MOVES_PER_SECOND = 20;
   private long windowStart = System.currentTimeMillis();
   private int windowCount = 0;
   private int lastCount = 0;

   public MoveHudModule() {
      super("Move");
      EVENT_BUS.register(this);
   }

   @Subscribe
   private void onPacketSend(PacketEvent.Send event) {
      if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
         this.rollWindow();
         this.windowCount++;
      }
   }

   private void rollWindow() {
      long now = System.currentTimeMillis();
      if (now - this.windowStart >= 1000L) {
         this.lastCount = this.windowCount;
         this.windowCount = 0;
         this.windowStart = now;
      }
   }

   @Override
   public void render(Render2DEvent event) {
      this.rollWindow();
      GuiGraphicsExtractor ctx = event.getContext();
      int ry = this.bottomAnchor() - 2 - 9;
      HudClientModule hud = KetamineClub.moduleManager.getModuleByClass(HudClientModule.class);
      if (hud != null && hud.isElementEnabled(SpeedHudModule.class)) {
         ry -= 9;
      }

      String value = String.valueOf(this.lastCount);
      String label = "/20 move";
      ctx.text(mc.font, value, 2, ry, -5592406);
      ctx.text(mc.font, label, 2 + mc.font.width(value), ry, -1);
   }
}
