package we.ketamine.club.mixin.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.render.NametagsModule;

@Mixin({EntityRenderer.class})
public class MixinEntityRenderer {
   public MixinEntityRenderer() {
   }

   @Inject(
      method = {"submitNameDisplay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cancelVanillaNameTag(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
      NametagsModule nametags = KetamineClub.moduleManager.getModuleByClass(NametagsModule.class);
      if (nametags != null && nametags.isEnabled()) {
         ci.cancel();
      }
   }
}
