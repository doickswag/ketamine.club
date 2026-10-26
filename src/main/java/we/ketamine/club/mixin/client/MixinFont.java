package we.ketamine.club.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.util.render.font.Fonts;

@Mixin({Font.class})
public class MixinFont {
   public MixinFont() {
   }

   @Inject(
      method = {"width(Ljava/lang/String;)I"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$widthString(String text, CallbackInfoReturnable<Integer> cir) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            cir.setReturnValue(Fonts.overrideWidth(Fonts.applyHudTextCase(text)));
         } catch (Exception var4) {
         }
      }
   }

   @Inject(
      method = {"width(Lnet/minecraft/util/FormattedCharSequence;)I"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$widthFormatted(FormattedCharSequence text, CallbackInfoReturnable<Integer> cir) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            cir.setReturnValue(Fonts.overrideWidth(Fonts.applyHudTextCase(text)));
         } catch (Exception var4) {
         }
      }
   }

   @Inject(
      method = {"width(Lnet/minecraft/network/chat/FormattedText;)I"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$widthFormattedText(FormattedText text, CallbackInfoReturnable<Integer> cir) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            cir.setReturnValue(Fonts.overrideWidth(Fonts.applyHudTextCase(Language.getInstance().getVisualOrder(text))));
         } catch (Exception var4) {
         }
      }
   }

   @ModifyVariable(
      method = {"width(Ljava/lang/String;)I"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private String ketamineclub$lowercaseString(String text) {
      return Fonts.applyHudTextCase(text);
   }

   @ModifyVariable(
      method = {"width(Lnet/minecraft/util/FormattedCharSequence;)I"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private FormattedCharSequence ketamineclub$lowercaseFormatted(FormattedCharSequence text) {
      return Fonts.applyHudTextCase(text);
   }
}
