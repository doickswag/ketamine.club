package we.ketamine.club.util.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Optional;
import net.minecraft.client.particle.SingleQuadParticle.Layer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

public final class SeeThroughParticle {
   public static final Layer TRANSLUCENT_LAYER = createLayer();

   private SeeThroughParticle() {
   }

   private static Layer createLayer() {
      try {
         RenderPipeline pipeline = Pipelines.copy(RenderPipelines.TRANSLUCENT_PARTICLE)
            .withLocation(Identifier.fromNamespaceAndPath("ketamineclub", "pipeline/see_through_translucent_particle"))
            .withDepthStencilState(Optional.empty())
            .build();
         return new Layer(true, TextureAtlas.LOCATION_PARTICLES, pipeline);
      } catch (Throwable var1) {
         return Layer.TRANSLUCENT;
      }
   }
}
