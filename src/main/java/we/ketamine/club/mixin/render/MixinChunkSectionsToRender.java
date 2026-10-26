package we.ketamine.club.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.util.render.AmbienceRenderer;

@Mixin({ChunkSectionsToRender.class})
public class MixinChunkSectionsToRender {
   public MixinChunkSectionsToRender() {
   }

   @Inject(
      method = {"renderGroup"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms(Lcom/mojang/blaze3d/systems/RenderPass;)V",
         shift = Shift.AFTER
      )}
   )
   private void ketamineclub$bindAmbienceColor(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci, @Local RenderPass renderPass) {
      AmbienceRenderer.bindChunkFadeColor(renderPass);
   }
}
