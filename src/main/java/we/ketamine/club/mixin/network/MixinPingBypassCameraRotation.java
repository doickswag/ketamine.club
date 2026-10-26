package we.ketamine.club.mixin.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.pingbypass.PingBypass;

@Mixin({ClientPacketListener.class})
public class MixinPingBypassCameraRotation {
   @Unique
   private LocalPlayer pingbypass$savedPlayer;
   @Unique
   private float pingbypass$yaw;
   @Unique
   private float pingbypass$pitch;
   @Unique
   private float pingbypass$oldYaw;
   @Unique
   private float pingbypass$oldPitch;
   @Unique
   private float pingbypass$headYaw;
   @Unique
   private float pingbypass$oldHeadYaw;
   @Unique
   private float pingbypass$bodyYaw;
   @Unique
   private float pingbypass$oldBodyYaw;

   public MixinPingBypassCameraRotation() {
   }

   @Inject(
      method = {"handleMovePlayer", "handleRotatePlayer", "handleLookAt"},
      at = {@At("HEAD")}
   )
   private void pingbypass$saveViewerRotation(CallbackInfo ci) {
      this.pingbypass$savedPlayer = null;
      if (PingBypass.get().isViewer()) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            this.pingbypass$savedPlayer = player;
            this.pingbypass$yaw = player.getYRot();
            this.pingbypass$pitch = player.getXRot();
            this.pingbypass$oldYaw = player.yRotO;
            this.pingbypass$oldPitch = player.xRotO;
            this.pingbypass$headYaw = player.yHeadRot;
            this.pingbypass$oldHeadYaw = player.yHeadRotO;
            this.pingbypass$bodyYaw = player.yBodyRot;
            this.pingbypass$oldBodyYaw = player.yBodyRotO;
         }
      }
   }

   @Inject(
      method = {"handleMovePlayer", "handleRotatePlayer", "handleLookAt"},
      at = {@At("TAIL")}
   )
   private void pingbypass$restoreViewerRotation(CallbackInfo ci) {
      LocalPlayer player = this.pingbypass$savedPlayer;
      this.pingbypass$savedPlayer = null;
      if (player != null && Minecraft.getInstance().player == player) {
         player.setYRot(this.pingbypass$yaw);
         player.setXRot(this.pingbypass$pitch);
         player.yRotO = this.pingbypass$oldYaw;
         player.xRotO = this.pingbypass$oldPitch;
         player.yHeadRot = this.pingbypass$headYaw;
         player.yHeadRotO = this.pingbypass$oldHeadYaw;
         player.yBodyRot = this.pingbypass$bodyYaw;
         player.yBodyRotO = this.pingbypass$oldBodyYaw;
      }
   }
}
