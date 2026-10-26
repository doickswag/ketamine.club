package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.slop.CrystalChangerModule;

@Mixin({EndCrystalRenderer.class})
public class MixinEndCrystalRenderer {
   public MixinEndCrystalRenderer() {
   }

   @Inject(
      method = {"submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",
         ordinal = 0,
         shift = Shift.AFTER
      )}
   )
   private void ketamineclub$crystalScale(
      EndCrystalRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci
   ) {
      CrystalChangerModule module = CrystalChangerModule.active();
      if (module != null) {
         module.applyScale(poseStack);
      }
   }
}
