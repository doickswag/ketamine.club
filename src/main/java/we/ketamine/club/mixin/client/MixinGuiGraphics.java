package we.ketamine.club.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.features.modules.render.ShulkerPreviewModule;
import we.ketamine.club.util.render.RenderUtil;
import we.ketamine.club.util.render.font.Fonts;
import we.ketamine.club.util.render.state.RectRenderState;

@Mixin({GuiGraphicsExtractor.class})
public abstract class MixinGuiGraphics {
   @Unique
   private static final float KETAMINECLUB$PREVIEW_SCALE = 0.625F;
   @Unique
   private static final float KETAMINECLUB$PREVIEW_X_OFFSET = 4.0F;
   @Unique
   private static final float KETAMINECLUB$PREVIEW_Y_OFFSET = -4.0F;
   @Unique
   private boolean ketamineclub$renderingShulkerPreview;

   public MixinGuiGraphics() {
   }

   @Redirect(
      method = {"innerFill"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addGuiElement(Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;)V"
      )
   )
   private void ketamineclub$horizontalGradient(GuiRenderState renderState, GuiElementRenderState element) {
      if (element instanceof ColoredRectangleRenderState rectangle && RenderUtil.isHorizontalGradientPipeline(rectangle.pipeline())) {
         renderState.addGuiElement(
            new RectRenderState(
               rectangle.pipeline(),
               rectangle.textureSetup(),
               new Matrix3x2f(rectangle.pose()),
               rectangle.x0(),
               rectangle.y0(),
               rectangle.x1(),
               rectangle.y1(),
               rectangle.col1(),
               rectangle.col1(),
               rectangle.col2(),
               rectangle.col2(),
               rectangle.scissorArea()
            )
         );
      } else {
         renderState.addGuiElement(element);
      }
   }

   @Inject(
      method = {"text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$onDrawString(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
      if (text != null && !text.isEmpty() && Fonts.drawOverrideActive()) {
         try {
            if (Fonts.renderOverrideString((GuiGraphicsExtractor)(Object)this, Fonts.applyHudTextCase(text), x, y, color, shadow)) {
               ci.cancel();
            }
         } catch (RuntimeException var9) {
            Fonts.handleFailure(var9);
         }
      }
   }

   @Inject(
      method = {"text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void ketamineclub$onDrawFormatted(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
      if (text != null && Fonts.drawOverrideActive()) {
         try {
            if (Fonts.renderOverrideText((GuiGraphicsExtractor)(Object)this, Fonts.applyHudTextCase(text), x, y, color, shadow)) {
               ci.cancel();
            }
         } catch (RuntimeException var9) {
            Fonts.handleFailure(var9);
         }
      }
   }

   @ModifyVariable(
      method = {"text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private String ketamineclub$lowercaseString(String text) {
      return Fonts.applyHudTextCase(text);
   }

   @ModifyVariable(
      method = {"text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private FormattedCharSequence ketamineclub$lowercaseFormatted(FormattedCharSequence text) {
      return Fonts.applyHudTextCase(text);
   }

   @Inject(
      method = {"itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemBar(Lnet/minecraft/world/item/ItemStack;II)V"
      )}
   )
   private void ketamineclub$renderShulkerPreview(Font font, ItemStack stack, int x, int y, String countText, CallbackInfo ci) {
      ShulkerPreviewModule module = ShulkerPreviewModule.get();
      if (module != null && module.isEnabled()) {
         ItemStack displayStack = module.getDisplayStack(stack);
         if (!displayStack.isEmpty()) {
            this.ketamineclub$renderingShulkerPreview = true;

            try {
               ((GuiGraphicsExtractor)(Object)this).fakeItem(displayStack, x, y);
            } finally {
               this.ketamineclub$renderingShulkerPreview = false;
            }
         }
      }
   }

   @WrapOperation(
      method = {"item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V"},
      at = {@At(
         value = "NEW",
         target = "(Lorg/joml/Matrix3x2f;Lnet/minecraft/client/renderer/item/TrackingItemStackRenderState;IILnet/minecraft/client/gui/navigation/ScreenRectangle;)Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;"
      )}
   )
   private GuiItemRenderState ketamineclub$scaleShulkerPreview(
      Matrix3x2f pose, TrackingItemStackRenderState itemState, int x, int y, ScreenRectangle scissorArea, Operation<GuiItemRenderState> original
   ) {
      if (!this.ketamineclub$renderingShulkerPreview) {
         return (GuiItemRenderState)original.call(new Object[]{pose, itemState, x, y, scissorArea});
      } else {
         Matrix3x2f scaledPose = new Matrix3x2f(pose).scale(0.625F, 0.625F);
         float centerShift = 3.0F;
         int previewX = (int)((x + 4.0F + centerShift) / 0.625F);
         int previewY = (int)((y + -4.0F + centerShift) / 0.625F);
         return (GuiItemRenderState)original.call(new Object[]{scaledPose, itemState, previewX, previewY, scissorArea});
      }
   }
}
