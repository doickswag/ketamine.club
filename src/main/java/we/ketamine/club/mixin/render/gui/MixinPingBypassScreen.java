package we.ketamine.club.mixin.render.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.pingbypass.PingBypass;
import we.ketamine.club.pingbypass.ui.PingBypassScreen;

@Mixin({Screen.class})
public abstract class MixinPingBypassScreen {
   @Shadow
   public int width;
   @Shadow
   @Final
   protected Minecraft minecraft;
   @Unique
   private Button ketamineclub$pingBypassButton;

   public MixinPingBypassScreen() {
   }

   @Shadow
   public abstract List<? extends GuiEventListener> children();

   @Shadow
   protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T var1);

   @Inject(
      method = {"init(II)V", "rebuildWidgets()V"},
      at = {@At("RETURN")}
   )
   private void ketamineclub$addPingBypassButton(CallbackInfo ci) {
      Screen screen = (Screen)(Object)this;
      if (!PingBypass.isWorker() && (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen || screen instanceof PauseScreen)) {
         if (this.ketamineclub$pingBypassButton == null || !this.children().contains(this.ketamineclub$pingBypassButton)) {
            this.ketamineclub$pingBypassButton = this.addRenderableWidget(
               Button.builder(Component.literal("PingBypass"), button -> this.minecraft.gui.setScreen(new PingBypassScreen(screen)))
                  .bounds(this.width - 108, 4, 104, 20)
                  .build()
            );
         }

         this.ketamineclub$pingBypassButton.setX(this.width - 108);
      }
   }
}
