package we.ketamine.club.mixin.render.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractStringWidget.class})
public abstract class MixinAbstractStringWidget {
   public MixinAbstractStringWidget() {
   }

   @Inject(
      method = {"extractWidgetRenderState"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$hideMultiplayerTitle(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (Minecraft.getInstance().gui.screen() instanceof JoinMultiplayerScreen screen && ((AbstractStringWidget)(Object)this).getMessage().equals(screen.getTitle())) {
         ci.cancel();
      }
   }
}
