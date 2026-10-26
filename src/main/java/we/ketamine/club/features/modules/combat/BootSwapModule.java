package we.ketamine.club.features.modules.combat;

import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.state.BlockState;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.util.EnchantmentUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;

public class BootSwapModule extends Module {
   private static final int BOOTS_MENU_SLOT = 8;
   private static final int OFFHAND_MENU_SLOT = 45;

   public BootSwapModule() {
      super("BootSwap", "Equips Protection boots while phased and Blast Protection boots otherwise.", Module.Category.COMBAT);
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck()) {
         ResourceKey<Enchantment> desired = this.isPhased() ? Enchantments.PROTECTION : Enchantments.BLAST_PROTECTION;
         if (!EnchantmentUtil.has(desired, mc.player.getItemBySlot(EquipmentSlot.FEET))) {
            if (mc.gameMode != null && mc.player.containerMenu == mc.player.inventoryMenu) {
               if (InventoryUtil.cursor().isEmpty()) {
                  Result boots = InventoryUtil.find(
                     (Predicate<ItemStack>)(stack -> isBoots(stack) && EnchantmentUtil.has(desired, stack)), InventoryUtil.FULL_SCOPE
                  );
                  if (boots.found()) {
                     int sourceSlot = containerSlotOf(boots);
                     InventoryUtil.click(sourceSlot, 0, ContainerInput.PICKUP);
                     InventoryUtil.click(8, 0, ContainerInput.PICKUP);
                     InventoryUtil.click(sourceSlot, 0, ContainerInput.PICKUP);
                  }
               }
            }
         }
      }
   }

   private boolean isPhased() {
      BlockState state = mc.level.getBlockState(mc.player.blockPosition());
      return !state.isAir() && !state.canBeReplaced();
   }

   private static boolean isBoots(ItemStack stack) {
      Equippable equippable = (Equippable)stack.get(DataComponents.EQUIPPABLE);
      return equippable != null && equippable.slot() == EquipmentSlot.FEET;
   }

   private static int containerSlotOf(Result result) {
      if (result.type() == ResultType.OFFHAND) {
         return 45;
      } else {
         return result.slot() < 9 ? result.slot() + 36 : result.slot();
      }
   }
}
