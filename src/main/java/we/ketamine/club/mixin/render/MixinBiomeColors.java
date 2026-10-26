package we.ketamine.club.mixin.render;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.features.modules.slop.ColorChangerForStuffModule;

@Mixin({BiomeColors.class})
public class MixinBiomeColors {
   public MixinBiomeColors() {
   }

   @Inject(
      method = {"getAverageWaterColor"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void ketamineclub$waterColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
      ColorChangerForStuffModule module = ColorChangerForStuffModule.active();
      if (module != null) {
         cir.setReturnValue(module.waterColor((Integer)cir.getReturnValue()));
      }
   }

   @Inject(
      method = {"getAverageGrassColor"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void ketamineclub$grassColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
      ColorChangerForStuffModule module = ColorChangerForStuffModule.active();
      if (module != null) {
         cir.setReturnValue(module.grassColor((Integer)cir.getReturnValue()));
      }
   }

   @Inject(
      method = {"getAverageFoliageColor"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void ketamineclub$foliageColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
      ColorChangerForStuffModule module = ColorChangerForStuffModule.active();
      if (module != null) {
         cir.setReturnValue(module.foliageColor((Integer)cir.getReturnValue()));
      }
   }
}
