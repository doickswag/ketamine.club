package we.ketamine.club.features.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;

public final class GuiDevBridge {
   private static final Minecraft MC = Minecraft.getInstance();
   private static final String BRIDGE_DIR = "ketamineclub-ui-bridge";
   private static boolean initialized;
   private static Path directory;
   private static Path commandFile;
   private static Path statusFile;
   private static long lastCommandStamp = Long.MIN_VALUE;
   private static final String README = "KetamineClub ClickGUI development bridge\n\nWrite one JSON object to command.json. The client consumes it once and writes status.json.\nScreenshots are saved under run/screenshots/.\n\nExamples:\n  {\"action\":\"open\"}\n  {\"action\":\"arrange\"}\n  {\"action\":\"collapse\",\"category\":\"CLIENT\"}\n  {\"action\":\"panel\",\"category\":\"CLIENT\",\"x\":8,\"y\":8,\"open\":true}\n  {\"action\":\"module\",\"module\":\"ClickGUI\",\"open\":true}\n  {\"action\":\"colorPicker\",\"module\":\"ClickGUI\",\"setting\":\"Custom Color\",\"open\":true}\n  {\"action\":\"set\",\"module\":\"ClickGUI\",\"setting\":\"Theme\",\"value\":\"CUSTOM\"}\n  {\"action\":\"set\",\"module\":\"ClickGUI\",\"setting\":\"Custom Color\",\"value\":\"#9fc2d8ff\"}\n  {\"action\":\"toggle\",\"module\":\"ClickGUI\"}\n  {\"action\":\"fullscreen\",\"enabled\":true}\n  {\"action\":\"screenshot\",\"name\":\"clickgui.png\"}\n  {\"action\":\"close\"}\n";

   private GuiDevBridge() {
   }

   public static void init() {
      if (!initialized && FabricLoader.getInstance().isDevelopmentEnvironment()) {
         initialized = true;
         directory = MC.gameDirectory.toPath().resolve("ketamineclub-ui-bridge");
         commandFile = directory.resolve("command.json");
         statusFile = directory.resolve("status.json");

         try {
            Files.createDirectories(directory);
            Files.writeString(
               directory.resolve("README.txt"),
               "KetamineClub ClickGUI development bridge\n\nWrite one JSON object to command.json. The client consumes it once and writes status.json.\nScreenshots are saved under run/screenshots/.\n\nExamples:\n  {\"action\":\"open\"}\n  {\"action\":\"arrange\"}\n  {\"action\":\"collapse\",\"category\":\"CLIENT\"}\n  {\"action\":\"panel\",\"category\":\"CLIENT\",\"x\":8,\"y\":8,\"open\":true}\n  {\"action\":\"module\",\"module\":\"ClickGUI\",\"open\":true}\n  {\"action\":\"colorPicker\",\"module\":\"ClickGUI\",\"setting\":\"Custom Color\",\"open\":true}\n  {\"action\":\"set\",\"module\":\"ClickGUI\",\"setting\":\"Theme\",\"value\":\"CUSTOM\"}\n  {\"action\":\"set\",\"module\":\"ClickGUI\",\"setting\":\"Custom Color\",\"value\":\"#9fc2d8ff\"}\n  {\"action\":\"toggle\",\"module\":\"ClickGUI\"}\n  {\"action\":\"fullscreen\",\"enabled\":true}\n  {\"action\":\"screenshot\",\"name\":\"clickgui.png\"}\n  {\"action\":\"close\"}\n",
               StandardCharsets.UTF_8,
               StandardOpenOption.CREATE,
               StandardOpenOption.TRUNCATE_EXISTING
            );
            writeStatus(true, "Bridge ready. Write a JSON command to command.json.", null);
         } catch (IOException var1) {
            KetamineClub.LOGGER.warn("Unable to initialize ClickGUI dev bridge", var1);
         }

         ClientTickEvents.END_CLIENT_TICK.register(GuiDevBridge::poll);
      }
   }

