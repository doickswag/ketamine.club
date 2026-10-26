package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.render.NoRenderModule;

@Mixin({CustomHeadLayer.class})
public class MixinCustomHeadLayer<S extends LivingEntityRenderState, M extends EntityModel<S> & HeadedModel> {
   public MixinCustomHeadLayer() {
   }

   @Inject(
      method = {"submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noPlayerHead(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, S renderState, float f, float g, CallbackInfo ci) {
      if (NoRenderModule.isActive(m -> m.noArmor.getValue()) && renderState.entityType == EntityTypes.PLAYER) {
         ci.cancel();
      }
   }
}
