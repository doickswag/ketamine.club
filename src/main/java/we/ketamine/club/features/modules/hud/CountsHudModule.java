package we.ketamine.club.features.modules.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.features.modules.client.HudModule;

public class CountsHudModule extends HudModule {
   private static final int ICON = 16;
   private static final float ICON_SCALE = 0.85F;
   private static final float RENDER_ICON = 13.6F;
   private static final int HOTBAR_HALF = 91;
   private static final int HUNGER_TOP_OFFSET = 39;
   private static final int BUBBLE_HEIGHT = 10;
   private static final int GAP = 2;
   private static final int TEXT_PAD = 2;
   private static final Item[] ITEMS = new Item[]{Items.END_CRYSTAL, Items.OBSIDIAN, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE};
   private static ItemStack[] iconStacks;
   private static final int GLYPH_HEIGHT = 8;

   public CountsHudModule() {
      super("Counts");
   }

   @Override
   public void render(Render2DEvent event) {
      GuiGraphicsExtractor ctx = event.getContext();
      int x = this.screenWidth() / 2 + 91 + 2;
      int yTop = this.screenHeight() - 39 - 1 - 16;
      if (this.showingBubbles()) {
         yTop -= 10;
      }

      int[] counts = this.countItems();

      for (int i = 0; i < ITEMS.length; i++) {
         int y = yTop + Math.round(i * 13.6F);
         int count = counts[i];
         this.drawIcon(ctx, iconStack(i), x, y);
         this.drawCount(ctx, count, x, y);
      }
   }

   private void drawIcon(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y) {
      ctx.pose().pushMatrix();
      ctx.pose().scale(0.85F, 0.85F);
      ctx.item(stack, Math.round(x / 0.85F), Math.round(y / 0.85F));
      ctx.pose().popMatrix();
   }

   private static ItemStack iconStack(int index) {
      if (iconStacks == null) {
         iconStacks = new ItemStack[ITEMS.length];

         for (int i = 0; i < ITEMS.length; i++) {
            iconStacks[i] = new ItemStack(ITEMS[i]);
         }
      }

      return iconStacks[index];
   }

   private void drawCount(GuiGraphicsExtractor ctx, int count, int x, int y) {
      String label = String.valueOf(count);
      int drawX = Math.round(x + 13.6F + 2.0F);
      int drawY = Math.round(y + 2.8000002F);
      ctx.text(mc.font, label, drawX, drawY, count > 0 ? -1 : -43691, true);
   }

   private int[] countItems() {
      int[] counts = new int[ITEMS.length];
      Inventory inv = mc.player.getInventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);

         for (int itemIndex = 0; itemIndex < ITEMS.length; itemIndex++) {
            if (stack.is(ITEMS[itemIndex])) {
               counts[itemIndex] += stack.getCount();
               break;
            }
         }
      }

      return counts;
   }

   private boolean showingBubbles() {
      return mc.player.isEyeInFluid(FluidTags.WATER) || mc.player.getAirSupply() < mc.player.getMaxAirSupply();
   }
}
