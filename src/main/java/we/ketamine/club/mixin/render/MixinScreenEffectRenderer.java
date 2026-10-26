package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.render.NoRenderModule;

@Mixin({ScreenEffectRenderer.class})
public class MixinScreenEffectRenderer {
   public MixinScreenEffectRenderer() {
   }

   @Inject(
      method = {"submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void ketamineclub$noFire(PoseStack matrices, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noFire.getValue())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"submitWater(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void ketamineclub$noWater(Minecraft client, PoseStack matrices, SubmitNodeCollector collector, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noLiquid.getValue())) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"submitBlockSprite(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void ketamineclub$noWall(TextureAtlasSprite sprite, PoseStack matrices, SubmitNodeCollector collector, int color, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noWall.getValue())) {
         ci.cancel();
      }
   }
}
