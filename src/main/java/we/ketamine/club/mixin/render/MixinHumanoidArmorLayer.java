package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.render.NoRenderModule;

@Mixin({HumanoidArmorLayer.class})
public class MixinHumanoidArmorLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> {
   public MixinHumanoidArmorLayer() {
   }

   @Inject(
      method = {"submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noArmor(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, S renderState, float f, float g, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noArmor.getValue()) && renderState.entityType == EntityTypes.PLAYER) {
         ci.cancel();
      }
   }
}
