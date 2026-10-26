package we.ketamine.club.mixin.render;

import java.util.Set;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.render.ShadersModule;
import we.ketamine.club.util.render.WorldChamsChain;

@Mixin({LevelRenderer.class})
public class MixinShaderLevelRenderer {
   public MixinShaderLevelRenderer() {
   }

   @Redirect(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"
      )
   )
   private PostChain ketamineclub$swapEntityOutlineChain(ShaderManager shaderManager, Identifier id, Set<Identifier> externalTargets) {
      ShadersModule shaders = KetamineClub.moduleManager == null ? null : KetamineClub.moduleManager.getModuleByClass(ShadersModule.class);
      if (shaders != null && shaders.hasWorldShaderEffect()) {
         PostChain optimised = WorldChamsChain.get(
            externalTargets,
            shaders.getOutlineWidth(),
            shaders.getOutlineAlpha(),
            shaders.getGlowRadius(),
            shaders.getGlowIntensity(),
            shaders.getFillTint(),
            shaders.getFillAlpha(),
            shaders.getInnerGlowRadius(),
            shaders.getInnerGlowIntensity()
         );
         if (optimised != null) {
            return optimised;
         }
      }

      WorldChamsChain.cleanup();
      return shaderManager.getPostChain(id, externalTargets);
   }
}
