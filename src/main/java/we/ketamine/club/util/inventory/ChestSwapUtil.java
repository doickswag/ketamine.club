package we.ketamine.club.util.inventory;

import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import we.ketamine.club.util.traits.Util;

public final class ChestSwapUtil implements Util {
   private static final int CHEST_MENU_SLOT = 6;
   private static final int OFFHAND_MENU_SLOT = 45;

   private ChestSwapUtil() {
      throw new AssertionError();
   }

   public static ChestSwapUtil.SwapResult equipElytra() {
      return equip(ChestSwapUtil::isUsableElytra);
   }

   public static ChestSwapUtil.SwapResult equipChestplate() {
      return equip(ChestSwapUtil::isChestplate);
   }

   public static void restore(ChestSwapUtil.SwapResult swap) {
      if (swap != null && swap.swapped() && canSwap()) {
         swapChestWith(swap.sourceSlot());
      }
   }

   public static boolean isUsableElytra(ItemStack stack) {
      return stack.is(Items.ELYTRA) && LivingEntity.canGlideUsing(stack, EquipmentSlot.CHEST);
   }

   public static boolean isChestplate(ItemStack stack) {
      if (!stack.isEmpty() && !stack.is(Items.ELYTRA)) {
         Equippable equippable = (Equippable)stack.get(DataComponents.EQUIPPABLE);
         return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
      } else {
         return false;
      }
   }

   private static ChestSwapUtil.SwapResult equip(Predicate<ItemStack> predicate) {
      if (mc.player == null) {
         return ChestSwapUtil.SwapResult.FAILED;
      } else if (predicate.test(mc.player.getItemBySlot(EquipmentSlot.CHEST))) {
         return new ChestSwapUtil.SwapResult(true, false, -1);
      } else if (!canSwap()) {
         return ChestSwapUtil.SwapResult.FAILED;
      } else {
         Result item = InventoryUtil.find(predicate, InventoryUtil.FULL_SCOPE);
         if (!item.found()) {
            return ChestSwapUtil.SwapResult.FAILED;
         } else {
            int sourceSlot = containerSlotOf(item);
            swapChestWith(sourceSlot);
            return new ChestSwapUtil.SwapResult(predicate.test(mc.player.getItemBySlot(EquipmentSlot.CHEST)), true, sourceSlot);
         }
      }
   }

   private static boolean canSwap() {
      return mc.player != null && mc.gameMode != null && mc.player.containerMenu == mc.player.inventoryMenu && InventoryUtil.cursor().isEmpty();
   }

   private static void swapChestWith(int sourceSlot) {
      InventoryUtil.click(sourceSlot, 0, ContainerInput.PICKUP);
      InventoryUtil.click(6, 0, ContainerInput.PICKUP);
      InventoryUtil.click(sourceSlot, 0, ContainerInput.PICKUP);
   }

   private static int containerSlotOf(Result result) {
      if (result.type() == ResultType.OFFHAND) {
         return 45;
      } else {
         return result.slot() < 9 ? result.slot() + 36 : result.slot();
      }
   }

   public record SwapResult(boolean ready, boolean swapped, int sourceSlot) {
      private static final ChestSwapUtil.SwapResult FAILED = new ChestSwapUtil.SwapResult(false, false, -1);
   }
}
