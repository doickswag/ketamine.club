package we.ketamine.club.mixin.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.network.AttackBlockEvent;
import we.ketamine.club.util.traits.Util;

@Mixin({MultiPlayerGameMode.class})
public class MixinMultiPlayerGameMode {
   @Unique
   private float ketamineclub$origUseYaw;
   @Unique
   private float ketamineclub$origUsePitch;

   public MixinMultiPlayerGameMode() {
   }

   @Inject(
      method = {"useItem"},
      at = {@At("HEAD")}
   )
   private void ketamineclub$useItemRotateHead(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
      if (player == Minecraft.getInstance().player) {
         if (KetamineClub.rotationManager != null && KetamineClub.rotationManager.isRotating()) {
            if (!KetamineClub.rotationManager.isBypassUseSpoof()) {
               this.ketamineclub$origUseYaw = player.getYRot();
               this.ketamineclub$origUsePitch = player.getXRot();
               player.setYRot(KetamineClub.rotationManager.getRotationYaw());
               player.setXRot(KetamineClub.rotationManager.getRotationPitch());
            }
         }
      }
   }

   @Inject(
      method = {"useItem"},
      at = {@At("RETURN")}
   )
   private void ketamineclub$useItemRotateReturn(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
      if (player == Minecraft.getInstance().player) {
         if (KetamineClub.rotationManager != null && KetamineClub.rotationManager.isRotating()) {
            if (!KetamineClub.rotationManager.isBypassUseSpoof()) {
               player.setYRot(this.ketamineclub$origUseYaw);
               player.setXRot(this.ketamineclub$origUsePitch);
            }
         }
      }
   }

   @Inject(
      method = {"startDestroyBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         AttackBlockEvent event = new AttackBlockEvent(pos, mc.level.getBlockState(pos), direction);
         if (Util.EVENT_BUS.post(event)) {
            cir.setReturnValue(false);
         }
      }
   }
}
