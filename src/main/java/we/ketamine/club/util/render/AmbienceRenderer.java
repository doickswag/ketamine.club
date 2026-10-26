package we.ketamine.club.util.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import java.awt.Color;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import we.ketamine.club.features.modules.render.AmbienceModule;

public final class AmbienceRenderer {
   private static final int UNIFORM_SIZE = 16;
   private static final BindGroupLayout AMBIENCE_LAYOUT = BindGroupLayout.builder().withUniform("Ambience", UniformType.UNIFORM_BUFFER).build();
   private static final Identifier FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("ketamineclub", "core/ambience_terrain");
   private static final RenderPipeline SOLID_TERRAIN = terrainPipeline(RenderPipelines.SOLID_TERRAIN, "solid");
   private static final RenderPipeline CUTOUT_TERRAIN = terrainPipeline(RenderPipelines.CUTOUT_TERRAIN, "cutout");
   private static final RenderPipeline TRANSLUCENT_TERRAIN = terrainPipeline(RenderPipelines.TRANSLUCENT_TERRAIN, "translucent");
   private static MappableRingBuffer colorBuffer;
   private static boolean bufferWritten;
   private static boolean bufferUsed;

   private AmbienceRenderer() {
   }

   public static RenderPipeline replaceTerrainPipeline(RenderPipeline original) {
      AmbienceModule module = AmbienceModule.active();
      if (module == null || !module.chunkFade.getValue()) {
         return original;
      } else if (original == RenderPipelines.SOLID_TERRAIN) {
         return SOLID_TERRAIN;
      } else if (original == RenderPipelines.CUTOUT_TERRAIN) {
         return CUTOUT_TERRAIN;
      } else {
         return original == RenderPipelines.TRANSLUCENT_TERRAIN ? TRANSLUCENT_TERRAIN : original;
      }
   }

   public static void bindChunkFadeColor(RenderPass renderPass) {
      AmbienceModule module = AmbienceModule.active();
      if (module != null && module.chunkFade.getValue()) {
         ensureBuffer();
         if (!bufferWritten) {
            Color color = module.chunkFadeColor.getValue();
            MappedView view = colorBuffer.currentBuffer().map(false, true);

            try {
               Std140Builder.intoBuffer(view.data())
                  .putVec4(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
            } catch (Throwable var7) {
               if (view != null) {
                  try {
                     view.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }
               }

               throw var7;
            }

            if (view != null) {
               view.close();
            }

            bufferWritten = true;
         }

         bufferUsed = true;
         renderPass.setUniform("Ambience", colorBuffer.currentBuffer().slice(0L, 16L));
      }
   }

   public static void endFrame() {
      if (bufferUsed && colorBuffer != null) {
         colorBuffer.rotate();
         bufferWritten = false;
         bufferUsed = false;
      }
   }

   public static void cleanup() {
      MappableRingBuffer buffer = colorBuffer;
      colorBuffer = null;
      bufferWritten = false;
      bufferUsed = false;
      if (buffer != null) {
         buffer.close();
      }
   }

   private static void ensureBuffer() {
      if (colorBuffer == null) {
         colorBuffer = new MappableRingBuffer(() -> "ketamine.club ambience color", 130, 16);
      }
   }

   private static RenderPipeline terrainPipeline(RenderPipeline original, String name) {
      return Pipelines.copy(original)
         .withLocation(Identifier.fromNamespaceAndPath("ketamineclub", "pipeline/ambience_terrain_" + name))
         .withFragmentShader(FRAGMENT_SHADER)
         .withBindGroupLayout(AMBIENCE_LAYOUT)
         .build();
   }
}
