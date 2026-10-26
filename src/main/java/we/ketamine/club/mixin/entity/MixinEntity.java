package we.ketamine.club.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.movement.NoSlowModule;
import we.ketamine.club.features.modules.movement.VelocityModule;
import we.ketamine.club.features.modules.render.ShadersModule;
import we.ketamine.club.manager.RotationManager;

@Mixin({Entity.class})
public class MixinEntity {
   @Shadow
   protected Vec3 stuckSpeedMultiplier;

   public MixinEntity() {
   }

   @Shadow
   protected static Vec3 getInputVector(Vec3 movementInput, float speed, float yaw) {
      throw new AssertionError();
   }

   @Inject(
      method = {"makeStuckInBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$webNoSlow(BlockState state, Vec3 multiplier, CallbackInfo ci) {
      Entity self = (Entity)(Object)this;
      if (self == Minecraft.getInstance().player && NoSlowModule.shouldCancelWebSlow(state)) {
         self.resetFallDistance();
         this.stuckSpeedMultiplier = Vec3.ZERO;
         ci.cancel();
      }
   }

   @Inject(
      method = {"moveRelative"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$moveFixVelocity(float speed, Vec3 movementInput, CallbackInfo ci) {
      Entity self = (Entity)(Object)this;
      if (self == Minecraft.getInstance().player) {
         RotationManager rm = KetamineClub.rotationManager;
         if (rm != null && rm.isMoveFixEnabled() && rm.hasMovementRotation()) {
            self.setDeltaMovement(self.getDeltaMovement().add(getInputVector(movementInput, speed, rm.getMovementYaw())));
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"getLookAngle"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$spoofLookAngle(CallbackInfoReturnable<Vec3> cir) {
      Entity self = (Entity)(Object)this;
      if (self == Minecraft.getInstance().player) {
         RotationManager rm = KetamineClub.rotationManager;
         if (rm != null && rm.isMoveFixEnabled() && rm.hasMovementRotation()) {
            cir.setReturnValue(self.calculateViewVector(rm.getMovementPitch(), rm.getMovementYaw()));
         }
      }
   }

   @Inject(
      method = {"isCurrentlyGlowing"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
      Entity self = (Entity)(Object)this;
      ShadersModule shaders = KetamineClub.moduleManager.getModuleByClass(ShadersModule.class);
      if (shaders != null && shaders.isEnabled() && shaders.shouldShader(self)) {
         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"getTeamColor"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
      Entity self = (Entity)(Object)this;
      ShadersModule shaders = KetamineClub.moduleManager.getModuleByClass(ShadersModule.class);
      if (shaders != null && shaders.isEnabled() && shaders.shouldShader(self)) {
         cir.setReturnValue(shaders.getRgbFor(self));
      }
   }

   @WrapOperation(
      method = {"push(Lnet/minecraft/world/entity/Entity;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"
      )}
   )
   private void cancelEntityPush(Entity pushed, double x, double y, double z, Operation<Void> original) {
      LocalPlayer localPlayer = Minecraft.getInstance().player;
      VelocityModule velocity = KetamineClub.moduleManager.getModuleByClass(VelocityModule.class);
      if (localPlayer == null || pushed != localPlayer || velocity == null || !velocity.shouldCancelEntityPush()) {
         original.call(new Object[]{pushed, x, y, z});
      }
   }
}
