package we.ketamine.club.features.modules.movement;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.util.inventory.ChestSwapUtil;

public class ElytraModule extends Module {
   public ElytraModule() {
      super("Elytra", "Swaps the equipped chestplate and Elytra.", Module.Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      if (nullCheck()) {
         this.disable();
      } else {
         ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
         if (chest.is(Items.ELYTRA)) {
            ChestSwapUtil.equipChestplate();
         } else if (ChestSwapUtil.isChestplate(chest)) {
            ChestSwapUtil.equipElytra();
         }

         this.disable();
      }
   }
}
