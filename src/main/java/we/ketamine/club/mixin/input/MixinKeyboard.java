package we.ketamine.club.mixin.input;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.event.impl.input.KeyInputEvent;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.traits.Util;

@Mixin({KeyboardHandler.class})
public class MixinKeyboard {
   public MixinKeyboard() {
   }

   @Inject(
      method = {"keyPress"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKey(long window, int action, KeyEvent input, CallbackInfo ci) {
      if (action == 0 || action == 1) {
         PingBypassInput.key(input.key(), action);
         if (action == 1 && Util.EVENT_BUS.post(new KeyInputEvent(input.key(), action))) {
            ci.cancel();
         } else if (action == 0) {
            Util.EVENT_BUS.post(new KeyInputEvent(input.key(), action));
         }
      }
   }
}
