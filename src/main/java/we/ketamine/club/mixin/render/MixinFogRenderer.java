package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.features.modules.render.AmbienceModule;
import we.ketamine.club.features.modules.render.NoRenderModule;

@Mixin({FogRenderer.class})
public class MixinFogRenderer {
   @Shadow
   @Final
   private GpuBuffer emptyBuffer;
   @Shadow
   @Final
   private static int FOG_UBO_SIZE;

   public MixinFogRenderer() {
   }

   @Inject(
      method = {"getBuffer"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noFog(FogMode mode, CallbackInfoReturnable<GpuBufferSlice> cir) {
      if (NoRenderModule.isActive(m -> m.noFog.getValue())) {
         cir.setReturnValue(this.emptyBuffer.slice(0L, FOG_UBO_SIZE));
      }
   }

   @Inject(
      method = {"setupFog"},
      at = {@At("RETURN")}
   )
   private void ketamineclub$ambienceFog(
      Camera camera, int renderDistance, DeltaTracker deltaTracker, float worldDarkening, ClientLevel level, CallbackInfoReturnable<FogData> cir
   ) {
      AmbienceModule module = AmbienceModule.active();
      if (module != null) {
         module.applyFogColor((FogData)cir.getReturnValue());
      }
   }
}
