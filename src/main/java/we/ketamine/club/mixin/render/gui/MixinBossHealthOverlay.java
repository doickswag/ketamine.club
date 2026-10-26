package we.ketamine.club.mixin.render.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.render.NoRenderModule;

@Mixin({BossHealthOverlay.class})
public class MixinBossHealthOverlay {
   public MixinBossHealthOverlay() {
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noBossBar(GuiGraphicsExtractor context, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noBossBar.getValue())) {
         ci.cancel();
      }
   }
}
