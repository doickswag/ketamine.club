package we.ketamine.club.manager;

import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.Stage;
import we.ketamine.club.event.impl.entity.player.TickEvent;
import we.ketamine.club.event.impl.entity.player.UpdateWalkingPlayerEvent;
import we.ketamine.club.event.impl.input.KeyInputEvent;
import we.ketamine.club.event.impl.input.MouseInputEvent;
import we.ketamine.club.event.impl.network.ChatEvent;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.Feature;
import we.ketamine.club.features.modules.client.ClickGuiModule;
import we.ketamine.club.pingbypass.PingBypass;
import we.ketamine.club.pingbypass.PingBypassModules;

public class EventManager extends Feature {
   public EventManager() {
   }

   public void init() {
      EVENT_BUS.register(this);
   }

   public void onUnload() {
      EVENT_BUS.unregister(this);
   }

   @Subscribe
   public void onTick(TickEvent event) {
      if (!nullCheck()) {
         KetamineClub.moduleManager.onTick();
      }
   }

   @Subscribe(
      priority = 100
   )
   public void onUpdateWalkingPlayer(UpdateWalkingPlayerEvent event) {
      if (!nullCheck() && !PingBypassModules.isViewer()) {
         if (event.getStage() == Stage.PRE) {
            KetamineClub.positionManager.updatePosition();
         }

         if (event.getStage() == Stage.POST) {
            KetamineClub.positionManager.restorePosition();
         }
      }
   }

   @Subscribe
   public void onWorldRender(Render3DEvent event) {
      KetamineClub.moduleManager.onRender3D(event);
   }

   @Subscribe
   public void onRenderGameOverlayEvent(Render2DEvent event) {
      KetamineClub.moduleManager.onRender2D(event);
   }

   @Subscribe
   public void onKeyInput(KeyInputEvent event) {
      if (!PingBypass.isWorker()) {
         if (event.getAction() == 1) {
            if (mc.gui.screen() == null && ClickGuiModule.matchesKey(event.getKey())) {
               ClickGuiModule.syncModuleBind();
               ClickGuiModule.getInstance().toggle();
            } else {
               KetamineClub.moduleManager.onKeyPressed(event.getKey());
            }
         } else if (event.getAction() == 0) {
            KetamineClub.moduleManager.onKeyReleased(event.getKey());
         }
      }
   }

   @Subscribe
   public void onMouseInput(MouseInputEvent event) {
      if (!PingBypass.isWorker()) {
         if (event.getAction() == 1) {
            KetamineClub.moduleManager.onMousePressed(event.getButton());
         } else if (event.getAction() == 0) {
            KetamineClub.moduleManager.onMouseReleased(event.getButton());
         }
      }
   }

   @Subscribe
   public void onChatSent(ChatEvent event) {
      String message = event.getMessage();
      if (message.startsWith(KetamineClub.commandManager.getCommandPrefix())) {
         event.cancel();
         KetamineClub.commandManager.onChatSent(message);
      }
   }
}
