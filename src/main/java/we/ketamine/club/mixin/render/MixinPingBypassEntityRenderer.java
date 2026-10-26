package we.ketamine.club.mixin.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.pingbypass.PingBypassMotionSmoothing;

@Mixin({EntityRenderer.class})
public class MixinPingBypassEntityRenderer {
   public MixinPingBypassEntityRenderer() {
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V"
      )}
   )
   private void pingbypass$smoothPosition(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
      Vec3 position = PingBypassMotionSmoothing.renderPosition(entity);
      if (position != null) {
         state.x = position.x;
         state.y = position.y;
         state.z = position.z;
         state.passengerOffset = null;
      }
   }
}
