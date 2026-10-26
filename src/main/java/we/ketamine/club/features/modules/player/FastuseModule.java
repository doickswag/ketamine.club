package we.ketamine.club.features.modules.player;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class FastuseModule extends Module {
   public final Setting<Boolean> all = this.bool("All", false);
   public final Setting<Boolean> experience = this.bool("Experience", true);
   public final Setting<Boolean> blocks = this.bool("Blocks", false);
   public final Setting<Boolean> enderChests = this.bool("EnderChests", true);

   public FastuseModule() {
      super("Fastuse", "Use items and interact with blocks faster", Module.Category.PLAYER);
   }

   @Override
   public void onTick() {
      if (!nullCheck()) {
         if (this.matches(mc.player.getMainHandItem()) || this.matches(mc.player.getOffhandItem())) {
            mc.rightClickDelay = 0;
         }
      }
   }

   private boolean matches(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else if (this.all.getValue()) {
         return true;
      } else if (this.experience.getValue() && stack.is(Items.EXPERIENCE_BOTTLE)) {
         return true;
      } else {
         return this.enderChests.getValue() && stack.is(Items.ENDER_CHEST) ? true : this.blocks.getValue() && stack.getItem() instanceof BlockItem;
      }
   }
}
