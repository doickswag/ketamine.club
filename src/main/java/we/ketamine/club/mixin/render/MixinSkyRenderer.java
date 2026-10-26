package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.render.SkyboxModule;
import we.ketamine.club.util.render.SkyboxRenderer;

@Mixin({SkyRenderer.class})
public class MixinSkyRenderer {
   public MixinSkyRenderer() {
   }

   @Inject(
      method = {"renderSkyDisc"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$replaceSky(int skyColor, CallbackInfo ci) {
      SkyboxModule module = SkyboxModule.getInstance();
      if (module != null && module.isEnabled()) {
         Color c = module.auroraColor.getValue();
         if (SkyboxRenderer.render(module.mode.getValue(), c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F)) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"renderSunMoonAndStars"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$cancelCelestial(
      PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci
   ) {
      if (SkyboxRenderer.wasLastRenderSuccessful() && SkyboxModule.isActive(m -> m.replaceCelestial.getValue())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderSunriseAndSunset"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$cancelSunrise(PoseStack poseStack, float sunAngle, int color, CallbackInfo ci) {
      if (SkyboxRenderer.wasLastRenderSuccessful() && SkyboxModule.isActive(m -> m.replaceCelestial.getValue())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderDarkDisc"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$cancelDarkDisc(CallbackInfo ci) {
      if (SkyboxRenderer.wasLastRenderSuccessful() && SkyboxModule.isActive(m -> m.replaceCelestial.getValue())) {
         ci.cancel();
      }
   }
}
