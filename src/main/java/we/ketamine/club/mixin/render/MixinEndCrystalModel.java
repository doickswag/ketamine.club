package we.ketamine.club.mixin.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.slop.CrystalChangerModule;

@Mixin({EndCrystalModel.class})
public abstract class MixinEndCrystalModel {
   @Unique
   private static final float ketamineclub$restingOuterGlassY = 4.0F + 4.0F * (0.5F + Mth.sqrt(1.5F));
   @Shadow
   @Final
   public ModelPart outerGlass;
   @Unique
   private float ketamineclub$originalAge;
   @Unique
   private CrystalChangerModule ketamineclub$module;

   public MixinEndCrystalModel() {
   }

   @Inject(
      method = {"setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V"},
      at = {@At("HEAD")}
   )
   private void ketamineclub$scaledSpin(EndCrystalRenderState state, CallbackInfo ci) {
      this.ketamineclub$module = CrystalChangerModule.active();
      if (this.ketamineclub$module != null) {
         this.ketamineclub$originalAge = state.ageInTicks;
         state.ageInTicks = state.ageInTicks * this.ketamineclub$module.spin.getValue().floatValue();
      }
   }

   @Inject(
      method = {"setupAnim(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;)V"},
      at = {@At("RETURN")}
   )
   private void ketamineclub$scaledBounce(EndCrystalRenderState state, CallbackInfo ci) {
      CrystalChangerModule module = this.ketamineclub$module;
      if (module != null) {
         float spunOffset = EndCrystalRenderer.getY(state.ageInTicks) * 8.0F;
         float originalOffset = EndCrystalRenderer.getY(this.ketamineclub$originalAge) * 8.0F;
         float baseY = this.outerGlass.y - spunOffset;
         float vanillaY = baseY + originalOffset;
         this.outerGlass.y = Mth.lerp(module.bounce.getValue().floatValue(), ketamineclub$restingOuterGlassY, vanillaY);
         state.ageInTicks = this.ketamineclub$originalAge;
         this.ketamineclub$module = null;
      }
   }
}
