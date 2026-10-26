package we.ketamine.club.mixin.network;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.pingbypass.PingBypassModules;

@Mixin({MultiPlayerGameMode.class})
public class MixinPingBypassContainerInput {
   public MixinPingBypassContainerInput() {
   }

   @Inject(
      method = {"handleContainerInput"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void sendIntent(int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
      if (PingBypassModules.isViewer()) {
         ci.cancel();
         Minecraft minecraft = Minecraft.getInstance();
         if (player == minecraft.player && minecraft.getConnection() != null && player.containerMenu.containerId == containerId) {
            if ((slot >= 0 || slot == -999 || slot == -1) && slot < player.containerMenu.slots.size() && slot <= 32767 && button >= -128 && button <= 127) {
               minecraft.getConnection()
                  .send(
                     new ServerboundContainerClickPacket(
                        containerId, player.containerMenu.getStateId(), (short)slot, (byte)button, input, Int2ObjectMaps.emptyMap(), HashedStack.EMPTY
                     )
                  );
            }
         }
      }
   }
}
