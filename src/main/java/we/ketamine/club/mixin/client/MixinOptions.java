package we.ketamine.club.mixin.client;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin({Options.class})
public class MixinOptions {
   public MixinOptions() {
   }

   @ModifyConstant(
      method = {"<init>"},
      constant = {@Constant(
         intValue = 110
      )}
   )
   private int ketamineclub$raiseFovCap(int originalMax) {
      return 160;
   }
}
