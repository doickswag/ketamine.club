package we.ketamine.club.features.gui.items.buttons;

import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import we.ketamine.club.features.gui.GuiTheme;
import we.ketamine.club.features.gui.Widget;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.RenderUtil;

public class BooleanButton extends SettingButton<Boolean> {
   public BooleanButton(Setting<Boolean> setting) {
      super(setting);
      this.height = 13;
   }

   @Override
   public int getHeight() {
      return 13;
   }

   @Override
   public void drawScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
      boolean hovering = this.isHovering(mouseX, mouseY);
      Color accent = Widget.currentAccent != null ? Widget.currentAccent : new Color(120, 175, 220);
      if (hovering) {
         RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height, GuiTheme.ROW_HOVER_BG);
      }

      float boxRight = this.x + this.width - 3.0F;
      float boxLeft = boxRight - 8.0F;
      float boxTop = this.y + (this.height - 8.0F) / 2.0F;
      float boxBottom = boxTop + 8.0F;
      if (GuiTheme.isFlat()) {
         RenderUtil.rect(context, boxLeft, boxTop, boxRight, boxBottom, this.getState() ? GuiTheme.withAlpha(accent, 220).getRGB() : GuiTheme.CHECKBOX_OFF_BG);
         if (!this.getState()) {
            RenderUtil.rect(context, boxLeft, boxTop, boxRight, boxBottom, GuiTheme.CHECKBOX_BORDER, 1.0F);
         }
      } else {
         RenderUtil.chamferedRect(
            context, boxLeft, boxTop, boxRight, boxBottom, this.getState() ? GuiTheme.withAlpha(accent, 218).getRGB() : GuiTheme.CHECKBOX_OFF_BG, 2, true, true
         );
         RenderUtil.chamferedOutline(
            context, boxLeft, boxTop, boxRight, boxBottom, this.getState() ? GuiTheme.withAlpha(accent, 245).getRGB() : GuiTheme.CHECKBOX_BORDER, 2, true, true
         );
         if (this.getState()) {
            RenderUtil.rect(context, boxLeft + 2.0F, boxTop + 2.0F, boxRight - 2.0F, boxBottom - 2.0F, -1);
         }
      }

      int textColor = this.getState() ? -1 : -1512465;
      this.drawScrollableString(this.getName(), this.x + 2.0F, this.y + (this.height - 8) / 2.0F, textColor, this.width - 16, hovering);
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (this.isHovering(mouseX, mouseY)) {
         mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
      }
   }

   @Override
   public void toggle() {
      this.setting.setValue(!this.setting.getValue());
   }

   @Override
   public boolean getState() {
      return this.setting.getValue();
   }
}
