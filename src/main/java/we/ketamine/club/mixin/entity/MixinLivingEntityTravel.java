package we.ketamine.club.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.movement.ElytraTestModule;
import we.ketamine.club.manager.RotationManager;
import we.ketamine.club.pingbypass.PingBypass;
import we.ketamine.club.util.traits.Util;

@Mixin({LivingEntity.class})
public class MixinLivingEntityTravel {
   @Unique
   private float ketamineclub$origYaw;
   @Unique
   private float ketamineclub$origPitch;
   @Unique
   private boolean ketamineclub$applied;

   public MixinLivingEntityTravel() {
   }

   @Inject(
      method = {"travel"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$travelHead(Vec3 movementInput, CallbackInfo ci) {
      this.ketamineclub$applied = false;
      LivingEntity self = (LivingEntity)(Object)this;
      if (self == Util.mc.player && PingBypass.get().suppressViewerMovement()) {
         ci.cancel();
      } else {
         if (self == Util.mc.player && this.ketamineclub$spoofing()) {
            this.ketamineclub$origYaw = self.getYRot();
            this.ketamineclub$origPitch = self.getXRot();
            self.setYRot(KetamineClub.rotationManager.getMovementYaw());
            self.setXRot(KetamineClub.rotationManager.getMovementPitch());
            this.ketamineclub$applied = true;
         }
      }
   }

   @Inject(
      method = {"travel"},
      at = {@At("RETURN")}
   )
   private void ketamineclub$travelReturn(Vec3 movementInput, CallbackInfo ci) {
      LivingEntity self = (LivingEntity)(Object)this;
      if (this.ketamineclub$applied) {
         this.ketamineclub$applied = false;
         self.setYRot(this.ketamineclub$origYaw);
         self.setXRot(this.ketamineclub$origPitch);
      }
   }

   @ModifyExpressionValue(
      method = {"jumpFromGround"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"
      )}
   )
   private float ketamineclub$jumpYaw(float original) {
      return (Object)this == Util.mc.player && this.ketamineclub$spoofing() ? KetamineClub.rotationManager.getMovementYaw() : original;
   }

   @Unique
   private boolean ketamineclub$spoofing() {
      RotationManager rm = KetamineClub.rotationManager;
      return rm != null && rm.isMoveFixEnabled() && rm.hasMovementRotation();
   }

   @ModifyExpressionValue(
      method = {"handleRelativeFrictionAndCalculateMovement"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/world/entity/LivingEntity;horizontalCollision:Z"
      )}
   )
   private boolean ketamineclub$noScaffoldClimb(boolean original) {
      return (Object)this == Util.mc.player && original ? !((LivingEntity)(Object)this).getInBlockState().is(Blocks.SCAFFOLDING) : original;
   }

   @WrapOperation(
      method = {"travelFallFlying"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/LivingEntity;updateFallFlyingMovement(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
      )}
   )
   private Vec3 ketamineclub$overrideGlideVelocity(LivingEntity instance, Vec3 oldVelocity, Operation<Vec3> original) {
      if (instance == Util.mc.player && KetamineClub.moduleManager != null) {
         ElytraTestModule module = KetamineClub.moduleManager.getModuleByClass(ElytraTestModule.class);
         if (module != null && module.isEnabled()) {
            Vec3 overriding = module.requestGlideVelocityOverride();
            if (overriding != null) {
               return overriding;
            }
         }
      }

      return (Vec3)original.call(new Object[]{instance, oldVelocity});
   }
}
