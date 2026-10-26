package we.ketamine.club.mixin.render.gui;

import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({Hud.class})
public abstract class MixinHeldItemTooltip {
   private static final int KETAMINECLUB$Y_OFFSET = 9;

   public MixinHeldItemTooltip() {
   }

   @ModifyArg(
      method = {"extractSelectedItemName"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"
      ),
      index = 3
   )
   private int ketamineclub$shiftHeldItemName(int y) {
      return y - 9;
   }
}
