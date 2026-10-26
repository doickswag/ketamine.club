package we.ketamine.club.features.modules.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.features.modules.client.HudModule;
import we.ketamine.club.util.EnchantmentUtil;

public class ArmorHudModule extends HudModule {
   private static final int ICON = 16;
   private static final int STRIDE = 18;
   private static final int HOTBAR_HALF = 91;
   private static final int HUNGER_TOP_OFFSET = 39;
   private static final int BUBBLE_HEIGHT = 10;
   private static final float TAG_SCALE = 0.8F;
   private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final ArmorHudModule.ProtTag[] PROT_TAGS = new ArmorHudModule.ProtTag[]{
      new ArmorHudModule.ProtTag(Enchantments.PROTECTION, "P"),
      new ArmorHudModule.ProtTag(Enchantments.BLAST_PROTECTION, "BP"),
      new ArmorHudModule.ProtTag(Enchantments.FIRE_PROTECTION, "FP"),
      new ArmorHudModule.ProtTag(Enchantments.PROJECTILE_PROTECTION, "PP")
   };

   public ArmorHudModule() {
      super("Armor");
   }

   @Override
   public void render(Render2DEvent event) {
      int totalWidth = SLOTS.length * 18 - 2;
      int rightX = this.screenWidth() / 2 + 91;
      int x = rightX - totalWidth;
      int y = this.screenHeight() - 39 - 1 - 16;
      if (this.showingBubbles()) {
         y -= 10;
      }

      GuiGraphicsExtractor ctx = event.getContext();

      for (int i = 0; i < SLOTS.length; i++) {
         int sx = x + i * 18;
         ItemStack stack = mc.player.getItemBySlot(SLOTS[i]);
         if (!stack.isEmpty()) {
            ctx.item(stack, sx, y);
            ctx.itemDecorations(mc.font, stack, sx, y);
            String tag = this.protectionTag(stack);
            if (tag != null) {
               ctx.pose().pushMatrix();
               ctx.pose().scale(0.8F, 0.8F);
               ctx.text(mc.font, tag, Math.round(sx / 0.8F), Math.round(y / 0.8F), -1, true);
               ctx.pose().popMatrix();
            }
         }
      }
   }

   private String protectionTag(ItemStack stack) {
      for (ArmorHudModule.ProtTag t : PROT_TAGS) {
         if (EnchantmentUtil.has(t.key(), stack)) {
            return t.label();
         }
      }

      return null;
   }

   private boolean showingBubbles() {
      return mc.player.isEyeInFluid(FluidTags.WATER) || mc.player.getAirSupply() < mc.player.getMaxAirSupply();
   }

   private record ProtTag(ResourceKey<Enchantment> key, String label) {
   }
}
