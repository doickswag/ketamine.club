package we.ketamine.club.features.modules.render;

import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public class ShulkerPreviewModule extends Module {
   private static final int COLUMNS = 9;
   private static final int ROWS = 3;
   private static final int SLOTS = 27;
   private static final int CELL = 18;
   private static final int PADDING = 3;
   private static final int BORDER = 1;
   private static final int CURSOR_GAP = 12;
   private static final int SCREEN_MARGIN = 2;
   public final Setting<Color> background = this.color("Background", 16, 16, 20, 240);
   public final Setting<Color> border = this.color("Border", 130, 80, 255, 255);
   public final Setting<Boolean> hideEmpty = this.bool("HideEmpty", false);

   public ShulkerPreviewModule() {
      super("ShulkerPreview", "Shows a contents icon and inventory preview for shulker boxes", Module.Category.RENDER);
   }

   public static ShulkerPreviewModule get() {
      return KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(ShulkerPreviewModule.class);
   }

   public ItemStack getDisplayStack(ItemStack stack) {
      if (!isShulkerBox(stack)) {
         return ItemStack.EMPTY;
      } else {
         ItemContainerContents contents = (ItemContainerContents)stack.get(DataComponents.CONTAINER);
         return contents == null ? ItemStack.EMPTY : contents.nonEmptyItemCopyStream().findFirst().orElse(ItemStack.EMPTY);
      }
   }

   public boolean renderPreview(GuiGraphicsExtractor graphics, ItemStack stack, int mouseX, int mouseY) {
      if (!isShulkerBox(stack)) {
         return false;
      } else {
         ItemContainerContents contents = (ItemContainerContents)stack.get(DataComponents.CONTAINER);
         if (contents == null) {
            return false;
         } else {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            contents.copyInto(items);
            if (this.hideEmpty.getValue() && items.stream().allMatch(ItemStack::isEmpty)) {
               return false;
            } else {
               int width = 168;
               int height = 60;
               int x = clamp(mouseX + 12, 2, graphics.guiWidth() - width - 2);
               int y = clamp(mouseY - 12, 2, graphics.guiHeight() - height - 2);
               graphics.nextStratum();
               graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, this.border.getValue().getRGB());
               graphics.fill(x, y, x + width, y + height, this.background.getValue().getRGB());

               for (int i = 0; i < 27; i++) {
                  ItemStack item = (ItemStack)items.get(i);
                  if (!item.isEmpty()) {
                     int ix = x + 3 + i % 9 * 18 + 1;
                     int iy = y + 3 + i / 9 * 18 + 1;
                     graphics.item(item, ix, iy);
                     graphics.itemDecorations(mc.font, item, ix, iy);
                  }
               }

               return true;
            }
         }
      }
   }

   public static boolean isShulkerBox(ItemStack stack) {
      return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
   }

   private static int clamp(int value, int min, int max) {
      return max < min ? min : Math.max(min, Math.min(max, value));
   }
}
