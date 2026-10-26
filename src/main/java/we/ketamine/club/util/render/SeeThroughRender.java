package we.ketamine.club.util.render;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.platform.CompareOp;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup.RenderSetupBuilder;
import net.minecraft.client.renderer.rendertype.RenderSetup.TextureBinding;
import we.ketamine.club.mixin.render.RenderSetupAccessor;
import we.ketamine.club.mixin.render.RenderTypeAccessor;

public final class SeeThroughRender {
   public static final int SEED_ORDER_BASE = 100000;
   public static final int COLOR_ORDER_BASE = 200000;
   public static final int SHINE_ORDER_BASE = 300000;
   public static boolean active;
   public static boolean cloning;
   public static boolean shine;
   public static int shineRgb = 16777215;
   public static int shineOpacity = 100;
   public static int cloneSubOrder;
   private static final ConcurrentHashMap<RenderType, RenderType> SEED_CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<RenderType, RenderType> COLOR_CACHE = new ConcurrentHashMap<>();
   private static volatile RenderType shineType;

   private SeeThroughRender() {
   }

   public static void begin(boolean applyShine, int rgb, int opacity) {
      active = true;
      shine = applyShine;
      shineRgb = rgb & 16777215;
      shineOpacity = Math.max(0, opacity);
      cloneSubOrder = 0;
   }

   public static void end() {
      active = false;
      shine = false;
      shineRgb = 16777215;
      shineOpacity = 100;
   }

   public static boolean isGlintType(RenderType type) {
      if (type == null) {
         return false;
      } else {
         String name = type.toString().toLowerCase(Locale.ROOT);
         return name.contains("glint") || name.contains("foil");
      }
   }

   public static RenderType wrapDepthSeed(RenderType original) {
      return original == null
         ? null
         : SEED_CACHE.computeIfAbsent(original, o -> build(o, "_ketamineclub_seethrough_seed", CompareOp.LESS_THAN, true, false, Boolean.FALSE));
   }

   public static RenderType wrapColor(RenderType original) {
      return original == null
         ? null
         : COLOR_CACHE.computeIfAbsent(original, o -> build(o, "_ketamineclub_seethrough_color", CompareOp.GREATER_THAN_OR_EQUAL, true, true, null));
   }

   public static RenderType shineType() {
      RenderType result = shineType;
      if (result != null) {
         return result;
      } else {
         synchronized (SeeThroughRender.class) {
            if (shineType == null) {
               shineType = build(RenderTypes.armorEntityGlint(), "_ketamineclub_seethrough_shine", CompareOp.ALWAYS_PASS, false, true, Boolean.FALSE);
            }

            return shineType;
         }
      }
   }

   public static int shinePassCount() {
      return shine ? (shineOpacity + 99) / 100 : 0;
   }

   public static int shineTint(int pass) {
      int remaining = shineOpacity - pass * 100;
      int alpha = Math.round(255.0F * Math.min(Math.max(remaining, 0), 100) / 100.0F);
      return alpha << 24 | shineRgb;
   }

   private static RenderType build(RenderType original, String suffix, CompareOp depthFunc, boolean depthWrite, boolean colorWrite, Boolean cullOverride) {
      try {
         RenderSetup origSetup = ((RenderTypeAccessor)original).ketamineclub$getState();
         RenderSetupAccessor sa = (RenderSetupAccessor)(Object)origSetup;
         RenderPipeline pipeline = clonePipeline(original.pipeline(), suffix, depthFunc, depthWrite, colorWrite, cullOverride);
         RenderSetupBuilder b = RenderSetup.builder(pipeline);

         for (Entry<String, TextureBinding> e : sa.ketamineclub$getTextures().entrySet()) {
            TextureBinding binding = e.getValue();
            if (binding.sampler() != null) {
               b.withTexture(e.getKey(), binding.location(), binding.sampler());
            } else {
               b.withTexture(e.getKey(), binding.location());
            }
         }

         if (sa.ketamineclub$useLightmap()) {
            b.useLightmap();
         }

         if (sa.ketamineclub$useOverlay()) {
            b.useOverlay();
         }

         if (sa.ketamineclub$affectsCrumbling()) {
            b.affectsCrumbling();
         }

         if (sa.ketamineclub$sortOnUpload()) {
            b.sortOnUpload();
         }

         b.setLayeringTransform(sa.ketamineclub$getLayeringTransform());
         b.setOutputTarget(sa.ketamineclub$getOutputTarget());
         b.setTextureTransform(sa.ketamineclub$getTextureTransform());
         b.setOutline(sa.ketamineclub$getOutlineProperty());
         return RenderTypeAccessor.ketamineclub$create(original.toString() + suffix, b.createRenderSetup());
      } catch (Throwable var13) {
         return original;
      }
   }

   private static RenderPipeline clonePipeline(
      RenderPipeline orig, String suffix, CompareOp depthFunc, boolean depthWrite, boolean colorWrite, Boolean cullOverride
   ) {
      DepthStencilState originalDepth = orig.getDepthStencilState();
      float depthBiasScale = originalDepth == null ? 0.0F : originalDepth.depthBiasScaleFactor();
      float depthBiasConstant = originalDepth == null ? 0.0F : originalDepth.depthBiasConstant();
      Builder b = Pipelines.copy(orig)
         .withLocation(orig.getLocation().withSuffix(suffix))
         .withDepthStencilState(new DepthStencilState(depthFunc, depthWrite, depthBiasScale, depthBiasConstant))
         .withCull(cullOverride != null ? cullOverride : orig.isCull());
      ColorTargetState[] colorTargets = orig.getColorTargetStates();

      for (int index = 0; index < colorTargets.length; index++) {
         ColorTargetState target = colorTargets[index];
         if (target == null) {
            b.withUnusedColorTargetState(index);
         } else {
            int writeMask = colorWrite ? target.writeMask() : 0;
            b.withColorTargetState(index, new ColorTargetState(target.blendFunction(), target.format(), writeMask));
         }
      }

      return b.build();
   }
}
