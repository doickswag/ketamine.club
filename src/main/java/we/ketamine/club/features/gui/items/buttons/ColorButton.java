package we.ketamine.club.features.gui.items.buttons;

import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import we.ketamine.club.features.gui.GuiTheme;
import we.ketamine.club.features.gui.KetamineClubGui;
import we.ketamine.club.features.gui.Widget;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.RenderUtil;

public class ColorButton extends SettingButton<Color> {
   private static final int SV_HEIGHT = 46;
   private static final int BAR_HEIGHT = 7;
   private static final int BTN_HEIGHT = 12;
   private static final int GAP = 2;
   private boolean open = false;
   private boolean hoveringHue;
   private boolean hoveringColor;
   private boolean hoveringAlpha;
   private boolean hoveringCopy;
   private boolean hoveringPaste;
   private boolean hoveringSync;
   private boolean draggingHue;
   private boolean draggingColor;
   private boolean draggingAlpha;
   private float[] hsb;

   public ColorButton(Setting<Color> setting) {
      super(setting);
      this.height = 13;
      this.hsb = Color.RGBtoHSB(setting.getValue().getRed(), setting.getValue().getGreen(), setting.getValue().getBlue(), null);
   }

   @Override
   public int getHeight() {
      return !this.open ? 13 : 95;
   }

   @Override
   public int getRowHeight() {
      return 13;
   }

   @Override
   public void keyActivate() {
      this.open = !this.open;
      mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }

   private int pickerWidth() {
      return Math.max(40, this.width - 4);
   }

