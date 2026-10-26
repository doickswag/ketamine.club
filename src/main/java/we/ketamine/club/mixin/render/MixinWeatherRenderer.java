package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.slop.NoWeatherModule;

@Mixin({LevelRenderer.class})
public class MixinWeatherRenderer {
   public MixinWeatherRenderer() {
   }

   @Inject(
      method = {"addWeatherPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noWeather(FrameGraphBuilder frameGraph, GpuBufferSlice fogBuffer, CallbackInfo ci) {
      NoWeatherModule module = KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(NoWeatherModule.class);
      if (module != null && module.isEnabled()) {
         ci.cancel();
      }
   }
}
