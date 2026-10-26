package we.ketamine.club.mixin.render;

import java.util.Map;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty;
import net.minecraft.client.renderer.rendertype.RenderSetup.TextureBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({RenderSetup.class})
public interface RenderSetupAccessor {
   @Accessor("textures")
   Map<String, TextureBinding> ketamineclub$getTextures();

   @Accessor("textureTransform")
   TextureTransform ketamineclub$getTextureTransform();

   @Accessor("outputTarget")
   OutputTarget ketamineclub$getOutputTarget();

   @Accessor("outlineProperty")
   OutlineProperty ketamineclub$getOutlineProperty();

   @Accessor("useLightmap")
   boolean ketamineclub$useLightmap();

   @Accessor("useOverlay")
   boolean ketamineclub$useOverlay();

   @Accessor("affectsCrumbling")
   boolean ketamineclub$affectsCrumbling();

   @Accessor("sortOnUpload")
   boolean ketamineclub$sortOnUpload();

   @Accessor("layeringTransform")
   LayeringTransform ketamineclub$getLayeringTransform();
}
