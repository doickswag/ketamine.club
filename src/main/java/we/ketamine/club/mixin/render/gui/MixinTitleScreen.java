package we.ketamine.club.mixin.render.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.util.render.MenuRenderUtil;

@Mixin({TitleScreen.class})
public abstract class MixinTitleScreen extends Screen {
   protected MixinTitleScreen(Component title) {
      super(title);
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
         shift = Shift.BEFORE
      )}
   )
   private void ketamineclub$drawAtmosphere(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      MenuRenderUtil.drawTitleBackdrop(graphics, this.width, this.height);
   }

   @Redirect(
      method = {"extractRenderState"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/TitleScreen;extractPanorama(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"
      )
   )
   private void ketamineclub$replacePanorama(TitleScreen screen, GuiGraphicsExtractor graphics, float delta) {
   }

   @Redirect(
      method = {"extractRenderState"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"
      )
   )
   private void ketamineclub$replaceLogo(LogoRenderer logo, GuiGraphicsExtractor graphics, int screenWidth, float alpha) {
      MenuRenderUtil.drawTitleBrand(graphics, screenWidth, alpha);
   }

   @Redirect(
      method = {"extractRenderState"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V"
      )
   )
   private void ketamineclub$removeSplash(SplashRenderer splash, GuiGraphicsExtractor graphics, int screenWidth, Font font, float alpha) {
   }
}
