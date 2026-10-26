package we.ketamine.club.features.gui.items.buttons;

import java.awt.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import we.ketamine.club.features.gui.Widget;
import we.ketamine.club.features.gui.items.TextBox;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.font.Fonts;

public class StringButton extends SettingButton<String> {
   private final TextBox textBox;

   public StringButton(Setting<String> setting) {
      super(setting);
      this.height = 13;
      this.textBox = new TextBox()
         .placeholder(this.getName())
         .maxLength(2048)
         .onCommit(this::commit)
         .onCancel(() -> this.textBox().setText(setting.getValue()));
   }

   private TextBox textBox() {
      return this.textBox;
   }

   @Override
   public int getHeight() {
      return 13;
   }

   @Override
   public void drawScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHovering(mouseX, mouseY);
      Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(70, 75, 82);
      float x1 = this.x;
      float y1 = this.y;
      float x2 = this.x + this.width;
      float y2 = this.y + this.height - 1.0F;
      if (this.textBox.isFocused()) {
         this.textBox.renderPill(context, x1, y1, x2, y2, accent, hovering, 3.0F);
      } else {
         float ty = this.y + (this.height - 8) / 2.0F;
         String val = ChatFormatting.GRAY + this.setting.getValue();
         int w = Fonts.width(val);
         if (hovering) {
            this.textBox.renderPill(context, x1, y1, x2, y2, accent, true, 3.0F);
         }

         this.drawString(val, this.x + this.width - w - 3.0F, ty, -5327168);
         int labelMax = Math.max(0, this.width - w - 8);
         this.drawScrollableString(this.getName(), this.x + 2.0F, ty, -1512465, labelMax, hovering);
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
         if (!this.textBox.isFocused()) {
            this.textBox.setText(this.setting.getValue());
            this.textBox.focus();
         }
      } else if (!this.isHovering(mouseX, mouseY) && this.textBox.isFocused()) {
         this.textBox.unfocus();
      }
   }

   @Override
   public void keyActivate() {
      this.textBox.setText(this.setting.getValue());
      this.textBox.focus();
   }

   private void commit() {
      String s = this.textBox.getText();
      if (s.isEmpty()) {
         this.setting.setValue(this.setting.getDefaultValue());
      } else {
         this.setting.setValue(s);
      }
   }
}