   private static void poll(Minecraft client) {
      if (initialized && commandFile != null && Files.exists(commandFile)) {
         try {
            long stamp = Files.getLastModifiedTime(commandFile).toMillis();
            if (stamp <= lastCommandStamp) {
               return;
            }

            lastCommandStamp = stamp;
            String raw = Files.readString(commandFile, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) {
               writeStatus(false, "Command must be a JSON object.", null);
               return;
            }

            apply(parsed.getAsJsonObject());
         } catch (Exception var5) {
            writeStatus(false, "Command failed: " + var5.getMessage(), null);
            KetamineClub.LOGGER.warn("ClickGUI dev bridge command failed", var5);
         }
      }
   }

   private static void apply(JsonObject command) {
      String action = string(command, "action", "").toLowerCase(Locale.ROOT);
      KetamineClubGui gui = KetamineClubGui.getClickGui();
      switch (action) {
         case "open":
            MC.gui.setScreen(gui);
            writeStatus(true, "ClickGUI opened.", null);
            break;
         case "close":
            if (MC.gui.screen() instanceof KetamineClubGui clickGui) {
               clickGui.onClose();
            } else {
               MC.gui.setScreen(null);
            }

            writeStatus(true, "ClickGUI closing.", null);
            break;
         case "screenshot":
            screenshot(command);
            break;
         case "fullscreen":
            boolean enabled = bool(command, "enabled", true);
            if (MC.getWindow() != null && MC.getWindow().isFullscreen() != enabled) {
               MC.getWindow().toggleFullScreen();
            }

            MC.options.fullscreen().set(MC.getWindow() != null && MC.getWindow().isFullscreen());
            MC.options.save();
            writeStatus(true, "Fullscreen " + (MC.options.fullscreen().get() ? "enabled." : "disabled."), null);
            break;
         case "reload":
            gui.reload();
            writeStatus(true, "ClickGUI components reloaded.", null);
            break;
         case "arrange":
            gui.arrangeCompact();
            writeStatus(true, "Panels arranged in a compact row.", null);
            break;
         case "collapse":
            gui.collapsePanelsExcept(string(command, "category", ""));
            writeStatus(true, "Panels collapsed.", null);
            break;
         case "panel":
            String category = string(command, "category", "");
            gui.setPanelState(category, integer(command, "x"), integer(command, "y"), bool(command, "open"));
            writeStatus(true, "Panel updated: " + category, null);
            break;
         case "module": {
            String moduleName = string(command, "module", string(command, "name", ""));
            boolean open = bool(command, "open", true);
            boolean found = gui.setModuleOpen(moduleName, open);
            writeStatus(found, found ? "Module updated: " + moduleName : "Module not found: " + moduleName, null);
            break;
         }
         case "colorpicker": {
            boolean found = gui.setColorPickerOpen(string(command, "module", ""), string(command, "setting", ""), bool(command, "open", true));
            writeStatus(found, found ? "Color picker updated." : "Color picker or module not found.", null);
            break;
         }
         case "set":
            boolean changed = setSetting(command);
            writeStatus(changed, changed ? "Setting updated." : "Module or setting not found.", null);
            break;
         case "toggle":
            Module module = findModule(string(command, "module", string(command, "name", "")));
            if (module == null) {
               writeStatus(false, "Module not found.", null);
            } else {
               module.toggle();
               writeStatus(true, "Toggled " + module.getName() + ".", null);
            }
            break;
         default:
            writeStatus(false, "Unknown action: " + action, null);
      }
   }

   private static void screenshot(JsonObject command) {
      String fileName = safeFileName(string(command, "name", "clickgui.png"));
      Screenshot.grab(
         MC.gameDirectory, fileName, MC.gameRenderer.mainRenderTarget(), 1, message -> writeStatus(true, "Screenshot saved: " + fileName, message.getString())
      );
   }

