package we.ketamine.club.features;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import we.ketamine.club.event.impl.input.KeyInputEvent;
import we.ketamine.club.event.impl.input.MouseInputEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.gui.KetamineClubGui;
import we.ketamine.club.util.traits.Util;

public class GuiMove implements Util {
   private boolean up;
   private boolean down;
   private boolean left;
   private boolean right;
   private boolean jump;
   private boolean shift;
   private boolean sprint;

   public GuiMove() {
   }

   public void init() {
      EVENT_BUS.register(this);
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (this.shouldRun()) {
            mc.options.keyUp.setDown(this.up);
            mc.options.keyDown.setDown(this.down);
            mc.options.keyLeft.setDown(this.left);
            mc.options.keyRight.setDown(this.right);
            mc.options.keyJump.setDown(this.jump);
            mc.options.keyShift.setDown(this.shift);
            mc.options.keySprint.setDown(this.sprint);
         }
      });
   }

   @Subscribe
   private void onKey(KeyInputEvent event) {
      if (event.getAction() == 1 || event.getAction() == 0) {
         if (!(mc.gui.screen() instanceof KetamineClubGui) || !isArrowKey(event.getKey())) {
            boolean pressed = event.getAction() == 1;
            KeyEvent ke = new KeyEvent(event.getKey(), 0, 0);
            if (mc.options.keyUp.matches(ke)) {
               this.up = pressed;
            } else if (mc.options.keyDown.matches(ke)) {
               this.down = pressed;
            } else if (mc.options.keyLeft.matches(ke)) {
               this.left = pressed;
            } else if (mc.options.keyRight.matches(ke)) {
               this.right = pressed;
            } else if (mc.options.keyJump.matches(ke)) {
               this.jump = pressed;
            } else if (mc.options.keyShift.matches(ke)) {
               this.shift = pressed;
            } else if (mc.options.keySprint.matches(ke)) {
               this.sprint = pressed;
            }
         }
      }
   }

   @Subscribe
   private void onMouse(MouseInputEvent event) {
      if (event.getAction() == 1 || event.getAction() == 0) {
         boolean pressed = event.getAction() == 1;
         MouseButtonEvent me = new MouseButtonEvent(0.0, 0.0, new MouseButtonInfo(event.getButton(), 0));
         if (mc.options.keyUp.matchesMouse(me)) {
            this.up = pressed;
         } else if (mc.options.keyDown.matchesMouse(me)) {
            this.down = pressed;
         } else if (mc.options.keyLeft.matchesMouse(me)) {
            this.left = pressed;
         } else if (mc.options.keyRight.matchesMouse(me)) {
            this.right = pressed;
         } else if (mc.options.keyJump.matchesMouse(me)) {
            this.jump = pressed;
         } else if (mc.options.keyShift.matchesMouse(me)) {
            this.shift = pressed;
         } else if (mc.options.keySprint.matchesMouse(me)) {
            this.sprint = pressed;
         }
      }
   }

   private static boolean isArrowKey(int key) {
      return key == 263 || key == 262 || key == 265 || key == 264;
   }

   private boolean shouldRun() {
      Screen screen = mc.gui.screen();
      return screen != null
         && !(screen instanceof ChatScreen)
         && !(screen instanceof AbstractSignEditScreen)
         && !(screen instanceof AnvilScreen)
         && !(screen instanceof AbstractCommandBlockEditScreen)
         && !(screen instanceof StructureBlockEditScreen);
   }
}
