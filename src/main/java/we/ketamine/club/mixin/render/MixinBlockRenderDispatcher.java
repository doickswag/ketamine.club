package we.ketamine.club.mixin.render;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.render.NoRenderModule;

@Mixin({ModelBlockRenderer.class})
public class MixinBlockRenderDispatcher {
   public MixinBlockRenderDispatcher() {
   }

   @Inject(
      method = {"tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$noCrystalFire(
      BlockQuadOutput output,
      float x,
      float y,
      float z,
      BlockAndTintGetter level,
      BlockPos pos,
      BlockState state,
      BlockStateModel model,
      long seed,
      CallbackInfo ci
   ) {
      if (state.getBlock() instanceof BaseFireBlock && NoRenderModule.isActive(m -> m.noCrystalFire.getValue())) {
         ci.cancel();
      }
   }
}
