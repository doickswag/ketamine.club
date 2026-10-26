package we.ketamine.club.features.gui.items.buttons;

import java.awt.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;
import we.ketamine.club.features.gui.GuiTheme;
import we.ketamine.club.features.gui.KetamineClubGui;
import we.ketamine.club.features.gui.Widget;
import we.ketamine.club.features.gui.items.TextBox;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.RenderUtil;
import we.ketamine.club.util.render.font.Fonts;

public class Slider extends SettingButton<Number> {
   private final Number min;
   private final Number max;
   private final float difference;
   private final TextBox textBox;

   public Slider(Setting<Number> setting) {
      super(setting);
      this.min = setting.getMin();
      this.max = setting.getMax();
      this.difference = this.max.floatValue() - this.min.floatValue();
      this.height = 13;
      this.textBox = new TextBox().filter(this::acceptNumericChar).maxLength(16).onCommit(this::commitInput);
   }

   private boolean acceptNumericChar(int c) {
      if (Character.isDigit(c)) {
         return true;
      } else {
         String cur = this.textBox.getText();
         if (c == 46) {
            return !cur.contains(".");
         } else {
            return c == 45 ? cur.isEmpty() : false;
         }
      }
   }

   @Override
   public int getHeight() {
      return 13;
   }

   @Override
   public void drawScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHovering(mouseX, mouseY);
      if (!this.textBox.isFocused()) {
         this.dragSetting(mouseX, mouseY);
      }

      Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(120, 175, 220);
      if (hovering || this.textBox.isFocused()) {
         RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height, GuiTheme.ROW_HOVER_BG);
      }

      float trackX1 = this.x + 2.0F;
      float trackX2 = this.x + this.width - 2.0F;
      float pct = this.partialMultiplier();
      float fillX = trackX1 + (trackX2 - trackX1) * pct;
      float trackY = this.y + this.height - 2.0F;
      RenderUtil.rect(context, trackX1, trackY, trackX2, trackY + 1.0F, hovering ? GuiTheme.SLIDER_TRACK_HOVER : GuiTheme.SLIDER_TRACK);
      RenderUtil.rect(context, trackX1, trackY, fillX, trackY + 1.0F, GuiTheme.withAlpha(accent, 230).getRGB());
      RenderUtil.rect(context, fillX - 1.0F, trackY - 1.0F, fillX + 1.0F, trackY + 2.0F, GuiTheme.withAlpha(accent, 245).getRGB());
      float labelY = this.y + (this.height - 8) / 2.0F;
      float centerY = this.y + this.height / 2.0F;
      float valueRight = this.x + this.width - 3.0F;
      int valueColor = -5327168;
      float valueLeft;
      if (this.textBox.isFocused()) {
         valueLeft = this.textBox.renderInlineRight(context, valueRight, centerY, -1);
      } else {
         String value = ChatFormatting.GRAY + this.formatCurrentValue();
         int valWidth = Fonts.width(value);
         this.drawString(value, valueRight - valWidth, labelY, valueColor);
         valueLeft = valueRight - valWidth;
      }

      int labelMax = Math.max(0, (int)(valueLeft - (this.x + 2.0F) - 4.0F));
      this.drawScrollableString(this.getName(), this.x + 2.0F, labelY, -1512465, labelMax, hovering);
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (this.textBox.isFocused()) {
         if (!this.isHovering(mouseX, mouseY)) {
            this.textBox.unfocus();
         }
      } else if (mouseButton == 1 && this.isHovering(mouseX, mouseY)) {
         this.textBox.setText(this.formatCurrentValue());
         this.textBox.focus();
      } else {
         super.mouseClicked(mouseX, mouseY, mouseButton);
         if (this.isHovering(mouseX, mouseY)) {
            this.setSettingFromX(mouseX);
         }
      }
   }

   @Override
   public void keyActivate() {
      this.textBox.setText(this.formatCurrentValue());
      this.textBox.focus();
   }

   private void commitInput() {
      String input = this.textBox.getText();
      if (!input.isEmpty() && !input.equals("-")) {
         try {
            if (this.setting.getValue() instanceof Double) {
               double val = Double.parseDouble(input);
               this.setting.setValue(Math.max(this.min.doubleValue(), Math.min(this.max.doubleValue(), val)));
            } else if (this.setting.getValue() instanceof Float) {
               float val = Float.parseFloat(input);
               this.setting.setValue(Math.max(this.min.floatValue(), Math.min(this.max.floatValue(), val)));
            } else if (this.setting.getValue() instanceof Integer) {
               int val = Integer.parseInt(input);
               this.setting.setValue(Math.max(this.min.intValue(), Math.min(this.max.intValue(), val)));
            }
         } catch (NumberFormatException var4) {
         }
      }
   }

   private String formatCurrentValue() {
      if (this.setting.getValue() instanceof Integer) {
         return String.valueOf(this.setting.getValue().intValue());
      } else {
         return this.setting.getValue() instanceof Float ? this.setting.getValue().toString() : String.valueOf(this.setting.getValue().doubleValue());
      }
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

   private void dragSetting(int mouseX, int mouseY) {
      if (this.isHovering(mouseX, mouseY) && GLFW.glfwGetMouseButton(mc.getWindow().handle(), 0) == 1) {
         this.setSettingFromX(mouseX);
      }
   }

   private void setSettingFromX(int mouseX) {
      float trackX1 = this.x + 2.0F;
      float trackX2 = this.x + this.width - 2.0F;
      float pct = Math.max(0.0F, Math.min(1.0F, (mouseX - trackX1) / (trackX2 - trackX1)));
      if (this.setting.getValue() instanceof Double) {
         double result = this.min.doubleValue() + this.difference * pct;
         this.setting.setValue(Math.round(10.0 * result) / 10.0);
      } else if (this.setting.getValue() instanceof Float) {
         float result = this.min.floatValue() + this.difference * pct;
         this.setting.setValue(Math.round(10.0F * result) / 10.0F);
      } else if (this.setting.getValue() instanceof Integer) {
         this.setting.setValue(this.min.intValue() + (int)(this.difference * pct));
      }
   }

   private float partialMultiplier() {
      return this.difference == 0.0F ? 0.0F : (this.setting.getValue().floatValue() - this.min.floatValue()) / this.difference;
   }
}
