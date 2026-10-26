package we.ketamine.club.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.Stage;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.entity.player.UpdateWalkingPlayerEvent;
import we.ketamine.club.features.modules.movement.NoSlowModule;
import we.ketamine.club.features.modules.movement.SprintModule;
import we.ketamine.club.features.modules.movement.VelocityModule;
import we.ketamine.club.features.modules.player.FreecamModule;
import we.ketamine.club.features.modules.render.NoRenderModule;
import we.ketamine.club.features.modules.world.ScaffoldModule;
import we.ketamine.club.manager.RotationManager;
import we.ketamine.club.pingbypass.PingBypass;
import we.ketamine.club.pingbypass.PingBypassModules;
import we.ketamine.club.util.traits.Util;

@Mixin({LocalPlayer.class})
public class MixinClientPlayerEntity {
   public MixinClientPlayerEntity() {
   }

   @Inject(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/ClientInput;tick()V",
         shift = Shift.AFTER
      )}
   )
   private void ketamineclub$freecamSuppressPlayerMovement(CallbackInfo ci) {
      FreecamModule freecam = KetamineClub.moduleManager.getModuleByClass(FreecamModule.class);
      if (freecam != null && freecam.isEnabled()) {
         LocalPlayer self = (LocalPlayer)(Object)this;
         self.input.keyPresses = new Input(false, false, false, false, false, false, false);
         self.input.moveVector = Vec2.ZERO;
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void preTickHook(CallbackInfo ci) {
      PingBypass.get().beforePlayerTick();
      Util.EVENT_BUS.post(new PreTickEvent());
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void tickHook(CallbackInfo ci) {
      Util.EVENT_BUS.post(new TickEvent());
      PingBypass.get().afterPlayerTick();
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V",
         shift = Shift.AFTER
      )}
   )
   private void tickHook2(CallbackInfo ci) {
      PingBypass.get().afterMovementTick();
      Util.EVENT_BUS.post(new UpdateWalkingPlayerEvent(Stage.PRE));
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;sendPosition()V",
         shift = Shift.AFTER
      )}
   )
   private void tickHook3(CallbackInfo ci) {
      Util.EVENT_BUS.post(new UpdateWalkingPlayerEvent(Stage.POST));
   }

   @Inject(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/ClientInput;tick()V",
         shift = Shift.AFTER
      )}
   )
   private void scaffold$suppressSneakAfterInputTick(CallbackInfo ci) {
      ScaffoldModule scaffold = KetamineClub.moduleManager.getModuleByClass(ScaffoldModule.class);
      if (scaffold != null && scaffold.isEnabled()) {
         LocalPlayer self = (LocalPlayer)(Object)this;
         Input kp = self.input.keyPresses;
         if (kp.shift()) {
            self.input.keyPresses = new Input(kp.forward(), kp.backward(), kp.left(), kp.right(), kp.jump(), false, kp.sprint());
         }
      }
   }

   @Inject(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/ClientInput;tick()V",
         shift = Shift.AFTER
      )}
   )
   private void sprint$applyBeforeJump(CallbackInfo ci) {
      SprintModule sprint = KetamineClub.moduleManager.getModuleByClass(SprintModule.class);
      if (sprint != null && sprint.wantsSprint()) {
         ((LocalPlayer)(Object)this).setSprinting(true);
      }
   }

   @Inject(
      method = {"aiStep"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/ClientInput;tick()V",
         shift = Shift.AFTER
      )}
   )
   private void ketamineclub$moveFixAfterInputTick(CallbackInfo ci) {
      RotationManager rotation = KetamineClub.rotationManager;
      LocalPlayer self = (LocalPlayer)(Object)this;
      if (rotation != null && rotation.hasMovementRotation() && rotation.isMoveFixEnabled()) {
         Input real = self.input.keyPresses;
         Input fixed = rotation.computeMoveFixInput(real, self.getYRot());
         if (fixed != real) {
            self.input.keyPresses = fixed;
            float sideways = fixed.left() == fixed.right() ? 0.0F : (fixed.left() ? 1.0F : -1.0F);
            float forward = fixed.forward() == fixed.backward() ? 0.0F : (fixed.forward() ? 1.0F : -1.0F);
            float magnitude = self.input.moveVector.length();
            self.input.moveVector = new Vec2(sideways, forward).normalized().scale(magnitude);
         }
      }

      VelocityModule velocity = KetamineClub.moduleManager.getModuleByClass(VelocityModule.class);
      if (velocity != null) {
         velocity.applyGrimCounterInput(self);
      }
   }

   @Inject(
      method = {"moveTowardsClosestSpace"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cancelBlockPush(double x, double z, CallbackInfo ci) {
      VelocityModule velocity = KetamineClub.moduleManager.getModuleByClass(VelocityModule.class);
      if (PingBypassModules.isViewer() || velocity != null && velocity.isEnabled() && velocity.blockPush.getValue() && velocity.phaseConditionMet()) {
         ci.cancel();
      }
   }

   @ModifyExpressionValue(
      method = {"modifyInput"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/player/LocalPlayer;itemUseSpeedMultiplier()F"
      )}
   )
   private float noSlow$eatSpeed(float original) {
      return NoSlowModule.shouldCancelConsumeSlow() ? 1.0F : original;
   }

   @Inject(
      method = {"isMovingSlowly"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void fastCrawl(CallbackInfoReturnable<Boolean> cir) {
      LocalPlayer self = (LocalPlayer)(Object)this;
      if (self.isVisuallyCrawling() && NoSlowModule.isActive(m -> m.crawl.getValue())) {
         cir.setReturnValue(false);
      }
   }

   @Redirect(
      method = {"handlePortalTransitionEffect"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/Screen;isAllowedInPortal()Z"
      )
   )
   private boolean ketamineclub$portalGui(Screen screen) {
      return NoRenderModule.isActive(m -> m.portalGui.getValue()) ? true : screen.isAllowedInPortal();
   }
}
