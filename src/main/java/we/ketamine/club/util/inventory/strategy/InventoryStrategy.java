package we.ketamine.club.util.inventory.strategy;

import net.minecraft.world.inventory.ContainerInput;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;

public final class InventoryStrategy implements SwapStrategy {
   public static final InventoryStrategy INSTANCE = new InventoryStrategy();

   private InventoryStrategy() {
   }

   @Override
   public boolean swap(Result result) {
      if (result.type() != ResultType.INVENTORY && result.type() != ResultType.HOTBAR) {
         return false;
      } else {
         int slot = inventorySlot(result);
         InventoryUtil.click(slot, InventoryUtil.selected(), ContainerInput.SWAP);
         return true;
      }
   }

   @Override
   public boolean swapBack(int last, Result result) {
      return this.swap(result);
   }

   private static int inventorySlot(Result result) {
      return result.type() == ResultType.HOTBAR ? result.slot() + 36 : result.slot();
   }
}
