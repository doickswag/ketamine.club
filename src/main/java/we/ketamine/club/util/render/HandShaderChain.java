package we.ketamine.club.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.client.renderer.PostChainConfig.InternalTarget;
import net.minecraft.client.renderer.PostChainConfig.Pass;
import net.minecraft.client.renderer.PostChainConfig.TargetInput;
import net.minecraft.client.renderer.UniformValue.FloatUniform;
import net.minecraft.client.renderer.UniformValue.IntUniform;
import net.minecraft.client.renderer.UniformValue.Vec4Uniform;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import we.ketamine.club.KetamineClub;

public final class HandShaderChain {
   private static final Identifier SCREENQUAD = Identifier.fromNamespaceAndPath("minecraft", "core/screenquad");
   private static final Identifier BLIT_FSH = Identifier.fromNamespaceAndPath("minecraft", "post/blit");
   private static final Identifier DILATE_H_FSH = Identifier.fromNamespaceAndPath("ketamineclub", "post/hand_outline_h");
   private static final Identifier OUTLINE_FSH = Identifier.fromNamespaceAndPath("ketamineclub", "post/hand_outline");
   private static final Identifier GLOW_H_FSH = Identifier.fromNamespaceAndPath("ketamineclub", "post/hand_glow_h");
   private static final Identifier INNER_GLOW_H_FSH = Identifier.fromNamespaceAndPath("ketamineclub", "post/hand_inner_glow_h");
   private static final Identifier OUTLINE_GLOW_FSH = Identifier.fromNamespaceAndPath("ketamineclub", "post/hand_outline_glow");
   private static final Identifier DILATED = Identifier.fromNamespaceAndPath("ketamineclub", "hand_dilated");
   private static final Identifier GLOW_H = Identifier.fromNamespaceAndPath("ketamineclub", "hand_glow_h");
   private static final Identifier INNER_GLOW_H = Identifier.fromNamespaceAndPath("ketamineclub", "hand_inner_glow_h");
   private static final Identifier COMPOSITE = Identifier.fromNamespaceAndPath("ketamineclub", "hand_composite");
   private static final Identifier CHAIN_NAME = Identifier.fromNamespaceAndPath("ketamineclub", "hand_shader_runtime");
   private static Projection projection;
   private static ProjectionMatrixBuffer projectionMatrixBuffer;
   private static PostChain cached;
   private static int lastColor = Integer.MIN_VALUE;
   private static int lastLineWidth = Integer.MIN_VALUE;
   private static float lastOutlineAlpha = Float.NaN;
   private static float lastFillAlpha = Float.NaN;
   private static boolean lastFill;
   private static int lastGlowRadius = Integer.MIN_VALUE;
   private static float lastGlowIntensity = Float.NaN;
   private static int lastInnerGlowRadius = Integer.MIN_VALUE;
   private static float lastInnerGlowIntensity = Float.NaN;
   private static HandShaderChain.BuildKey failedBuildKey;
   private static boolean reportedBuildFailure;

   private HandShaderChain() {
   }

