package we.ketamine.club.features.modules.player;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.funny.XCarryModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.inventory.InventoryUtil;

public class ReplenishModule extends Module {
   private final Setting<Integer> percentage = this.num("Percentage", 20, 10, 50);
   private final Setting<Boolean> alternative = this.bool("Alternative", false);
   private final Setting<Boolean> xcarry = this.bool("XCarry", true);
   private final Map<Integer, Integer> hotbarTicks = new HashMap<>();
   private final Map<Integer, Item> lastHotbarItems = new HashMap<>();

   public ReplenishModule() {
      super("Replenish", "Automatically refills items in the hotbar.", Module.Category.PLAYER);
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         if (!PingBypassInput.isScreenOpen()) {
            if (mc.player.containerMenu == mc.player.inventoryMenu) {
               InstantRekitModule rekit = KetamineClub.moduleManager.getModuleByClass(InstantRekitModule.class);
               if (rekit == null || !rekit.isRestoringXCarry()) {
                  if (InventoryUtil.cursor().isEmpty()) {
                     for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
                        ItemStack stack = mc.player.getInventory().getItem(hotbarSlot);
                        Item currentItem = stack.isEmpty() ? null : stack.getItem();
                        if (this.lastHotbarItems.getOrDefault(hotbarSlot, null) != currentItem) {
                           this.hotbarTicks.put(hotbarSlot, 0);
                           this.lastHotbarItems.put(hotbarSlot, currentItem);
                        } else {
                           this.hotbarTicks.put(hotbarSlot, this.hotbarTicks.getOrDefault(hotbarSlot, 0) + 1);
                        }

                        if (this.hotbarTicks.getOrDefault(hotbarSlot, 0) >= 10 && !stack.isEmpty()) {
                           int maxCount = stack.getMaxStackSize();
                           int minCount = Math.max(1, (int)(maxCount * (this.percentage.getValue().intValue() / 100.0F)));
                           if (stack.getCount() < minCount) {
                              int sourceSlot = this.findSourceSlot(stack);
                              if (sourceSlot != -1) {
                                 this.refill(hotbarSlot, sourceSlot);
                                 return;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private int findSourceSlot(ItemStack target) {
      for (int i = 9; i < 36; i++) {
         ItemStack stack = mc.player.getInventory().getItem(i);
         if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, target)) {
            return i;
         }
      }

      if (this.xcarry.getValue() && XCarryModule.isActive()) {
         for (int slot = 1; slot < 5; slot++) {
            ItemStack stack = mc.player.inventoryMenu.getSlot(slot).getItem();
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, target)) {
               return slot;
            }
         }
      }

      return -1;
   }

   private void refill(int hotbarSlot, int sourceSlot) {
      int targetSlot = 36 + hotbarSlot;
      if (this.alternative.getValue() && sourceSlot >= 9) {
         InventoryUtil.click(sourceSlot, 0, ContainerInput.QUICK_MOVE);
      } else {
         InventoryUtil.click(sourceSlot, 0, ContainerInput.PICKUP);
         InventoryUtil.click(targetSlot, 0, ContainerInput.PICKUP);
         InventoryUtil.click(sourceSlot, 0, ContainerInput.PICKUP);
      }
   }
}
