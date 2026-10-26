package we.ketamine.club.util.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer.Draw;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;
import we.ketamine.club.features.modules.render.SkyboxModule;

public final class SkyboxRenderer {
   private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("ketamineclub", "core/skybox");
   private static StagedVertexBuffer vertexBuffer;
   private static boolean lastRenderSuccessful;
   private static final Map<SkyboxModule.Mode, Layers.Layer> TYPES = new EnumMap<>(SkyboxModule.Mode.class);

   private SkyboxRenderer() {
   }

   private static Layers.Layer buildType(SkyboxModule.Mode mode) {
      try {
         String modeName = mode.name().toLowerCase(Locale.ROOT);
         RenderPipeline pipeline = Pipelines.copy(RenderPipelines.SKY)
            .withLocation(Identifier.fromNamespaceAndPath("ketamineclub", "pipeline/skybox_" + modeName))
            .withVertexShader(VERTEX_SHADER)
            .withFragmentShader(Identifier.fromNamespaceAndPath("ketamineclub", "core/skybox_" + modeName))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();
         return Layers.create("ketamineclub_skybox_" + modeName, RenderSetup.builder(pipeline).createRenderSetup());
      } catch (Throwable var3) {
         return null;
      }
   }

   public static boolean render(SkyboxModule.Mode mode, float r, float g, float b) {
      Layers.Layer type = TYPES.get(mode);
      if (type == null) {
         lastRenderSuccessful = false;
         return false;
      } else {
         StagedVertexBuffer buffer;
         try {
            buffer = getOrCreateVertexBuffer();
         } catch (Throwable var26) {
            lastRenderSuccessful = false;
            return false;
         }

         boolean success = false;

         try {
            Draw draw = buffer.appendDraw(DefaultVertexFormat.POSITION_COLOR, PrimitiveTopology.QUADS);
            VertexConsumer vertices = buffer.getVertexBuilder(draw);
            vertices.addVertex(-1.0F, -1.0F, 0.0F).setColor(r, g, b, 1.0F);
            vertices.addVertex(1.0F, -1.0F, 0.0F).setColor(r, g, b, 1.0F);
            vertices.addVertex(1.0F, 1.0F, 0.0F).setColor(r, g, b, 1.0F);
            vertices.addVertex(-1.0F, 1.0F, 0.0F).setColor(r, g, b, 1.0F);
            buffer.upload();
            type.draw(buffer, draw);
            success = true;
         } catch (Throwable var25) {
            try {
               invalidate(buffer);
            } catch (Throwable var24) {
            }
         } finally {
            if (buffer == vertexBuffer) {
               try {
                  buffer.endFrame();
               } catch (Throwable var23) {
                  success = false;

                  try {
                     invalidate(buffer);
                  } catch (Throwable var22) {
                  }
               }
            }
         }

         lastRenderSuccessful = success;
         return success;
      }
   }

   public static boolean wasLastRenderSuccessful() {
      return lastRenderSuccessful;
   }

   public static void cleanup() {
      StagedVertexBuffer buffer = vertexBuffer;
      vertexBuffer = null;
      lastRenderSuccessful = false;
      if (buffer != null) {
         RenderSystem.queueFencedTask(buffer::close);
      }
   }

   private static StagedVertexBuffer getOrCreateVertexBuffer() {
      if (vertexBuffer == null) {
         vertexBuffer = createVertexBuffer();
      }

      return vertexBuffer;
   }

   private static StagedVertexBuffer createVertexBuffer() {
      return new StagedVertexBuffer(() -> "ketamine.club skybox", DefaultVertexFormat.POSITION_COLOR.getVertexSize() * 4);
   }

   private static void invalidate(StagedVertexBuffer buffer) {
      if (vertexBuffer == buffer) {
         vertexBuffer = null;
         RenderSystem.queueFencedTask(buffer::close);
      }
   }

   static {
      for (SkyboxModule.Mode mode : SkyboxModule.Mode.values()) {
         Layers.Layer type = buildType(mode);
         if (type != null) {
            TYPES.put(mode, type);
         }
      }
   }
}
