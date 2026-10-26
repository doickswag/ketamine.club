package we.ketamine.club.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({GuiGraphicsExtractor.class})
public interface GuiGraphicsExtractorAccessor {
   @Invoker("innerFill")
   void ketamineclub$innerFill(RenderPipeline var1, TextureSetup var2, int var3, int var4, int var5, int var6, int var7, Integer var8);
}
