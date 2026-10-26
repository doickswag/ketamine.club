package we.ketamine.club.features.gui.items;

import java.awt.Color;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import we.ketamine.club.features.gui.GuiTheme;
import we.ketamine.club.features.gui.Widget;
import we.ketamine.club.util.render.RenderUtil;

public class SearchBar extends Item {
   public static String QUERY = "";
   private final TextBox textBox = new TextBox().placeholder("Search...").onChange(s -> QUERY = s.toLowerCase(Locale.ROOT));

   public SearchBar() {
      super("Search");
      this.height = 14;
   }

   public boolean isFocused() {
      return this.textBox.isFocused();
   }

   public static boolean hasQuery() {
      return QUERY != null && !QUERY.isEmpty();
   }

   public static boolean matches(String name) {
      return !hasQuery() ? true : name.toLowerCase(Locale.ROOT).contains(QUERY);
   }

   public static void unfocus() {
      TextBox.unfocusAll();
   }

   @Override
   public int getHeight() {
      return 14;
   }

   @Override
   public void drawScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHovering(mouseX, mouseY);
      Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(70, 75, 82);
      float x1 = this.x;
      float y1 = this.y;
      float x2 = this.x + this.width;
      float y2 = this.y + this.height - 1.0F;
      this.textBox.renderPill(context, x1, y1, x2, y2, accent, hovering, 12.0F);
      float iconX = this.x + 4.0F;
      float iconY = this.y + (this.height - 6.0F) / 2.0F;
      int iconColor = !this.textBox.isFocused() && !hasQuery() ? -5327168 : GuiTheme.brighten(accent, 0.55F).getRGB();
      this.drawMagnifier(context, iconX, iconY, iconColor);
   }

   private void drawMagnifier(GuiGraphicsExtractor context, float x, float y, int color) {
      RenderUtil.rect(context, x, y, x + 5.0F, y + 1.0F, color);
      RenderUtil.rect(context, x, y + 4.0F, x + 5.0F, y + 5.0F, color);
      RenderUtil.rect(context, x, y, x + 1.0F, y + 5.0F, color);
      RenderUtil.rect(context, x + 4.0F, y, x + 5.0F, y + 5.0F, color);
      RenderUtil.rect(context, x + 5.0F, y + 5.0F, x + 7.0F, y + 6.0F, color);
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton == 0) {
         if (this.isHovering(mouseX, mouseY)) {
            this.textBox.focus();
         } else {
            this.textBox.unfocus();
         }
      }
   }

   @Override
   public void keyActivate() {
      this.textBox.focus();
   }

   public void clear() {
      this.textBox.setText("");
      this.textBox.unfocus();
   }

   @Override
   public boolean isHovering(int mouseX, int mouseY) {
      return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
   }
}
