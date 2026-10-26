package we.ketamine.club.mixin.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.TotemPopEvent;
import we.ketamine.club.event.impl.network.ChatEvent;
import we.ketamine.club.features.modules.movement.VelocityModule;
import we.ketamine.club.features.modules.player.NoRotateModule;
import we.ketamine.club.util.traits.Util;

@Mixin({ClientPacketListener.class})
public class MixinClientPlayNetworkHandler {
   @Unique
   private Vec3 velocity$preExplosionVel = null;
   @Unique
   private float rotation$prevYaw;
   @Unique
   private float rotation$prevPitch;

   public MixinClientPlayNetworkHandler() {
   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("HEAD")}
   )
   private void onHandleMovePlayerPre(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.isSameThread() && mc.player != null && KetamineClub.rotationManager != null) {
         this.rotation$prevYaw = mc.player.getYRot();
         this.rotation$prevPitch = mc.player.getXRot();
      }
   }

   @Inject(
      method = {"handleMovePlayer"},
      at = {@At("TAIL")}
   )
   private void onHandleMovePlayerPost(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && KetamineClub.rotationManager != null) {
         if (KetamineClub.rotationManager.isSilentActive() || NoRotateModule.isActive()) {
            mc.player.setYRot(this.rotation$prevYaw);
            mc.player.setXRot(this.rotation$prevPitch);
         }
      }
   }

   @Inject(
      method = {"handleSetEntityMotion"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cancelKnockback(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.isSameThread() && mc.player != null) {
         if (packet.id() == mc.player.getId()) {
            VelocityModule velocity = KetamineClub.moduleManager.getModuleByClass(VelocityModule.class);
            if (velocity != null) {
               Vec3 adjusted = velocity.adjustGrimKnockback(packet.movement());
               if (adjusted != null) {
                  mc.player.lerpMotion(adjusted);
                  ci.cancel();
               } else if (velocity.shouldCancelKnockback()) {
                  ci.cancel();
               }
            }
         }
      }
   }

   @Inject(
      method = {"handleExplosion"},
      at = {@At("HEAD")}
   )
   private void saveExplosionVelocity(ClientboundExplodePacket packet, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.isSameThread() && mc.player != null) {
         VelocityModule velocity = KetamineClub.moduleManager.getModuleByClass(VelocityModule.class);
         if (velocity != null) {
            if (velocity.shouldCancelExplosion()) {
               this.velocity$preExplosionVel = mc.player.getDeltaMovement();
            } else {
               packet.playerKnockback().ifPresent(velocity::counterExplosion);
            }
         }
      }
   }

   @Inject(
      method = {"handleExplosion"},
      at = {@At("TAIL")}
   )
   private void restoreExplosionVelocity(ClientboundExplodePacket packet, CallbackInfo ci) {
      if (this.velocity$preExplosionVel != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            mc.player.setDeltaMovement(this.velocity$preExplosionVel);
         }

         this.velocity$preExplosionVel = null;
      }
   }

   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void sendChatMessageHook(String content, CallbackInfo ci) {
      if (Util.EVENT_BUS.post(new ChatEvent(content))) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"handleEntityEvent"},
      at = {@At("HEAD")}
   )
   private void handleEntityEventHook(ClientboundEntityEventPacket packet, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.isSameThread()) {
         if (packet.getEventId() == 35) {
            if (mc.level != null) {
               if (packet.getEntity(mc.level) instanceof LivingEntity living) {
                  Util.EVENT_BUS.post(new TotemPopEvent(living));
               }
            }
         }
      }
   }
}
