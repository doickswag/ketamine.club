package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.render.SeeThroughModule;
import we.ketamine.club.util.render.SeeThroughRender;

@Mixin({EntityRenderDispatcher.class})
public class MixinEntityRenderDispatcher {
   public MixinEntityRenderDispatcher() {
   }

   @Redirect(
      method = {"submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
      )
   )
   private void ketamineclub$seeThroughSubmit(
      EntityRenderer renderer, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera
   ) {
      boolean apply = false;
      SeeThroughModule seeThrough = KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(SeeThroughModule.class);
      if (seeThrough != null && seeThrough.isEnabled() && seeThrough.shouldSeeThrough(state)) {
         apply = true;
         SeeThroughRender.begin(seeThrough.shine.getValue(), seeThrough.shineRgb(), seeThrough.shineOpacity.getValue());
      }

      try {
         renderer.submit(state, poseStack, collector, camera);
      } finally {
         if (apply) {
            SeeThroughRender.end();
         }
      }
   }
}
