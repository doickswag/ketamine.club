package we.ketamine.club.mixin.render.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.render.NoRenderModule;
import we.ketamine.club.util.render.MenuRenderUtil;

@Mixin({Screen.class})
public class MixinScreen {
   public MixinScreen() {
   }

   @Inject(
      method = {"extractBackground"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (((Object)this) instanceof JoinMultiplayerScreen screen) {
         MenuRenderUtil.drawServerBackground(context, screen.width, screen.height);
         MenuRenderUtil.drawServerOverlay(context, screen);
         ci.cancel();
      } else {
         if (Minecraft.getInstance().level != null && NoRenderModule.isActive(m -> m.noBackground.getValue())) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"extractTransparentBackground"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noTransparentBackground(GuiGraphicsExtractor context, CallbackInfo ci) {
      if (Minecraft.getInstance().level != null && NoRenderModule.isActive(m -> m.noBackground.getValue())) {
         ci.cancel();
      }
   }
}
