package we.ketamine.club.features.modules.hud;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.features.modules.client.HudModule;

public class TotemsHudModule extends HudModule {
   private static final int ICON = 16;
   private static final int XP_BAR_TOP_OFFSET = 32;
   private static final int GAP_ABOVE_XP = 6;

   public TotemsHudModule() {
      super("Totems");
   }

   @Override
   public void render(Render2DEvent event) {
      int count = this.countTotems();
      int x = this.screenWidth() / 2 - 8;
      int y = this.screenHeight() - 32 - 6 - 16;
      ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING, Math.max(count, 1));
      event.getContext().item(stack, x, y);
      if (count > 0) {
         event.getContext().itemDecorations(mc.font, stack, x, y, String.valueOf(count));
      } else {
         String label = "0";
         int tx = x + 19 - 2 - mc.font.width(label);
         int ty = y + 6 + 3;
         event.getContext().text(mc.font, label, tx, ty, -43691);
      }
   }

   private int countTotems() {
      int total = 0;
      Inventory inv = mc.player.getInventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (s.is(Items.TOTEM_OF_UNDYING)) {
            total += s.getCount();
         }
      }

      return total;
   }
}
