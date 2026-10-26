package we.ketamine.club.features.modules.render;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class CrystalHandModule extends Module {
   public final Setting<Boolean> require = this.bool("Require", true);

   public CrystalHandModule() {
      super("CrystalHand", "Renders your sword as an end crystal if you have crystals in your inventory", Module.Category.RENDER);
   }

   public boolean hasCrystalsInInventory() {
      if (nullCheck()) {
         return false;
      } else {
         for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.END_CRYSTAL)) {
               return true;
            }
         }

         return false;
      }
   }

   public ItemStack getDisplayStack(ItemStack original) {
      return !original.is(ItemTags.SWORDS) || this.require.getValue() && !this.hasCrystalsInInventory() ? original : new ItemStack(Items.END_CRYSTAL);
   }
}
