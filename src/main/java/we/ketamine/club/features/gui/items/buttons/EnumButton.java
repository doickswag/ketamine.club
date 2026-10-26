package we.ketamine.club.features.gui.items.buttons;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import we.ketamine.club.features.gui.GuiTheme;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.RenderUtil;
import we.ketamine.club.util.render.font.Fonts;

public class EnumButton extends SettingButton<Enum<?>> {
   public EnumButton(Setting<Enum<?>> setting) {
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
      if (hovering) {
         RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height, GuiTheme.ROW_HOVER_BG);
      }

      float ty = this.y + (this.height - 8) / 2.0F;
      String val = ChatFormatting.GRAY + this.setting.currentEnumName();
      int w = Fonts.width(val);
      this.drawString(val, this.x + this.width - w - 3.0F, ty, -5327168);
      int labelMax = Math.max(0, this.width - w - 8);
      this.drawScrollableString(this.getName(), this.x + 2.0F, ty, -1512465, labelMax, hovering);
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
      this.setting.increaseEnum();
   }

   @Override
   public boolean getState() {
      return true;
   }
}