   private static boolean setSetting(JsonObject command) {
      Module module = findModule(string(command, "module", ""));
      if (module != null && command.has("value")) {
         String wanted = normalize(string(command, "setting", ""));

         for (Setting<?> setting : module.getSettings()) {
            if (normalize(setting.getName()).equals(wanted)) {
               return assign(setting, command.get("value"));
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean assign(Setting<?> setting, JsonElement value) {
      if (setting.getFeature() instanceof Module module && setting == module.enabled) {
         module.setToggled(value.getAsBoolean());
         return true;
      } else {
         Object current = setting.getValue();
         if (current instanceof Boolean) {
            ((Setting<Boolean>)setting).setValue(value.getAsBoolean());
         } else if (current instanceof String) {
            ((Setting<String>)setting).setValue(value.getAsString());
         } else if (current instanceof Integer) {
            ((Setting<Integer>)setting).setValue(value.getAsInt());
         } else if (current instanceof Float) {
            ((Setting<Float>)setting).setValue(value.getAsFloat());
         } else if (current instanceof Double) {
            ((Setting<Double>)setting).setValue(value.getAsDouble());
         } else if (current instanceof Long) {
            ((Setting<Long>)setting).setValue(value.getAsLong());
         } else if (current instanceof Enum) {
            setting.setEnumValue(value.getAsString());
         } else {
            if (!(current instanceof Color)) {
               return false;
            }

            ((Setting<Color>)setting).setValue(parseColor(value, (Color)current));
         }

         return true;
      }
   }

   private static Color parseColor(JsonElement value, Color fallback) {
      if (value.isJsonObject()) {
         JsonObject color = value.getAsJsonObject();
         return new Color(
            clamp(integer(color, "r", fallback.getRed()), 0, 255),
            clamp(integer(color, "g", fallback.getGreen()), 0, 255),
            clamp(integer(color, "b", fallback.getBlue()), 0, 255),
            clamp(integer(color, "a", fallback.getAlpha()), 0, 255)
         );
      } else {
         String raw = value.getAsString().replace("#", "");

         try {
            long parsed = Long.parseLong(raw, 16);
            return raw.length() <= 6 ? new Color((int)parsed | 0xFF000000, true) : new Color((int)parsed, true);
         } catch (NumberFormatException var5) {
            return fallback;
         }
      }
   }

   private static Module findModule(String name) {
      if (name != null && !name.isBlank()) {
         Module module = KetamineClub.moduleManager.getModuleByName(name);
         if (module != null) {
            return module;
         } else {
            for (Module candidate : KetamineClub.moduleManager.getModules()) {
               if (normalize(candidate.getName()).equals(normalize(name)) || normalize(candidate.getDisplayName()).equals(normalize(name))) {
                  return candidate;
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private static String safeFileName(String name) {
      String clean = name == null ? "clickgui.png" : name.replaceAll("[^A-Za-z0-9._-]", "_");
      if (!clean.toLowerCase(Locale.ROOT).endsWith(".png")) {
         clean = clean + ".png";
      }

      return clean;
   }

   private static String normalize(String value) {
      return value == null ? "" : value.replace(" ", "").replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
   }

   private static String string(JsonObject object, String key, String fallback) {
      return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
   }

   private static Integer integer(JsonObject object, String key) {
      return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : null;
   }

   private static int integer(JsonObject object, String key, int fallback) {
      Integer value = integer(object, key);
      return value == null ? fallback : value;
   }

   private static boolean bool(JsonObject object, String key) {
      return bool(object, key, false);
   }

   private static boolean bool(JsonObject object, String key, boolean fallback) {
      return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static void writeStatus(boolean success, String message, String detail) {
      if (statusFile != null) {
         try {
            JsonObject status = new JsonObject();
            status.addProperty("success", success);
            status.addProperty("message", message);
            if (detail != null) {
               status.addProperty("detail", detail);
            }

            Files.writeString(
               statusFile, status.toString() + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            );
         } catch (IOException var4) {
            KetamineClub.LOGGER.debug("Unable to write ClickGUI bridge status", var4);
         }
      }
   }
}
