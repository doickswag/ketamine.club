package we.ketamine.club.pingbypass;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.input.KeyInputEvent;
import we.ketamine.club.event.impl.input.MouseInputEvent;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.traits.Util;

public final class PingBypassInput {
   private static final Set<Integer> localHeld = new TreeSet<>();
   private static final Set<Integer> remoteHeld = new TreeSet<>();
   private static Consumer<JsonObject> sender;
   private static boolean remoteScreenOpen;
   private static String lastSnapshot = "";

   private PingBypassInput() {
   }

   public static void attachViewer(Consumer<JsonObject> outbound) {
      detachViewer();
      sender = Objects.requireNonNull(outbound);
      Minecraft mc = Minecraft.getInstance();
      if (mc.gui.screen() == null && mc.isWindowActive()) {
         for (Module module : KetamineClub.moduleManager.getModules()) {
            for (Setting<?> setting : module.getSettings()) {
               if (setting.getValue() instanceof Bind bind && validCode(bind.getKey()) && physicallyDown(bind.getKey())) {
                  localHeld.add(bind.getKey());
               }
            }
         }
      }

      tickViewer();
   }

   public static void detachViewer() {
      sender = null;
      localHeld.clear();
      lastSnapshot = "";
   }

   public static void tickViewer() {
      if (sender != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gui.screen() == null && mc.isWindowActive()) {
            localHeld.removeIf(code -> !physicallyDown(code));
         } else {
            localHeld.clear();
         }

         sendSnapshot();
      }
   }

   public static void key(int key, int action) {
      input("key", key, key, action);
   }

   public static void mouse(int button, int action) {
      input("mouse", button, 1000 + button, action);
   }

   private static void input(String kind, int code, int bindCode, int action) {
      if (sender != null && validCode(bindCode) && (action == 1 || action == 0)) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gui.screen() == null && mc.isWindowActive()) {
            if (action == 1) {
               localHeld.add(bindCode);
            } else {
               localHeld.remove(bindCode);
            }

            sendSnapshot();
            JsonObject event = new JsonObject();
            event.addProperty("kind", kind);
            event.addProperty("code", code);
            event.addProperty("action", action);
            event.addProperty("screenOpen", false);
            sender.accept(event);
         } else {
            localHeld.clear();
            sendSnapshot();
         }
      }
   }

   private static void sendSnapshot() {
      JsonObject state = new JsonObject();
      state.addProperty("kind", "state");
      state.addProperty("screenOpen", Minecraft.getInstance().gui.screen() != null);
      JsonArray keys = new JsonArray();
      localHeld.forEach(keys::add);
      state.add("keys", keys);
      String encoded = state.toString();
      if (!encoded.equals(lastSnapshot)) {
         lastSnapshot = encoded;
         sender.accept(state);
      }
   }

   public static void apply(JsonObject message) {
      String kind = message.get("kind").getAsString();
      boolean screenOpen = message.get("screenOpen").getAsBoolean();
      if (kind.equals("state")) {
         JsonArray keys = message.getAsJsonArray("keys");
         if (keys != null && keys.size() <= 357) {
            Set<Integer> validated = new TreeSet<>();

            for (JsonElement key : keys) {
               int code = key.getAsInt();
               if (!validCode(code)) {
                  throw new IllegalArgumentException("Invalid held input key");
               }

               validated.add(code);
            }

            remoteScreenOpen = screenOpen;
            remoteHeld.clear();
            if (!screenOpen) {
               remoteHeld.addAll(validated);
            }
         } else {
            throw new IllegalArgumentException("Invalid held input snapshot");
         }
      } else {
         int code = message.get("code").getAsInt();
         int action = message.get("action").getAsInt();
         int bindCode = kind.equals("mouse") ? 1000 + code : code;
         if ((kind.equals("key") || kind.equals("mouse"))
            && validCode(bindCode)
            && (!kind.equals("key") || !Bind.isMouseButton(code))
            && (!kind.equals("mouse") || code >= 0 && code <= 7)
            && (action == 1 || action == 0)) {
            remoteScreenOpen = screenOpen;
            if (screenOpen) {
               remoteHeld.clear();
            } else {
               if (action == 1) {
                  remoteHeld.add(bindCode);
               } else {
                  remoteHeld.remove(bindCode);
               }

               if (kind.equals("key")) {
                  Util.EVENT_BUS.post(new KeyInputEvent(code, action));
               } else {
                  Util.EVENT_BUS.post(new MouseInputEvent(code, action));
               }
            }
         } else {
            throw new IllegalArgumentException("Invalid game input event");
         }
      }
   }

   public static void resetWorker() {
      remoteHeld.clear();
      remoteScreenOpen = false;
   }

   public static boolean isKeyDown(int code) {
      return !remoteScreenOpen && remoteHeld.contains(code);
   }

   public static boolean isScreenOpen() {
      Minecraft mc = Minecraft.getInstance();
      return PingBypass.isWorker() && remoteScreenOpen || mc.gui.screen() != null;
   }

   private static boolean validCode(int code) {
      return code >= 32 && code <= 348 || code >= 1000 && code <= 1007;
   }

   private static boolean physicallyDown(int code) {
      long window = Minecraft.getInstance().getWindow().handle();
      return Bind.isMouseButton(code) ? GLFW.glfwGetMouseButton(window, code - 1000) == 1 : GLFW.glfwGetKey(window, code) == 1;
   }
}
