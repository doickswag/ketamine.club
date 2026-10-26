package we.ketamine.club.features.modules.combat;

import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.Items;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.mixin.client.ClientLevelAccessor;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class KeyPotionModule extends Module {
   private final Setting<Boolean> onGround = this.bool("OnGround", false);

   public KeyPotionModule() {
      super("KeyPotion", "Throws a splash potion on keybind press.", Module.Category.PLAYER);
   }

   @Override
   public void onEnable() {
      if (mc.player == null || mc.level == null || mc.gameMode == null) {
         this.disable();
      } else if (this.onGround.getValue() && !mc.player.onGround()) {
         this.disable();
      } else {
         Result potion = InventoryUtil.find(Items.SPLASH_POTION, InventoryUtil.FULL_SCOPE);
         if (potion.found()) {
            float yaw = mc.player.getYRot();
            float pitch = 90.0F;
            KetamineClub.rotationManager.submit(new RotationRequest("KeyPotion", 20, yaw, pitch, RotationRequest.Mode.SILENT));
            mc.gameMode.ensureHasSentCarriedItem();
            KetamineClub.swapManager.submit(new SwapRequest("KeyPotion", 40, potion, r -> {
               BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level).ketamineclub$getBlockStatePredictionHandler().startPredicting();

               try {
                  mc.getConnection().send(new ServerboundUseItemPacket(r.hand(), handler.currentSequence(), yaw, pitch));
               } catch (Throwable var7) {
                  if (handler != null) {
                     try {
                        handler.close();
                     } catch (Throwable var6) {
                        var7.addSuppressed(var6);
                     }
                  }

                  throw var7;
               }

               if (handler != null) {
                  handler.close();
               }
            }));
         }

         this.disable();
      }
   }
}
