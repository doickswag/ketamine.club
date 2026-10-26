package we.ketamine.club.mixin.entity;

import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.movement.VerticalDesyncModule;

@Mixin({FireworkRocketEntity.class})
public class MixinFireworkRocketEntity {
   public MixinFireworkRocketEntity() {
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
         shift = Shift.AFTER
      )}
   )
   private void ketamineclub$saturateAttachedRocketBoost(CallbackInfo ci) {
      if (KetamineClub.moduleManager != null) {
         VerticalDesyncModule vertical = KetamineClub.moduleManager.getModuleByClass(VerticalDesyncModule.class);
         if (vertical != null) {
            vertical.applyVerticalBoost((FireworkRocketEntity)(Object)this);
         }
      }
   }
}
