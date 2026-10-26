package we.ketamine.club.mixin.render.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.features.modules.render.NoRenderModule;
import we.ketamine.club.util.traits.Util;

@Mixin({Hud.class})
public abstract class MixinInGameHud {
   @Shadow
   @Final
   private static Identifier POWDER_SNOW_OUTLINE_LOCATION;

   public MixinInGameHud() {
   }

   @Shadow
   protected abstract void extractTextureOverlay(GuiGraphicsExtractor var1, Identifier var2, float var3);

   @Inject(
      method = {"extractRenderState"},
      at = {@At("RETURN")}
   )
   public void extractRenderState(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen()) {
         Render2DEvent event = new Render2DEvent(context, tickCounter.getGameTimeDeltaPartialTick(true));
         Util.EVENT_BUS.post(event);
      }
   }

   @Inject(
      method = {"extractEffects"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noPotions(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noPotIcon.getValue())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractCrosshair"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noCrosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noCrosshair.getValue())) {
         ci.cancel();
      }
   }

   @ModifyExpressionValue(
      method = {"extractCrosshair", "extractHotbarAndDecorations"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;",
         ordinal = 0
      )}
   )
   private Object ketamineclub$noCooldown(Object value) {
      return value instanceof AttackIndicatorStatus && NoRenderModule.isActive(m -> m.noCooldown.getValue()) ? AttackIndicatorStatus.OFF : value;
   }

   @Inject(
      method = {"extractSelectedItemName"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noItemName(GuiGraphicsExtractor context, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noItemName.getValue())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractPortalOverlay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noPortal(GuiGraphicsExtractor context, float alpha, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noPortal.getValue())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractVignette"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noVignette(GuiGraphicsExtractor context, Entity entity, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noVignette.getValue())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractConfusionOverlay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noNausea(GuiGraphicsExtractor context, float distortionStrength, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noNausea.getValue())) {
         ci.cancel();
      }
   }

   @Redirect(
      method = {"extractCameraOverlays"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V"
      )
   )
   private void ketamineclub$noCameraOverlay(Hud instance, GuiGraphicsExtractor context, Identifier texture, float alpha) {
      if (!NoRenderModule.isActive(m -> m.noPowderedSnow.getValue()) || !POWDER_SNOW_OUTLINE_LOCATION.equals(texture)) {
         if (!NoRenderModule.isActive(m -> m.noPumpkin.getValue()) || !texture.getPath().contains("pumpkin")) {
            this.extractTextureOverlay(context, texture, alpha);
         }
      }
   }
}
