package we.ketamine.club.mixin.entity;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.pingbypass.PingBypass;

@Mixin({Entity.class})
public class MixinPingBypassEntityId {
   @Shadow
   private int id;

   public MixinPingBypassEntityId() {
   }

   @Inject(
      method = {"getId"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void legacyEntityId(CallbackInfoReturnable<Integer> cir) {
      if (this.id == 0 && PingBypass.get().isTranslatedViewer()) {
         cir.setReturnValue(0);
      }
   }
}
