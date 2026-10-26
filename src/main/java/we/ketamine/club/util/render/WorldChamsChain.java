package we.ketamine.club.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

public final class WorldChamsChain {
   private static final Identifier SCREENQUAD = Identifier.fromNamespaceAndPath("minecraft", "core/screenquad");
   private static final Identifier BLIT_FSH = Identifier.fromNamespaceAndPath("minecraft", "post/blit");
   private static final Identifier H_FSH = Identifier.fromNamespaceAndPath("ketamineclub", "post/chams_default_h");
   private static final Identifier C_FSH = Identifier.fromNamespaceAndPath("ketamineclub", "post/chams_default_c");
   private static final Identifier V_FSH = Identifier.fromNamespaceAndPath("ketamineclub", "post/chams_default_v");
   private static final Identifier CHAMS_H = Identifier.fromNamespaceAndPath("ketamineclub", "chams_h");
   private static final Identifier CHAMS_C = Identifier.fromNamespaceAndPath("ketamineclub", "chams_c");
   private static final Identifier COMPOSITE = Identifier.fromNamespaceAndPath("ketamineclub", "chams_composite");
   private static final Identifier CHAIN_NAME = Identifier.fromNamespaceAndPath("ketamineclub", "chams_default_runtime");
   private static Projection projection;
   private static ProjectionMatrixBuffer projectionMatrixBuffer;
   private static PostChain cached;
   private static Set<Identifier> lastExternalTargets = Set.of();
   private static int lastOutlineWidth = Integer.MIN_VALUE;
   private static float lastOutlineAlpha = Float.NaN;
   private static int lastGlowRadius = Integer.MIN_VALUE;
   private static float lastGlowIntensity = Float.NaN;
   private static float lastFillTint = Float.NaN;
   private static float lastFillAlpha = Float.NaN;
   private static int lastInnerGlowRadius = Integer.MIN_VALUE;
   private static float lastInnerGlowIntensity = Float.NaN;
   private static WorldChamsChain.BuildKey failedBuildKey;
   private static boolean reportedBuildFailure;

   private WorldChamsChain() {
   }