   public static PostChain get(
      int color, int thickness, float outlineAlpha, float fillAlpha, int glowRadius, float glowIntensity, int innerGlowRadius, float innerGlowIntensity
   ) {
      int lineWidth = outlineAlpha > 0.0F ? Math.max(0, thickness) : 0;
      float outlineOpacity = lineWidth > 0 ? clamp01(outlineAlpha) : 0.0F;
      float fillOpacity = clamp01(fillAlpha);
      boolean fill = fillOpacity > 0.0F;
      int glowSize = Math.max(0, glowRadius);
      int innerGlowSize = Math.max(0, innerGlowRadius);
      if (lineWidth == 0 && !fill && glowSize == 0 && innerGlowSize == 0) {
         return null;
      } else if (cached != null
         && color == lastColor
         && lineWidth == lastLineWidth
         && outlineOpacity == lastOutlineAlpha
         && fillOpacity == lastFillAlpha
         && fill == lastFill
         && glowSize == lastGlowRadius
         && glowIntensity == lastGlowIntensity
         && innerGlowSize == lastInnerGlowRadius
         && innerGlowIntensity == lastInnerGlowIntensity) {
         return cached;
      } else {
         HandShaderChain.BuildKey buildKey = new HandShaderChain.BuildKey(
            color, lineWidth, outlineOpacity, fillOpacity, glowSize, glowIntensity, innerGlowSize, innerGlowIntensity
         );
         if (buildKey.equals(failedBuildKey)) {
            return cached;
         } else {
            PostChain rebuilt = build(color, lineWidth, outlineOpacity, fillOpacity, glowSize, glowIntensity, innerGlowSize, innerGlowIntensity);
            if (rebuilt == null) {
               failedBuildKey = buildKey;
               return cached;
            } else {
               PostChain previous = cached;
               cached = rebuilt;
               failedBuildKey = null;
               reportedBuildFailure = false;
               lastColor = color;
               lastLineWidth = lineWidth;
               lastOutlineAlpha = outlineOpacity;
               lastFillAlpha = fillOpacity;
               lastFill = fill;
               lastGlowRadius = glowSize;
               lastGlowIntensity = glowIntensity;
               lastInnerGlowRadius = innerGlowSize;
               lastInnerGlowIntensity = innerGlowIntensity;
               retire(previous, null);
               return cached;
            }
         }
      }
   }

   public static void cleanup() {
      PostChain chain = cached;
      ProjectionMatrixBuffer matrixBuffer = projectionMatrixBuffer;
      cached = null;
      projectionMatrixBuffer = null;
      projection = null;
      failedBuildKey = null;
      reportedBuildFailure = false;
      retire(chain, matrixBuffer);
   }

