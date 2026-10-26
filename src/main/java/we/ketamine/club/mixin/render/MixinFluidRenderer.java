package we.ketamine.club.mixin.render;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidRenderer.Output;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.slop.ColorChangerForStuffModule;

@Mixin({FluidRenderer.class})
public class MixinFluidRenderer {
   @Unique
   private boolean ketamineclub$renderingLava;

   public MixinFluidRenderer() {
   }

   @Inject(
      method = {"tesselate(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"},
      at = {@At("HEAD")}
   )
   private void ketamineclub$beginLiquid(BlockAndTintGetter level, BlockPos pos, Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
      ColorChangerForStuffModule module = ColorChangerForStuffModule.active();
      this.ketamineclub$renderingLava = module != null && module.lava.getValue() && fluidState.is(FluidTags.LAVA);
   }

   @Inject(
      method = {"tesselate(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"},
      at = {@At("RETURN")}
   )
   private void ketamineclub$endLiquid(BlockAndTintGetter level, BlockPos pos, Output output, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
      this.ketamineclub$renderingLava = false;
   }

   @ModifyArg(
      method = {"addFace(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFFFFFFFFFFFFFIIZ)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/block/FluidRenderer;vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFIFFI)V"
      ),
      index = 4
   )
   private int ketamineclub$lavaColor(int original) {
      if (!this.ketamineclub$renderingLava) {
         return original;
      } else {
         ColorChangerForStuffModule module = ColorChangerForStuffModule.active();
         return module == null ? original : 0xFF000000 | module.lavaColor(original);
      }
   }
}
