package we.ketamine.club.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.render.NoRenderModule;

@Mixin({ChestRenderer.class})
public class MixinChestRenderer {
   public MixinChestRenderer() {
   }

   @Inject(
      method = {"submit(Lnet/minecraft/client/renderer/blockentity/state/ChestRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$tileEntityRange(
      ChestRenderState state, PoseStack matrices, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci
   ) {
      NoRenderModule module = NoRenderModule.getInstance();
      if (module != null && module.isEnabled() && module.tileEntity.getValue() >= 2) {
         Vec3 cameraPos = camera.pos;
         double distanceSquared = state.blockPos.distToCenterSqr(cameraPos);
         double maxDistance = Math.pow(module.tileEntity.getValue().intValue(), 2.0);
         if (distanceSquared > maxDistance) {
            ci.cancel();
         }
      }
   }
}