   private static PostChain build(
      int color, int lineWidth, float outlineAlpha, float fillAlpha, int glowRadius, float glowIntensity, int innerGlowRadius, float innerGlowIntensity
   ) {
      try {
         if (projection == null) {
            projection = new Projection();
            projection.setupOrtho(0.1F, 1000.0F, 1.0F, 1.0F, false);
         }

         if (projectionMatrixBuffer == null) {
            projectionMatrixBuffer = new ProjectionMatrixBuffer("ketamineclub_hand");
         }

         UniformValue handColor = new Vec4Uniform(new Vector4f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F));
         Pass dilate = new Pass(
            SCREENQUAD,
            DILATE_H_FSH,
            List.of(new TargetInput("In", PostChain.MAIN_TARGET_ID, false, false)),
            DILATED,
            Map.of("DilateConfig", List.of(integer(lineWidth)))
         );
         PostChainConfig config = glowRadius <= 0 && innerGlowRadius <= 0
            ? buildPlain(dilate, handColor, fillAlpha, outlineAlpha, lineWidth)
            : buildGlow(dilate, handColor, fillAlpha, outlineAlpha, lineWidth, glowRadius, glowIntensity, innerGlowRadius, innerGlowIntensity);
         return PostChain.load(
            config, Minecraft.getInstance().getTextureManager(), LevelTargetBundle.MAIN_TARGETS, CHAIN_NAME, projection, projectionMatrixBuffer
         );
      } catch (Exception var11) {
         if (!reportedBuildFailure) {
            reportedBuildFailure = true;
            KetamineClub.LOGGER.error("Failed to build hand shader post chain", var11);
         }

         return null;
      }
   }

   private static PostChainConfig buildPlain(Pass dilate, UniformValue handColor, float fillAlpha, float outlineAlpha, int lineWidth) {
      Pass outline = new Pass(
         SCREENQUAD,
         OUTLINE_FSH,
         List.of(new TargetInput("In", DILATED, false, false), new TargetInput("Orig", PostChain.MAIN_TARGET_ID, false, false)),
         COMPOSITE,
         Map.of("OutlineConfig", List.of(flt(fillAlpha), flt(outlineAlpha), integer(lineWidth)), "HandColor", List.of(handColor))
      );
      Pass blit = blit(COMPOSITE, PostChain.MAIN_TARGET_ID);
      return new PostChainConfig(
         Map.of(
            DILATED,
            new InternalTarget(Optional.empty(), Optional.empty(), false, 0),
            COMPOSITE,
            new InternalTarget(Optional.empty(), Optional.empty(), false, 0)
         ),
         List.of(dilate, outline, blit)
      );
   }

   private static PostChainConfig buildGlow(
      Pass dilate,
      UniformValue handColor,
      float fillAlpha,
      float outlineAlpha,
      int lineWidth,
      int glowRadius,
      float glowIntensity,
      int innerGlowRadius,
      float innerGlowIntensity
   ) {
      Pass glow = new Pass(
         SCREENQUAD,
         GLOW_H_FSH,
         List.of(new TargetInput("In", PostChain.MAIN_TARGET_ID, false, false)),
         GLOW_H,
         Map.of("GlowConfig", List.of(integer(glowRadius)))
      );
      Pass innerGlow = new Pass(
         SCREENQUAD,
         INNER_GLOW_H_FSH,
         List.of(new TargetInput("In", PostChain.MAIN_TARGET_ID, false, false)),
         INNER_GLOW_H,
         Map.of("InnerGlowConfig", List.of(integer(innerGlowRadius)))
      );
      List<UniformValue> outlineConfig = List.of(
         flt(fillAlpha), flt(outlineAlpha), flt(glowIntensity), integer(lineWidth), integer(glowRadius), flt(innerGlowIntensity), integer(innerGlowRadius)
      );
      Pass outline = new Pass(
         SCREENQUAD,
         OUTLINE_GLOW_FSH,
         List.of(
            new TargetInput("In", DILATED, false, false),
            new TargetInput("Glow", GLOW_H, false, false),
            new TargetInput("InnerGlow", INNER_GLOW_H, false, false),
            new TargetInput("Orig", PostChain.MAIN_TARGET_ID, false, false)
         ),
         COMPOSITE,
         Map.of("OutlineConfig", outlineConfig, "HandColor", List.of(handColor))
      );
      Pass blit = blit(COMPOSITE, PostChain.MAIN_TARGET_ID);
      return new PostChainConfig(
         Map.of(
            DILATED,
            new InternalTarget(Optional.empty(), Optional.empty(), false, 0),
            GLOW_H,
            new InternalTarget(Optional.empty(), Optional.empty(), false, 0),
            INNER_GLOW_H,
            new InternalTarget(Optional.empty(), Optional.empty(), false, 0),
            COMPOSITE,
            new InternalTarget(Optional.empty(), Optional.empty(), false, 0)
         ),
         List.of(dilate, glow, innerGlow, outline, blit)
      );
   }

   private static Pass blit(Identifier input, Identifier output) {
      return new Pass(
         SCREENQUAD, BLIT_FSH, List.of(new TargetInput("In", input, false, false)), output, Map.of("BlitConfig", List.of(new Vec4Uniform(new Vector4f(1.0F))))
      );
   }

   private static UniformValue flt(float value) {
      return new FloatUniform(value);
   }

   private static UniformValue integer(int value) {
      return new IntUniform(value);
   }

   private static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   private static void retire(PostChain chain, ProjectionMatrixBuffer matrixBuffer) {
      if (chain != null || matrixBuffer != null) {
         RenderSystem.queueFencedTask(() -> {
            try {
               if (chain != null) {
                  chain.close();
               }
            } finally {
               if (matrixBuffer != null) {
                  matrixBuffer.close();
               }
            }
         });
      }
   }

   private record BuildKey(
      int color, int lineWidth, float outlineAlpha, float fillAlpha, int glowRadius, float glowIntensity, int innerGlowRadius, float innerGlowIntensity
   ) {
   }
}
