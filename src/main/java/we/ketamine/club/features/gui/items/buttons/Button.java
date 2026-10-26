package we.ketamine.club.features.gui.items.buttons;

import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import we.ketamine.club.features.gui.GuiTheme;
import we.ketamine.club.features.gui.KetamineClubGui;
import we.ketamine.club.features.gui.Widget;
import we.ketamine.club.features.gui.items.Item;
import we.ketamine.club.util.render.RenderUtil;

public class Button extends Item {
   private boolean state;

   public Button(String name) {
      super(name);
      this.height = 14;
   }

   @Override
   public void drawScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHovering(mouseX, mouseY);
      drawRow(context, this.x, this.y, this.x + this.width, this.y + this.height, this.getState(), hovering);
      int textColor = this.getState() ? -1 : -3090980;
      this.drawScrollableString(this.getName(), this.x + 4.0F, this.y + (this.height - 8) / 2.0F, textColor, this.width - 8, hovering);
   }

   public static void drawSelectionRing(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, Color accent) {
      if (accent == null) {
         accent = new Color(110, 120, 130);
      }

      if (GuiTheme.isFlat()) {
         RenderUtil.rect(context, x1, y1, x2, y2, GuiTheme.withAlpha(accent, 210).getRGB(), 1.0F);
      } else {
         int inner = GuiTheme.withAlpha(GuiTheme.brighten(accent, 0.65F), 230).getRGB();
         int outer = GuiTheme.withAlpha(GuiTheme.brighten(accent, 0.45F), 140).getRGB();
         RenderUtil.rect(context, x1, y1, x2, y2, outer, 2.0F);
         RenderUtil.rect(context, x1, y1, x2, y2, inner, 1.0F);
         RenderUtil.rect(context, x1, y1, x2, y1 + 1.0F, GuiTheme.HIGHLIGHT_TOP);
      }
   }

   @Override
   public void keyActivate() {
      this.onMouseClick();
   }

   public static void drawRow(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, boolean enabled, boolean hovering) {
      Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(70, 75, 82);
      boolean flat = GuiTheme.isFlat();
      float fillBottom = y2 - 1.0F;
      if (flat) {
         if (enabled) {
            RenderUtil.rect(context, x1, y1, x2, fillBottom, GuiTheme.withAlpha(accent, 82).getRGB());
         } else if (hovering) {
            RenderUtil.rect(context, x1, y1, x2, fillBottom, GuiTheme.ROW_HOVER_BG);
         }

         RenderUtil.rect(context, x1, y2 - 1.0F, x2, y2, GuiTheme.withAlpha(accent, enabled ? 118 : 34).getRGB());
      } else {
         if (enabled) {
            RenderUtil.rect(context, x1, y1, x2, fillBottom, GuiTheme.ROW_ACTIVE_BG);
            RenderUtil.rect(context, x1, y1, x2, fillBottom, GuiTheme.withAlpha(accent, 54).getRGB());
            RenderUtil.rect(context, x1, y1, x1 + 2.0F, fillBottom, GuiTheme.withAlpha(accent, 224).getRGB());
            if (hovering) {
               RenderUtil.rect(context, x1, y1, x2, fillBottom, GuiTheme.HIGHLIGHT_SOFT);
            }

            RenderUtil.rect(context, x1, y1, x2, y1 + 1.0F, GuiTheme.HIGHLIGHT_TOP);
         } else if (hovering) {
            RenderUtil.rect(context, x1, y1, x2, fillBottom, GuiTheme.ROW_HOVER_BG);
            RenderUtil.rect(context, x1, y1, x1 + 2.0F, fillBottom, GuiTheme.withAlpha(accent, 150).getRGB());
         }

         RenderUtil.rect(context, x1, y2 - 1.0F, x2, y2, GuiTheme.ROW_SEPARATOR);
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
         this.onMouseClick();
      }
   }

   public void onMouseClick() {
      this.state = !this.state;
      this.toggle();
      mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }

   public void toggle() {
   }

   public boolean getState() {
      return this.state;
   }

   @Override
   public int getHeight() {
      return 14;
   }

   @Override
   public boolean isHovering(int mouseX, int mouseY) {
      for (Widget widget : KetamineClubGui.getClickGui().getComponents()) {
         if (widget.drag) {
            return false;
         }
      }

      return mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth() && mouseY >= this.getY() && mouseY < this.getY() + this.height;
   }
}
