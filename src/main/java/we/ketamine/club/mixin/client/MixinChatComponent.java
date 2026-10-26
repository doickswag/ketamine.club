package we.ketamine.club.mixin.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ChatComponent.DisplayMode;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.util.player.ChatUtil;

@Mixin({ChatComponent.class})
public abstract class MixinChatComponent {
   @Unique
   private static final double ketamineclub$SPEED = 12.0;
   @Unique
   private static final int ketamineclub$MAX_LINES = 3;
   @Unique
   private boolean ketamineclub$pushed;
   @Unique
   private float ketamineclub$offset;
   @Unique
   private Line ketamineclub$lastHead;
   @Unique
   private long ketamineclub$lastTimeMs;

   public MixinChatComponent() {
   }

   @Inject(
      method = {"extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"},
      at = {@At("HEAD")}
   )
   private void ketamineclub$slideIn(
      GuiGraphicsExtractor graphics, Font font, int tickCount, int mouseX, int mouseY, DisplayMode displayMode, boolean visible, CallbackInfo ci
   ) {
      this.ketamineclub$pushed = false;
      float dy = this.ketamineclub$update(((ChatComponentAccessor)(Object)this).ketamineclub$getTrimmedMessages());
      if (dy != 0.0F) {
         graphics.pose().pushMatrix();
         graphics.pose().translate(0.0F, dy);
         this.ketamineclub$pushed = true;
      }
   }

   @Inject(
      method = {"extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"},
      at = {@At("RETURN")}
   )
   private void ketamineclub$slideInEnd(
      GuiGraphicsExtractor graphics, Font font, int tickCount, int mouseX, int mouseY, DisplayMode displayMode, boolean visible, CallbackInfo ci
   ) {
      if (this.ketamineclub$pushed) {
         graphics.pose().popMatrix();
         this.ketamineclub$pushed = false;
      }
   }

   @Unique
   private float ketamineclub$update(List<Line> lines) {
      long now = System.currentTimeMillis();
      float dt = Math.min((float)(now - this.ketamineclub$lastTimeMs) / 1000.0F, 0.1F);
      this.ketamineclub$lastTimeMs = now;
      Line head = lines.isEmpty() ? null : lines.get(0);
      if (head != this.ketamineclub$lastHead) {
         if (this.ketamineclub$lastHead != null && head != ChatUtil.noAnimateHead) {
            int newLines = 0;

            for (Line line : lines) {
               if (line == this.ketamineclub$lastHead) {
                  break;
               }

               newLines++;
            }

            float pitch = this.ketamineclub$linePitch();
            this.ketamineclub$offset = Math.min(this.ketamineclub$offset + newLines * pitch, 3.0F * pitch);
         }

         this.ketamineclub$lastHead = head;
      }

      this.ketamineclub$offset = this.ketamineclub$offset * (float)Math.exp(-12.0 * dt);
      if (this.ketamineclub$offset < 0.5F) {
         this.ketamineclub$offset = 0.0F;
      }

      return this.ketamineclub$offset;
   }

   @Unique
   private float ketamineclub$linePitch() {
      Minecraft mc = Minecraft.getInstance();
      double scale = (Double)mc.options.chatScale().get();
      double spacing = (Double)mc.options.chatLineSpacing().get();
      return (float)(9.0 * (spacing + 1.0) * scale);
   }
}
