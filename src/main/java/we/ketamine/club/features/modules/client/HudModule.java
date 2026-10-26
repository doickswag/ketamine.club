package we.ketamine.club.features.modules.client;

import net.minecraft.client.gui.screens.ChatScreen;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.util.traits.Util;

public abstract class HudModule implements Util {
   private static final int CHAT_INPUT_HEIGHT = 14;
   protected static final int SCREEN_MARGIN = 2;
   private final String name;

   public HudModule(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }

   public abstract void render(Render2DEvent var1);

   protected int screenWidth() {
      return mc.getWindow().getGuiScaledWidth();
   }

   protected int screenHeight() {
      return mc.getWindow().getGuiScaledHeight();
   }

   protected int chatOffset() {
      return mc.gui.screen() instanceof ChatScreen ? 14 : 0;
   }

   protected int bottomAnchor() {
      return this.screenHeight() - this.chatOffset();
   }

   protected int lineX(HudPosition pos, int lineWidth) {
      return pos.isLeft() ? 2 : this.screenWidth() - 2 - lineWidth;
   }

   protected int blockTop(HudPosition pos, int ownLines, int linesBelow, int gap) {
      if (pos.isBottom()) {
         return this.bottomAnchor() - 2 - (linesBelow + ownLines) * 9 - gap;
      } else {
         return pos.isTop() ? 2 : this.screenHeight() / 2;
      }
   }
}