   @Override
   public void drawScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHoveringRow(mouseX, mouseY);
      if (hovering) {
         RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + 13.0F, GuiTheme.ROW_HOVER_BG);
      }

      this.drawScrollableString(this.getName(), this.x + 2.0F, this.y + 2.5F, -1512465, this.width - 22, hovering);
      float sw1 = this.x + this.width - 16.0F;
      float sw2 = this.x + this.width - 3.0F;
      float sy1 = this.y + 2.0F;
      float sy2 = this.y + 13.0F - 2.0F;
      if (GuiTheme.isFlat()) {
         RenderUtil.rect(context, sw1, sy1, sw2, sy2, this.setting.getValue().getRGB());
         RenderUtil.rect(context, sw1, sy1, sw2, sy2, GuiTheme.OUTLINE, 1.0F);
      } else {
         RenderUtil.chamferedRect(context, sw1, sy1, sw2, sy2, this.setting.getValue().getRGB(), 2, true, true);
         RenderUtil.chamferedOutline(context, sw1, sy1, sw2, sy2, GuiTheme.OUTLINE, 2, true, true);
      }

      if (this.open) {
         Color currentColor = this.setting.getValue();
         Color realColor = Color.getHSBColor(this.hsb[0], 1.0F, 1.0F);
         int outlineColor = GuiTheme.OUTLINE_INNER;
         int pickerWidth = this.pickerWidth();
         float pickerX = this.x + 2.0F;
         float yOffset = 15.0F;
         float panelTop = this.y + yOffset - 1.0F;
         float panelBottom = this.y + this.getHeight() - 1.0F;
         if (GuiTheme.isFlat()) {
            RenderUtil.rect(context, pickerX - 1.0F, panelTop, pickerX + pickerWidth + 1.0F, panelBottom, GuiTheme.BODY_BG_SOFT);
            RenderUtil.rect(context, pickerX - 1.0F, panelTop, pickerX + pickerWidth + 1.0F, panelBottom, GuiTheme.OUTLINE_INNER, 1.0F);
         } else {
            RenderUtil.chamferedRect(context, pickerX - 1.0F, panelTop, pickerX + pickerWidth + 1.0F, panelBottom, GuiTheme.BODY_BG_SOFT, 3, true, true);
            RenderUtil.chamferedOutline(context, pickerX - 1.0F, panelTop, pickerX + pickerWidth + 1.0F, panelBottom, GuiTheme.OUTLINE_INNER, 3, true, true);
         }

         int dragX = Mth.clamp(mouseX - (int)pickerX, 0, pickerWidth);
         int dragY = Mth.clamp(mouseY - (int)(this.y + yOffset), 0, 46);
         float dragHue = Math.max(pickerWidth * this.hsb[0] - 0.5F, 1.0F);
         float dragSaturation = Math.max(pickerWidth * this.hsb[1] - 1.0F, 2.0F);
         float dragBrightness = Math.max(46.0F * (1.0F - this.hsb[2]) - 1.0F, 2.0F);
         float dragAlpha = Math.max(pickerWidth * (currentColor.getAlpha() / 255.0F) - 0.5F, 1.0F);
         RenderUtil.horizontalGradient(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 46.0F, Color.WHITE, realColor);
         RenderUtil.verticalGradient(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 46.0F, new Color(0, 0, 0, 0), Color.BLACK);
         RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 46.0F, outlineColor, 1.0F);
         this.hoveringColor = this.isHoveringArea(mouseX, mouseY, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 46.0F);
         if (dragSaturation < pickerWidth && dragBrightness < 46.0F) {
            RenderUtil.rect(
               context,
               pickerX + dragSaturation - 2.0F,
               this.y + yOffset + dragBrightness - 2.0F,
               pickerX + dragSaturation + 1.0F,
               this.y + yOffset + dragBrightness + 1.0F,
               -16777216
            );
            RenderUtil.rect(
               context,
               pickerX + dragSaturation - 1.0F,
               this.y + yOffset + dragBrightness - 1.0F,
               pickerX + dragSaturation,
               this.y + yOffset + dragBrightness,
               -1
            );
         }

         if (this.draggingColor) {
            this.hsb[1] = (float)dragX / pickerWidth;
            this.hsb[2] = 1.0F - dragY / 46.0F;
            this.setColor(this.hsb);
         }

         yOffset += 48.0F;
         RenderUtil.horizontalGradient(
            context,
            pickerX,
            this.y + yOffset,
            pickerX + pickerWidth,
            this.y + yOffset + 7.0F,
            new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 0),
            new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 255)
         );
         RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 7.0F, outlineColor, 1.0F);
         this.hoveringAlpha = this.isHoveringArea(mouseX, mouseY, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 7.0F);
         RenderUtil.rect(context, pickerX + dragAlpha - 1.5F, this.y + yOffset - 1.0F, pickerX + dragAlpha + 1.5F, this.y + yOffset + 7.0F + 1.0F, -16777216);
         RenderUtil.rect(context, pickerX + dragAlpha - 0.5F, this.y + yOffset, pickerX + dragAlpha + 0.5F, this.y + yOffset + 7.0F, -1);
         if (this.draggingAlpha) {
            this.setColor(this.hsb, (int)(255.0F * dragX / pickerWidth));
         }

         yOffset += 9.0F;

         for (float i = 0.0F; i < pickerWidth; i += 0.5F) {
            RenderUtil.rect(
               context, pickerX + i, this.y + yOffset, pickerX + i + 0.5F, this.y + yOffset + 7.0F, Color.getHSBColor(i / pickerWidth, 1.0F, 1.0F).getRGB()
            );
         }

         RenderUtil.rect(context, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 7.0F, outlineColor, 1.0F);
         this.hoveringHue = this.isHoveringArea(mouseX, mouseY, pickerX, this.y + yOffset, pickerX + pickerWidth, this.y + yOffset + 7.0F);
         if (dragHue < pickerWidth) {
            RenderUtil.rect(context, pickerX + dragHue - 1.5F, this.y + yOffset - 1.0F, pickerX + dragHue + 1.5F, this.y + yOffset + 7.0F + 1.0F, -16777216);
            RenderUtil.rect(context, pickerX + dragHue - 0.5F, this.y + yOffset, pickerX + dragHue + 0.5F, this.y + yOffset + 7.0F, -1);
         }

         if (this.draggingHue) {
            this.hsb[0] = (float)dragX / pickerWidth;
            this.setColor(this.hsb);
         }

         yOffset += 9.0F;
         int buttonWidth = (pickerWidth - 4) / 3;
         int copyLeft = Math.round(pickerX);
         int pasteLeft = copyLeft + buttonWidth + 2;
         int syncLeft = pasteLeft + buttonWidth + 2;
         int buttonTop = Math.round(this.y + yOffset);
         int buttonBottom = buttonTop + 12;
         Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(120, 175, 220);
         boolean hasSource = KetamineClubGui.getColorClipboard() != null;
         this.hoveringCopy = this.isHoveringArea(mouseX, mouseY, copyLeft, buttonTop, copyLeft + buttonWidth, buttonBottom);
         this.hoveringPaste = hasSource && this.isHoveringArea(mouseX, mouseY, pasteLeft, buttonTop, pasteLeft + buttonWidth, buttonBottom);
         this.hoveringSync = hasSource && this.isHoveringArea(mouseX, mouseY, syncLeft, buttonTop, syncLeft + buttonWidth, buttonBottom);
         this.drawIconButton(context, copyLeft, buttonTop, buttonWidth, this.hoveringCopy, true, accent);
         this.drawCopyIcon(context, copyLeft, buttonTop, buttonWidth, -1512465);
         this.drawIconButton(context, pasteLeft, buttonTop, buttonWidth, this.hoveringPaste, hasSource, accent);
         this.drawPasteIcon(context, pasteLeft, buttonTop, buttonWidth, hasSource ? -1512465 : GuiTheme.withAlpha(new Color(230, 233, 238), 90).getRGB());
         this.drawIconButton(context, syncLeft, buttonTop, buttonWidth, this.hoveringSync, hasSource, accent);
         this.drawSyncIcon(context, syncLeft, buttonTop, buttonWidth, hasSource ? -1512465 : GuiTheme.withAlpha(new Color(230, 233, 238), 90).getRGB());
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (this.isHoveringRow(mouseX, mouseY) && mouseButton == 1) {
         this.open = !this.open;
         mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
      }

      if (mouseButton == 0 && this.open) {
         if (this.hoveringHue) {
            this.draggingHue = true;
         }

         if (this.hoveringColor) {
            this.draggingColor = true;
         }

         if (this.hoveringAlpha) {
            this.draggingAlpha = true;
         }

         if (this.hoveringCopy) {
            KetamineClubGui.setColorClipboard(this.setting.getValue());
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         }

         if (this.hoveringPaste && KetamineClubGui.getColorClipboard() != null) {
            this.setting.setValue(KetamineClubGui.getColorClipboard());
            this.hsb = Color.RGBtoHSB(this.setting.getValue().getRed(), this.setting.getValue().getGreen(), this.setting.getValue().getBlue(), null);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         }

         if (this.hoveringSync && KetamineClubGui.getColorClipboard() != null) {
            Color source = KetamineClubGui.getColorClipboard();
            Color current = this.setting.getValue();
            this.setting.setValue(new Color(source.getRed(), source.getGreen(), source.getBlue(), current.getAlpha()));
            this.hsb = Color.RGBtoHSB(source.getRed(), source.getGreen(), source.getBlue(), null);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         }
      }
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
      if (releaseButton == 0) {
         this.draggingHue = false;
         this.draggingColor = false;
         this.draggingAlpha = false;
      }
   }

   @Override
   public boolean isHovering(int mouseX, int mouseY) {
      return this.isHoveringRow(mouseX, mouseY);
   }

   private boolean isHoveringRow(int mouseX, int mouseY) {
      for (Widget widget : KetamineClubGui.getClickGui().getComponents()) {
         if (widget.drag) {
            return false;
         }
      }

      return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY < this.y + 13.0F;
   }

   private boolean isHoveringArea(int mouseX, int mouseY, float left, float top, float right, float bottom) {
      for (Widget widget : KetamineClubGui.getClickGui().getComponents()) {
         if (widget.drag) {
            return false;
         }
      }

      return left <= mouseX && top <= mouseY && right > mouseX && bottom > mouseY;
   }

   private void setColor(float[] hsb) {
      this.setColor(hsb, this.setting.getValue().getAlpha());
   }

   private void setColor(float[] hsb, int alpha) {
      Color color = new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
      this.setting.setValue(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
   }

   public void setOpen(boolean open) {
      this.open = open;
   }

   private void drawIconButton(GuiGraphicsExtractor context, int left, int top, int width, boolean hovering, boolean enabled, Color accent) {
      int bg = enabled ? (hovering ? GuiTheme.withAlpha(accent, 96).getRGB() : GuiTheme.CONTROL_BG) : GuiTheme.withAlpha(new Color(255, 255, 255), 12).getRGB();
      int border = enabled
         ? (hovering ? GuiTheme.withAlpha(accent, 178).getRGB() : GuiTheme.OUTLINE_INNER)
         : GuiTheme.withAlpha(new Color(255, 255, 255), 22).getRGB();
      if (GuiTheme.isFlat()) {
         RenderUtil.rect(context, (float)left, (float)top, (float)(left + width), (float)(top + 12), bg);
         RenderUtil.rect(context, (float)left, (float)top, (float)(left + width), (float)(top + 12), border, 1.0F);
      } else {
         RenderUtil.chamferedRect(context, left, top, left + width, top + 12, bg, 2, true, true);
         RenderUtil.chamferedOutline(context, left, top, left + width, top + 12, border, 2, true, true);
      }

      if (hovering && enabled && !GuiTheme.isFlat()) {
         RenderUtil.rect(context, left + 2.0F, (float)top, left + width - 2.0F, top + 1.0F, GuiTheme.HIGHLIGHT_TOP);
      }
   }

   private void drawCopyIcon(GuiGraphicsExtractor context, int left, int top, int width, int color) {
      int x = left + width / 2 - 4;
      int y = top + 2;
      RenderUtil.rect(context, (float)(x + 2), (float)y, (float)(x + 7), (float)(y + 5), color, 1.0F);
      RenderUtil.rect(context, (float)x, (float)(y + 2), (float)(x + 5), (float)(y + 7), color, 1.0F);
   }

   private void drawPasteIcon(GuiGraphicsExtractor context, int left, int top, int width, int color) {
      int x = left + width / 2 - 4;
      int y = top + 2;
      RenderUtil.rect(context, (float)(x + 2), (float)y, (float)(x + 6), (float)(y + 2), color, 1.0F);
      RenderUtil.rect(context, (float)x, (float)(y + 1), (float)(x + 8), (float)(y + 8), color, 1.0F);
      RenderUtil.rect(context, (float)(x + 2), (float)(y + 4), (float)(x + 6), (float)(y + 5), color);
   }

   private void drawSyncIcon(GuiGraphicsExtractor context, int left, int top, int width, int color) {
      int x = left + width / 2 - 5;
      int y = top + 2;
      RenderUtil.rect(context, (float)(x + 1), (float)(y + 1), (float)(x + 7), (float)(y + 2), color);
      RenderUtil.rect(context, (float)(x + 6), (float)y, (float)(x + 9), (float)(y + 3), color);
      RenderUtil.rect(context, (float)(x + 2), (float)(y + 2), (float)(x + 3), (float)(y + 4), color);
      RenderUtil.rect(context, (float)(x + 3), (float)(y + 6), (float)(x + 9), (float)(y + 7), color);
      RenderUtil.rect(context, (float)(x + 1), (float)(y + 5), (float)(x + 4), (float)(y + 8), color);
      RenderUtil.rect(context, (float)(x + 7), (float)(y + 4), (float)(x + 8), (float)(y + 6), color);
   }
}
