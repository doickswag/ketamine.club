package we.ketamine.club.features.modules.player;

import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.combat.OffhandModule;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapRequest;
import we.ketamine.club.mixin.client.ClientLevelAccessor;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.EnchantmentUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;

public class AutoXPModule extends Module {
   private final Setting<Integer> minDurability = this.num("MinDurability", 40, 1, 100);
   private final Setting<Integer> stopDurability = this.num("StopDurability", 70, 0, 100);
   private final Setting<Bind> throwBind = this.key("Throw", Bind.none());
   private static final int THROWS_PER_BATCH = 9;
   private static final long BATCH_INTERVAL_MS = 300L;
   private boolean throwing;
   private long lastThrowMs;

   public AutoXPModule() {
      super("AutoXP", "Throws XP bottles when your head is phased in a block and armor durability is low.", Module.Category.PLAYER);
   }

   @Override
   public void onDisable() {
      this.throwing = false;
      this.lastThrowMs = 0L;
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck() && !PingBypassInput.isScreenOpen()) {
         OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
         if (offhand == null || !offhand.shouldDeferForEat()) {
            boolean manual = !this.throwBind.getValue().isEmpty() && this.throwBind.getValue().isDown();
            if (this.anyArmorAtOrBelow(this.minDurability.getValue())) {
               this.throwing = true;
            } else if (this.throwing && this.allArmorAbove(this.stopDurability.getValue())) {
               this.throwing = false;
            }

            if (manual || this.throwing) {
               if (this.shouldThrowNow()) {
                  Result xpBottle = InventoryUtil.find(Items.EXPERIENCE_BOTTLE, InventoryUtil.FULL_SCOPE);
                  if (xpBottle.found()) {
                     int amount = 9;
                     float yaw = mc.player.getYRot();
                     float pitch = 90.0F;
                     KetamineClub.rotationManager.submit(new RotationRequest("AutoXP", 40, yaw, pitch, RotationRequest.Mode.SILENT));
                     mc.gameMode.ensureHasSentCarriedItem();
                     KetamineClub.swapManager
                        .submit(
                           new SwapRequest(
                              "AutoXP",
                              40,
                              xpBottle,
                              r -> {
                                 for (int i = 0; i < amount; i++) {
                                    BlockStatePredictionHandler handler = ((ClientLevelAccessor)mc.level)
                                       .ketamineclub$getBlockStatePredictionHandler()
                                       .startPredicting();

                                    try {
                                       mc.getConnection().send(new ServerboundUseItemPacket(r.hand(), handler.currentSequence(), yaw, pitch));
                                    } catch (Throwable var9) {
                                       if (handler != null) {
                                          try {
                                             handler.close();
                                          } catch (Throwable var8) {
                                             var9.addSuppressed(var8);
                                          }
                                       }

                                       throw var9;
                                    }

                                    if (handler != null) {
                                       handler.close();
                                    }
                                 }
                              }
                           )
                        );
                  }
               }
            }
         }
      }
   }

   private boolean shouldThrowNow() {
      long now = System.currentTimeMillis();
      if (now - this.lastThrowMs < 300L) {
         return false;
      } else {
         this.lastThrowMs = now;
         return true;
      }
   }

   private boolean anyArmorAtOrBelow(int pct) {
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack armor = mc.player.getItemBySlot(slot);
         if (!armor.isEmpty() && armor.getMaxDamage() > 0 && EnchantmentUtil.has(Enchantments.MENDING, armor)) {
            float durabilityPct = (float)(armor.getMaxDamage() - armor.getDamageValue()) / armor.getMaxDamage() * 100.0F;
            if (durabilityPct <= pct) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean allArmorAbove(int pct) {
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack armor = mc.player.getItemBySlot(slot);
         if (!armor.isEmpty() && armor.getMaxDamage() > 0 && EnchantmentUtil.has(Enchantments.MENDING, armor)) {
            float durabilityPct = (float)(armor.getMaxDamage() - armor.getDamageValue()) / armor.getMaxDamage() * 100.0F;
            if (durabilityPct <= pct) {
               return false;
            }
         }
      }

      return true;
   }
}
