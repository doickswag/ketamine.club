package we.ketamine.club.mixin.entity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.movement.FollowerModule;
import we.ketamine.club.util.traits.Util;

@Mixin({Player.class})
public class MixinPlayerTravel {
   public MixinPlayerTravel() {
   }

   @Inject(
      method = {"travel"},
      at = {@At("HEAD")}
   )
   private void ketamineclub$followerTravel(Vec3 movementInput, CallbackInfo ci) {
      if ((Object)this == Util.mc.player) {
         FollowerModule follower = KetamineClub.moduleManager.getModuleByClass(FollowerModule.class);
         if (follower != null) {
            follower.applyTravelVelocity();
         }
      }
   }
}
