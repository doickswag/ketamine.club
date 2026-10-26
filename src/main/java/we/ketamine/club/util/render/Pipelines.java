package we.ketamine.club.util.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

class Pipelines {
   static final RenderPipeline GUI_HORIZONTAL_GRADIENT_PIPELINE = copy(RenderPipelines.GUI)
      .withLocation(Identifier.fromNamespaceAndPath("ketamineclub", "pipeline/gui_horizontal_gradient"))
      .build();
   static final RenderPipeline GLOBAL_QUADS_PIPELINE = copy(RenderPipelines.DEBUG_FILLED_BOX)
      .withLocation(Identifier.fromNamespaceAndPath("ketamineclub", "pipeline/global_fill"))
      .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
      .withPrimitiveTopology(PrimitiveTopology.QUADS)
      .withDepthStencilState(Optional.empty())
      .withColorTargetState(translucent(RenderPipelines.DEBUG_FILLED_BOX.getColorTargetState()))
      .withCull(false)
      .build();
   static final RenderPipeline GLOBAL_LINES_PIPELINE = copy(RenderPipelines.LINES)
      .withLocation(Identifier.fromNamespaceAndPath("ketamineclub", "pipeline/global_lines"))
      .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
      .withPrimitiveTopology(PrimitiveTopology.LINES)
      .withDepthStencilState(Optional.empty())
      .withColorTargetState(translucent(RenderPipelines.LINES.getColorTargetState()))
      .withCull(false)
      .build();

   Pipelines() {
   }

   static Builder copy(RenderPipeline original) {
      Snippet snippet = new Snippet(
         Optional.of(original.getVertexShader()),
         Optional.of(original.getFragmentShader()),
         Optional.of(original.getShaderDefines()),
         Optional.of(original.getBindGroupLayouts()),
         (ColorTargetState[])original.getColorTargetStates().clone(),
         original.getColorTargetStates().length,
         Optional.ofNullable(original.getDepthStencilState()),
         Optional.of(original.getPolygonMode()),
         Optional.of(original.isCull()),
         (VertexFormat[])original.getVertexFormatBindings().clone(),
         Optional.of(original.getPrimitiveTopology())
      );
      return RenderPipeline.builder(new Snippet[]{snippet}).withLocation(original.getLocation());
   }

   private static ColorTargetState translucent(ColorTargetState original) {
      return new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), original.format(), 15);
   }
}
