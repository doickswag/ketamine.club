package we.ketamine.club.features.modules.hud;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.features.modules.client.HudModule;

public class NotifierHudModule extends HudModule {
   private static final long DEFAULT_DURATION_MS = 3000L;
   private static final long FADE_MS = 400L;
   private static final int MAX_VISIBLE = 5;
   private static final int LINE_GAP = 3;
   private static final int STATS_GAP = 4;
   private static final int STATS_TOP_OFFSET = 56;
   private static final int NAME_CLEARANCE = 11;
   private static final int BUBBLE_HEIGHT = 10;
   private static final int TEXT_COLOR = -1;
   private static final String OPEN = "<< ";
   private static final String SEP = " ";
   private static final String CLOSE = " >>";
   private static final List<NotifierHudModule.Entry> ENTRIES = new CopyOnWriteArrayList<>();

   public NotifierHudModule() {
      super("Notifier");
   }

   public static void push(NotifierHudModule.Icon icon, String message) {
      push(null, icon, Component.literal(message), 3000L);
   }

   public static void push(String key, NotifierHudModule.Icon icon, String message) {
      push(key, icon, Component.literal(message), 3000L);
   }

   public static void push(String key, NotifierHudModule.Icon icon, Component message) {
      push(key, icon, message, 3000L);
   }

   public static void push(String key, NotifierHudModule.Icon icon, Component message, long durationMs) {
      long expiry = System.currentTimeMillis() + durationMs;
      if (key != null) {
         for (NotifierHudModule.Entry e : ENTRIES) {
            if (key.equals(e.key)) {
               e.icon = icon;
               e.message = message;
               e.expiry = expiry;
               return;
            }
         }
      }

      ENTRIES.add(new NotifierHudModule.Entry(key, icon, message, expiry));
   }

   @Override
   public void render(Render2DEvent event) {
      long now = System.currentTimeMillis();
      ENTRIES.removeIf(ex -> ex.expiry <= now);
      if (!ENTRIES.isEmpty()) {
         GuiGraphicsExtractor ctx = event.getContext();
         int lineHeight = 9;
         int lineStride = lineHeight + 3;
         int statsTop = this.screenHeight() - 56;
         if (this.showingBubbles()) {
            statsTop -= 10;
         }

         int anchorY = statsTop - 4 - 11;
         int total = ENTRIES.size();
         int shown = Math.min(total, 5);

         for (int k = 0; k < shown; k++) {
            NotifierHudModule.Entry e = ENTRIES.get(total - 1 - k);
            int topY = anchorY - lineHeight - k * lineStride;
            float alpha = this.fade(e, now);
            this.drawLine(ctx, e, topY, alpha);
         }
      }
   }

   private void drawLine(GuiGraphicsExtractor ctx, NotifierHudModule.Entry e, int y, float alpha) {
      int width = mc.font.width("<< ") + mc.font.width(e.icon.glyph) + mc.font.width(" ") + mc.font.width(e.message) + mc.font.width(" >>");
      int x = (this.screenWidth() - width) / 2;
      int text = this.applyAlpha(-1, alpha);
      int iconColor = this.applyAlpha(e.icon.color, alpha);
      ctx.text(mc.font, "<< ", x, y, text, true);
      x += mc.font.width("<< ");
      ctx.text(mc.font, e.icon.glyph, x, y, iconColor, true);
      x += mc.font.width(e.icon.glyph);
      ctx.text(mc.font, " ", x, y, text, true);
      x += mc.font.width(" ");
      ctx.text(mc.font, e.message, x, y, text, true);
      x += mc.font.width(e.message);
      ctx.text(mc.font, " >>", x, y, text, true);
   }

   private float fade(NotifierHudModule.Entry e, long now) {
      long remaining = e.expiry - now;
      return remaining >= 400L ? 1.0F : Math.max(0.0F, (float)remaining / 400.0F);
   }

   private int applyAlpha(int argb, float alpha) {
      int a = Math.round((argb >>> 24 & 0xFF) * alpha);
      return a << 24 | argb & 16777215;
   }

   private boolean showingBubbles() {
      return mc.player.isEyeInFluid(FluidTags.WATER) || mc.player.getAirSupply() < mc.player.getMaxAirSupply();
   }

   private static final class Entry {
      final String key;
      NotifierHudModule.Icon icon;
      Component message;
      long expiry;

      Entry(String key, NotifierHudModule.Icon icon, Component message, long expiry) {
         this.key = key;
         this.icon = icon;
         this.message = message;
         this.expiry = expiry;
      }
   }

   public static enum Icon {
      DANGER("\u26a0", -22016),
      SKULL("\u2620", -43691),
      INFO("\u2139", -171);

      private final String glyph;
      private final int color;

      private Icon(String glyph, int color) {
         this.glyph = glyph;
         this.color = color;
      }
   }
}
