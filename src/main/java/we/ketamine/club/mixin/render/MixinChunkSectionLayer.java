package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.util.render.AmbienceRenderer;

@Mixin({ChunkSectionLayer.class})
public class MixinChunkSectionLayer {
   public MixinChunkSectionLayer() {
   }

   @Inject(
      method = {"pipeline"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void ketamineclub$ambienceTerrainPipeline(CallbackInfoReturnable<RenderPipeline> cir) {
      cir.setReturnValue(AmbienceRenderer.replaceTerrainPipeline((RenderPipeline)cir.getReturnValue()));
   }
}
