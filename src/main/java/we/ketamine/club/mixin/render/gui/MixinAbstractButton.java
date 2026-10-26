package we.ketamine.club.mixin.render.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.util.render.MenuRenderUtil;

@Mixin({AbstractButton.class})
public abstract class MixinAbstractButton {
   public MixinAbstractButton() {
   }

   @Inject(
      method = {"extractWidgetRenderState"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$drawBrandedButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      Minecraft minecraft = Minecraft.getInstance();
      Screen screen = minecraft.gui.screen();
      if ((screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen)
         && ((Object)this) instanceof Button button
         && button.getWidth() >= 70
         && !(button instanceof PlainTextButton)) {
         MenuRenderUtil.drawButton(graphics, button);
         ci.cancel();
      }
   }
}
