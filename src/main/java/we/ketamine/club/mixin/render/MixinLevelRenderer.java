package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.event.impl.render.RenderBlockOutlineEvent;
import we.ketamine.club.features.modules.render.AmbienceModule;
import we.ketamine.club.util.render.AmbienceRenderer;
import we.ketamine.club.util.traits.Util;

@Mixin({LevelRenderer.class})
public class MixinLevelRenderer {
   @Shadow
   @Final
   private LevelRenderState levelRenderState;

   public MixinLevelRenderer() {
   }

   @Inject(
      method = {"render"},
      at = {@At("HEAD")}
   )
   private void ketamineclub$ambienceColors(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderBlockOutline,
      CameraRenderState cameraState,
      Matrix4fc modelViewMatrix,
      GpuBufferSlice fogBuffer,
      Vector4f clearColor,
      boolean renderSky,
      CallbackInfo ci
   ) {
      AmbienceModule module = AmbienceModule.active();
      if (module != null) {
         module.applyLevelColors(this.levelRenderState);
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("RETURN")}
   )
   private void ketamineclub$endAmbienceFrame(
      GraphicsResourceAllocator allocator,
      DeltaTracker deltaTracker,
      boolean renderBlockOutline,
      CameraRenderState cameraState,
      Matrix4fc modelViewMatrix,
      GpuBufferSlice fogBuffer,
      Vector4f clearColor,
      boolean renderSky,
      CallbackInfo ci
   ) {
      AmbienceRenderer.endFrame();
   }

   @Inject(
      method = {"submitBlockOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$renderBlockOutline(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelRenderState, CallbackInfo ci) {
      if (Util.EVENT_BUS.post(new RenderBlockOutlineEvent())) {
         ci.cancel();
      }
   }
}
