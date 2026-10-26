package we.ketamine.club.features.gui.items.buttons;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import we.ketamine.club.features.gui.GuiTheme;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.render.RenderUtil;
import we.ketamine.club.util.render.font.Fonts;

public class BindButton extends SettingButton<Bind> {
   public boolean isListening;

   public BindButton(Setting<Bind> setting) {
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
      if (hovering || this.isListening) {
         RenderUtil.rect(context, this.x, this.y, this.x + this.width, this.y + this.height, GuiTheme.ROW_HOVER_BG);
      }

      float ty = this.y + (this.height - 8) / 2.0F;
      if (this.isListening) {
         this.drawString("Press a key...", this.x + 2.0F, ty, -1512465);
      } else {
         String str = ChatFormatting.GRAY + this.setting.getValue().toString().toUpperCase().replace("KEY.KEYBOARD", "").replace(".", " ").trim();
         int w = Fonts.width(str);
         this.drawString(str, this.x + this.width - w - 3.0F, ty, -5327168);
         int labelMax = Math.max(0, this.width - w - 8);
         this.drawScrollableString(this.getName(), this.x + 2.0F, ty, -1512465, labelMax, hovering);
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (this.isListening) {
         if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
            this.onMouseClick();
         } else {
            Bind bind = Bind.fromMouseButton(mouseButton);
            this.setting.setValue(bind);
            this.isListening = false;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         }
      } else {
         super.mouseClicked(mouseX, mouseY, mouseButton);
         if (this.isHovering(mouseX, mouseY)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         }
      }
   }

   @Override
   public void onKeyPressed(int key) {
      if (this.isListening) {
         Bind bind = new Bind(key);
         if (key == 261 || key == 259 || key == 256) {
            bind = new Bind(-1);
         }

         this.setting.setValue(bind);
         this.onMouseClick();
      }
   }

   @Override
   public void toggle() {
      this.isListening = !this.isListening;
   }

   @Override
   public boolean getState() {
      return !this.isListening;
   }
}
