package we.ketamine.club.features.modules.player;

import net.minecraft.world.item.Items;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.input.KeyInputEvent;
import we.ketamine.club.event.impl.input.MouseInputEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class MiddleClickExtraModule extends Module {
   private static final int SWAP_PRIORITY = 1000;
   private static final int ROTATION_PRIORITY = 1000;
   private final Setting<Boolean> fireworkInAir = this.bool("FireworkInAir", true);
   private final Setting<Bind> alternatePearlBind = this.key("stupid retarded fucking alternate pearl bind", Bind.none());

   public MiddleClickExtraModule() {
      super("MiddleClick", "Throws a pearl or firework on middle click.", Module.Category.PLAYER);
   }

   @Subscribe
   private void onMouse(MouseInputEvent event) {
      if (!nullCheck() && !PingBypassInput.isScreenOpen()) {
         if (event.getAction() == 1) {
            Bind alternate = this.alternatePearlBind.getValue();
            if (Bind.isMouseButton(alternate.getKey()) && alternate.getKey() - 1000 == event.getButton()) {
               this.throwPearl();
            } else if (event.getButton() == 2) {
               if (this.fireworkInAir.getValue() && mc.player.isFallFlying()) {
                  Result firework = InventoryUtil.find(Items.FIREWORK_ROCKET, InventoryUtil.FULL_SCOPE);
                  if (firework.found()) {
                     KetamineClub.swapManager
                        .submit(new SwapRequest("MiddleClick.firework", 1000, firework, () -> mc.gameMode.useItem(mc.player, firework.hand())));
                  }
               } else {
                  this.throwPearl();
               }
            }
         }
      }
   }

   @Subscribe
   private void onKey(KeyInputEvent event) {
      if (!nullCheck() && !PingBypassInput.isScreenOpen() && event.getAction() == 1) {
         Bind alternate = this.alternatePearlBind.getValue();
         if (!alternate.isEmpty() && !Bind.isMouseButton(alternate.getKey())) {
            if (event.getKey() == alternate.getKey()) {
               this.throwPearl();
            }
         }
      }
   }

   private void throwPearl() {
      Result pearl = InventoryUtil.find(Items.ENDER_PEARL, InventoryUtil.FULL_SCOPE);
      if (pearl.found()) {
         KetamineClub.rotationManager
            .submit(
               new RotationRequest(
                  "MiddleClick.pearl",
                  1000,
                  KetamineClub.rotationManager.getRealYaw(),
                  KetamineClub.rotationManager.getRealPitch(),
                  RotationRequest.Mode.SILENT
               )
            );
         KetamineClub.swapManager.submit(new SwapRequest("MiddleClick.pearl", 1000, pearl, () -> {
            KetamineClub.rotationManager.setBypassUseSpoof(true);

            try {
               mc.gameMode.useItem(mc.player, pearl.hand());
            } finally {
               KetamineClub.rotationManager.setBypassUseSpoof(false);
            }
         }));
      }
   }
}
