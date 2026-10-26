package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.util.render.MatrixCapture;
import we.ketamine.club.util.traits.Util;

@Mixin({LevelRenderer.class})
public class MixinWorldRenderer {
   public MixinWorldRenderer() {
   }

   @Inject(
      method = {"render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"},
      at = {@At("RETURN")}
   )
   private void render(
      GraphicsResourceAllocator allocator,
      DeltaTracker tickCounter,
      boolean renderBlockOutline,
      CameraRenderState cameraState,
      Matrix4fc modelViewMatrix,
      GpuBufferSlice fogBuffer,
      Vector4f fogColor,
      boolean renderSky,
      CallbackInfo ci
   ) {
      MatrixCapture.capture(new Matrix4f(cameraState.projectionMatrix), new Matrix4f(modelViewMatrix));
      PoseStack stack = new PoseStack();
      stack.pushPose();
      stack.mulPose(Axis.XP.rotationDegrees(Util.mc.gameRenderer.mainCamera().xRot()));
      stack.mulPose(Axis.YP.rotationDegrees(Util.mc.gameRenderer.mainCamera().yRot() + 180.0F));
      ProfilerFiller profiler = Profiler.get();
      profiler.push("ketamineclub-render-3d");

      try {
         Render3DEvent event = new Render3DEvent(stack, tickCounter.getGameTimeDeltaPartialTick(true));
         Util.EVENT_BUS.post(event);
      } finally {
         stack.popPose();
         profiler.pop();
      }
   }
}
