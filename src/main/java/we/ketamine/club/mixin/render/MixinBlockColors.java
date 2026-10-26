package we.ketamine.club.mixin.render;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import we.ketamine.club.features.modules.slop.ColorChangerForStuffModule;

@Mixin({ModelBlockRenderer.class})
public class MixinBlockColors {
   public MixinBlockColors() {
   }

   @Inject(
      method = {"getTintColor(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;I)I"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void ketamineclub$blockTint(BlockAndTintGetter level, BlockState state, BlockPos pos, int tintIndex, CallbackInfoReturnable<Integer> cir) {
      ColorChangerForStuffModule module = ColorChangerForStuffModule.active();
      if (module != null) {
         cir.setReturnValue(module.blockColor(state, (Integer)cir.getReturnValue()));
      }
   }
}
