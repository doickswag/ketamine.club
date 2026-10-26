package we.ketamine.club.features.gui.items;

import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import we.ketamine.club.features.Feature;
import we.ketamine.club.util.render.GuiFade;
import we.ketamine.club.util.render.font.Fonts;

public class Item extends Feature {
   public static GuiGraphicsExtractor context;
   protected float x;
   protected float y;
   protected int width;
   protected int height;
   private boolean hidden;
   private long scrollStartTime = 0L;

   public Item(String name) {
      super(name);
   }

   public void setLocation(float x, float y) {
      this.x = x;
      this.y = y;
   }

   public void drawScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
   }

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
   }

   public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
   }

   public void update() {
   }

   public void onKeyTyped(String typedChar, int keyCode) {
   }

   public void onKeyPressed(int key) {
   }

   public void keyActivate() {
   }

   public int getRowHeight() {
      return this.getHeight();
   }

   public float getX() {
      return this.x;
   }

   public float getY() {
      return this.y;
   }

   public int getWidth() {
      return this.width;
   }

   public void setWidth(int width) {
      this.width = width;
   }

   public int getHeight() {
      return this.height;
   }

   public void setHeight(int height) {
      this.height = height;
   }

   public boolean isHidden() {
      return this.hidden;
   }

   public boolean setHidden(boolean hidden) {
      this.hidden = hidden;
      return this.hidden;
   }

   protected void drawScrollableString(String text, double x, double y, int color, int maxWidth, boolean isHovering) {
      int textWidth = Fonts.width(text);
      if (textWidth <= maxWidth) {
         this.scrollStartTime = 0L;
         this.drawString(text, x, y, color);
      } else if (!isHovering) {
         this.scrollStartTime = 0L;
         this.drawString(this.truncateText(text, maxWidth), x, y, color);
      } else {
         if (this.scrollStartTime == 0L) {
            this.scrollStartTime = System.currentTimeMillis();
         }

         long elapsed = System.currentTimeMillis() - this.scrollStartTime;
         float overflow = textWidth - maxWidth;
         long scrollDurationMs = (long)(overflow / 0.03F);
         long pause = 600L;
         long cycle = pause + scrollDurationMs + pause + scrollDurationMs;
         long t = elapsed % cycle;
         float offset;
         if (t < pause) {
            offset = 0.0F;
         } else if (t < pause + scrollDurationMs) {
            offset = (float)(t - pause) * 0.03F;
         } else if (t < pause + scrollDurationMs + pause) {
            offset = overflow;
         } else {
            offset = overflow - (float)(t - pause - scrollDurationMs - pause) * 0.03F;
         }

         context.enableScissor((int)x, (int)y - 2, (int)(x + maxWidth + 2.0), (int)(y + Fonts.lineHeight() + 2.0));
         this.drawString(text, x - offset, y, color);
         context.disableScissor();
      }
   }

   private String truncateText(String text, int maxWidth) {
      String ellipsis = "...";
      int ellipsisWidth = Fonts.width(ellipsis);
      if (maxWidth <= ellipsisWidth) {
         return ellipsis;
      } else {
         int targetWidth = maxWidth - ellipsisWidth;
         StringBuilder sb = new StringBuilder();

         for (char c : text.toCharArray()) {
            if (Fonts.width(sb.toString() + c) > targetWidth) {
               break;
            }

            sb.append(c);
         }

         return sb + ellipsis;
      }
   }

   protected void drawString(String text, double x, double y, Color color) {
      this.drawString(text, x, y, color.hashCode());
   }

   protected void drawString(String text, double x, double y, int color) {
      Fonts.drawString(context, text, (float)x, (float)y, GuiFade.apply(color));
   }

   public boolean isHovering(int mouseX, int mouseY) {
      return false;
   }

   public String getPage() {
      return "General";
   }
}
