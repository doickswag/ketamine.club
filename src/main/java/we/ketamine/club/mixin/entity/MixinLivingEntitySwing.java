package we.ketamine.club.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.features.modules.render.ViewModelModule;

@Mixin({LivingEntity.class})
public abstract class MixinLivingEntitySwing {
   public MixinLivingEntitySwing() {
   }

   @Inject(
      method = {"getCurrentSwingDuration"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void ketamineclub$swingSpeed(CallbackInfoReturnable<Integer> cir) {
      ViewModelModule module = this.ketamineclub$activeModule();
      if (module != null) {
         int duration = Math.max(1, (int)Math.round(((Integer)cir.getReturnValue()).intValue() / module.swingSpeed.getValue()));
         cir.setReturnValue(duration);
      }
   }

   @ModifyExpressionValue(
      method = {"swing(Lnet/minecraft/world/InteractionHand;Z)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;getCurrentSwingDuration()I"
      )}
   )
   private int ketamineclub$swingDelay(int duration) {
      ViewModelModule module = this.ketamineclub$activeModule();
      return module == null ? duration : module.swingDelay.getValue() * 2;
   }

   private ViewModelModule ketamineclub$activeModule() {
      if (!(((Object)this) instanceof LocalPlayer)) {
         return null;
      } else {
         ViewModelModule module = ViewModelModule.getInstance();
         return module != null && module.isEnabled() ? module : null;
      }
   }
}
