package we.ketamine.club.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.features.modules.render.ShulkerPreviewModule;

@Mixin({AbstractContainerScreen.class})
public class MixinAbstractContainerScreen<T extends AbstractContainerMenu> {
   @Shadow
   @Final
   protected T menu;
   @Shadow
   protected Slot hoveredSlot;

   public MixinAbstractContainerScreen() {
   }

   @Inject(
      method = {"extractTooltip"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderShulkerPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
      ShulkerPreviewModule module = ShulkerPreviewModule.get();
      if (module != null && module.isEnabled()) {
         if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            if (this.menu.getCarried().isEmpty()) {
               if (module.renderPreview(graphics, this.hoveredSlot.getItem(), mouseX, mouseY)) {
                  ci.cancel();
               }
            }
         }
      }
   }

   @Inject(
      method = {"mouseDragged"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || !mc.player.getAbilities().instabuild) {
         ItemStack cursorStack = this.menu.getCarried();
         if (cursorStack != null && !cursorStack.isEmpty()) {
            if (!cursorStack.isStackable() || cursorStack.getItem() instanceof MapItem || cursorStack.getItem() instanceof BannerItem) {
               cir.setReturnValue(true);
            }
         }
      }
   }
}