   public static PostChain get(
      Set<Identifier> externalTargets,
      int outlineWidth,
      float outlineAlpha,
      int glowRadius,
      float glowIntensity,
      float fillTint,
      float fillAlpha,
      int innerGlowRadius,
      float innerGlowIntensity
   ) {
      Set<Identifier> targetKey = Set.copyOf(externalTargets);
      if (cached != null
         && targetKey.equals(lastExternalTargets)
         && outlineWidth == lastOutlineWidth
         && outlineAlpha == lastOutlineAlpha
         && glowRadius == lastGlowRadius
         && glowIntensity == lastGlowIntensity
         && fillTint == lastFillTint
         && fillAlpha == lastFillAlpha
         && innerGlowRadius == lastInnerGlowRadius
         && innerGlowIntensity == lastInnerGlowIntensity) {
         return cached;
      } else {
         WorldChamsChain.BuildKey buildKey = new WorldChamsChain.BuildKey(
            targetKey, outlineWidth, outlineAlpha, glowRadius, glowIntensity, fillTint, fillAlpha, innerGlowRadius, innerGlowIntensity
         );
         if (buildKey.equals(failedBuildKey)) {
            return cached;
         } else {
            PostChain rebuilt = build(
               externalTargets, outlineWidth, outlineAlpha, glowRadius, glowIntensity, fillTint, fillAlpha, innerGlowRadius, innerGlowIntensity
            );
            if (rebuilt == null) {
               failedBuildKey = buildKey;
               return cached;
            } else {
               PostChain previous = cached;
               cached = rebuilt;
               failedBuildKey = null;
               reportedBuildFailure = false;
               lastExternalTargets = targetKey;
               lastOutlineWidth = outlineWidth;
               lastOutlineAlpha = outlineAlpha;
               lastGlowRadius = glowRadius;
               lastGlowIntensity = glowIntensity;
               lastFillTint = fillTint;
               lastFillAlpha = fillAlpha;
               lastInnerGlowRadius = innerGlowRadius;
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
      lastExternalTargets = Set.of();
      lastOutlineWidth = Integer.MIN_VALUE;
      lastOutlineAlpha = Float.NaN;
      failedBuildKey = null;
      reportedBuildFailure = false;
      retire(chain, matrixBuffer);
   }

   private static PostChain build(
      Set<Identifier> externalTargets,
      int lineWidth,
      float outlineAlpha,
      int glowThickness,
      float glowIntensity,
      float fillTint,
      float fillAlpha,
      int innerGlowThickness,
      float innerGlowIntensity
   ) {
      try {
         if (projection == null) {
            projection = new Projection();
            projection.setupOrtho(0.1F, 1000.0F, 1.0F, 1.0F, false);
         }

         if (projectionMatrixBuffer == null) {
            projectionMatrixBuffer = new ProjectionMatrixBuffer("ketamineclub_chams");
         }

         Identifier outlineTarget = LevelTargetBundle.ENTITY_OUTLINE_TARGET_ID;
         Pass passH = new Pass(
            SCREENQUAD,
            H_FSH,
            List.of(new TargetInput("In", outlineTarget, false, false)),
            CHAMS_H,
            Map.of("DilateConfig", List.of(integer(lineWidth), integer(glowThickness), integer(innerGlowThickness)))
         );
         Pass passC = new Pass(
            SCREENQUAD,
            C_FSH,
            List.of(new TargetInput("In", outlineTarget, false, false)),
            CHAMS_C,
            Map.of("ColorConfig", List.of(integer(lineWidth), integer(glowThickness)))
         );
         List<UniformValue> chamsConfig = List.of(
            flt(glowIntensity),
            flt(fillTint),
            flt(fillAlpha),
            integer(glowThickness),
            integer(lineWidth),
            flt(outlineAlpha),
            flt(innerGlowIntensity),
            integer(innerGlowThickness)
         );
         Pass passV = new Pass(
            SCREENQUAD,
            V_FSH,
            List.of(
               new TargetInput("In", CHAMS_H, false, false),
               new TargetInput("Color", CHAMS_C, false, false),
               new TargetInput("Orig", outlineTarget, false, false)
            ),
            COMPOSITE,
            Map.of("ChamsConfig", chamsConfig)
         );
         Pass blit = new Pass(
            SCREENQUAD,
            BLIT_FSH,
            List.of(new TargetInput("In", COMPOSITE, false, false)),
            outlineTarget,
            Map.of("BlitConfig", List.of(new Vec4Uniform(new Vector4f(1.0F))))
         );
         PostChainConfig config = new PostChainConfig(
            Map.of(
               CHAMS_H,
               new InternalTarget(Optional.empty(), Optional.empty(), false, 0),
               CHAMS_C,
               new InternalTarget(Optional.empty(), Optional.empty(), false, 0),
               COMPOSITE,
               new InternalTarget(Optional.empty(), Optional.empty(), false, 0)
            ),
            List.of(passH, passC, passV, blit)
         );
         return PostChain.load(config, Minecraft.getInstance().getTextureManager(), externalTargets, CHAIN_NAME, projection, projectionMatrixBuffer);
      } catch (Exception var16) {
         if (!reportedBuildFailure) {
            reportedBuildFailure = true;
            KetamineClub.LOGGER.error("Failed to build world shader post chain", var16);
         }

         return null;
      }
   }

   private static UniformValue flt(float v) {
      return new FloatUniform(v);
   }

   private static UniformValue integer(int v) {
      return new IntUniform(v);
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
      Set<Identifier> externalTargets,
      int outlineWidth,
      float outlineAlpha,
      int glowRadius,
      float glowIntensity,
      float fillTint,
      float fillAlpha,
      int innerGlowRadius,
      float innerGlowIntensity
   ) {
   }
}
