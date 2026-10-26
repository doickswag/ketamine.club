package we.ketamine.club.util.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame;
import org.joml.Vector4f;

public final class HandShaderRender {
   private static final Vector4f CLEAR_COLOR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
   private static CrossFrameResourcePool resourcePool = new CrossFrameResourcePool(3);
   private static TextureTarget handTarget;
   private static SubmitNodeStorage captureStorage;
   private static int width;
   private static int height;
   private static boolean captureOpen;
   private static boolean hasCapture;

   private HandShaderRender() {
   }

   public static HandSilhouetteCollector beginCapture() {
      if (!ensureTarget()) {
         return null;
      } else {
         captureStorage = new SubmitNodeStorage();
         captureOpen = true;
         hasCapture = false;
         return new HandSilhouetteCollector(captureStorage);
      }
   }

   public static void endCapture() {
      if (captureOpen) {
         captureOpen = false;
         hasCapture = true;
      }
   }

   public static void composite(FeatureRenderDispatcher dispatcher, PostChain chain) {
      SubmitNodeStorage storage = captureStorage;
      captureStorage = null;
      if (hasCapture && handTarget != null && storage != null && chain != null) {
         hasCapture = false;
         RenderSystem.getDevice()
            .createCommandEncoder()
            .clearColorAndDepthTextures(handTarget.getColorTexture(), CLEAR_COLOR, handTarget.getDepthTexture(), 0.0);
         GpuTextureView previousColorOverride = RenderSystem.outputColorTextureOverride;
         GpuTextureView previousDepthOverride = RenderSystem.outputDepthTextureOverride;

         try {
            RenderSystem.outputColorTextureOverride = handTarget.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = handTarget.getDepthTextureView();
            PreparedFrame frame = dispatcher.prepareFrame(storage);

            try {
               frame.executeSolid();
               frame.executeTranslucent();
            } catch (Throwable var20) {
               if (frame != null) {
                  try {
                     frame.close();
                  } catch (Throwable var18) {
                     var20.addSuppressed(var18);
                  }
               }

               throw var20;
            }

            if (frame != null) {
               frame.close();
            }
         } finally {
            RenderSystem.outputColorTextureOverride = previousColorOverride;
            RenderSystem.outputDepthTextureOverride = previousDepthOverride;
         }

         CrossFrameResourcePool var22 = getOrCreateResourcePool();

         try {
            chain.process(handTarget, var22);
            RenderTarget mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            handTarget.blitAndBlendToTexture(mainTarget.getColorTextureView(), mainTarget.getDepthTextureView());
         } finally {
            var22.endFrame();
         }
      } else {
         hasCapture = false;
      }
   }

   public static void cleanup() {
      TextureTarget target = handTarget;
      CrossFrameResourcePool pool = resourcePool;
      handTarget = null;
      resourcePool = null;
      captureStorage = null;
      captureOpen = false;
      hasCapture = false;
      width = 0;
      height = 0;
      if (target != null || pool != null) {
         RenderSystem.queueFencedTask(() -> {
            try {
               if (target != null) {
                  target.destroyBuffers();
               }
            } finally {
               if (pool != null) {
                  pool.close();
               }
            }
         });
      }
   }

   private static boolean ensureTarget() {
      Minecraft minecraft = Minecraft.getInstance();
      int targetWidth = minecraft.getWindow().getWidth();
      int targetHeight = minecraft.getWindow().getHeight();
      if (targetWidth > 0 && targetHeight > 0) {
         if (handTarget == null) {
            handTarget = new TextureTarget("ketamineclub_hand_shader", targetWidth, targetHeight, true, GpuFormat.RGBA8_UNORM);
            width = targetWidth;
            height = targetHeight;
         } else if (targetWidth != width || targetHeight != height) {
            if (resourcePool != null) {
               resourcePool.clear();
            }

            handTarget.resize(targetWidth, targetHeight);
            width = targetWidth;
            height = targetHeight;
         }

         return true;
      } else {
         return false;
      }
   }

   private static CrossFrameResourcePool getOrCreateResourcePool() {
      if (resourcePool == null) {
         resourcePool = new CrossFrameResourcePool(3);
      }

      return resourcePool;
   }
}
