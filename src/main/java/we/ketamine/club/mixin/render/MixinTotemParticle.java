package we.ketamine.club.mixin.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.particle.SingleQuadParticle.Layer;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.render.PopEffectsModule;
import we.ketamine.club.util.render.SeeThroughParticle;

@Mixin({TotemParticle.class})
public abstract class MixinTotemParticle extends SimpleAnimatedParticle {
   protected MixinTotemParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, float scale) {
      super(level, x, y, z, sprites, scale);
   }

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void ketamineclub$customizeTotem(
      ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites, CallbackInfo ci
   ) {
      PopEffectsModule module = PopEffectsModule.get();
      if (module != null && module.shouldCustomize(ParticleTypes.TOTEM_OF_UNDYING)) {
         boolean yellowVariant = this.rCol >= 0.5F && this.gCol >= 0.5F;
         this.setColor(module.getRgb(yellowVariant));
         this.quadSize = this.quadSize * module.getScale();
      }
   }

   public Layer getLayer() {
      PopEffectsModule module = PopEffectsModule.get();
      return module != null && module.shouldCustomize(ParticleTypes.TOTEM_OF_UNDYING) && module.throughWalls.getValue()
         ? SeeThroughParticle.TRANSLUCENT_LAYER
         : super.getLayer();
   }
}
