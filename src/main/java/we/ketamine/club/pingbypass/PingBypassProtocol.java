package we.ketamine.club.pingbypass;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;

public final class PingBypassProtocol {
   private static volatile PingBypassProtocol.Bridge bridge;

   private PingBypassProtocol() {
   }

   public static boolean hasViaFabricPlus() {
      return FabricLoader.getInstance().isModLoaded("viafabricplus");
   }

   public static PingBypassProtocol.Selection captureSelected() {
      PingBypassProtocol.Bridge api = api();
      return api == null ? PingBypassProtocol.Selection.nativeVersion() : api.selection(api.call(api.getTargetVersion, api.platform));
   }

   public static String selectionLabel() {
      return captureSelected().label();
   }

   public static boolean usesLegacySequencing() {
      PingBypassProtocol.Bridge api = api();
      if (api == null) {
         return false;
      } else {
         Object target = api.call(api.getTargetVersion, api.platform);
         return target != api.autoVersion && (Boolean)api.call(api.olderThanOrEqualTo, target, api.lastUnsequencedVersion);
      }
   }

   public static boolean needsSyntheticLoadingStart() {
      PingBypassProtocol.Bridge api = api();
      if (api == null) {
         return false;
      } else {
         Object target = api.call(api.getTargetVersion, api.platform);
         return target != api.autoVersion && (Boolean)api.call(api.olderThanOrEqualTo, target, api.lastVersionWithoutLoadingEvent);
      }
   }

   public static boolean isInternalPayload(Packet<?> packet) {
      return packet instanceof ClientboundCustomPayloadPacket custom
         && custom.payload().getClass().getName().equals("com.viaversion.viafabricplus.util.network.DataCustomPayload");
   }

   public static void openSelector(Screen parent) {
      PingBypassProtocol.Bridge api = api();
      if (api == null) {
         throw new IllegalStateException("Install ViaFabricPlus to select a different server version.");
      } else {
         api.call(api.openSelector, api.platform, parent);
      }
   }

   public static void validateWorker(PingBypassProtocol.Selection selection) {
      Objects.requireNonNull(selection, "Missing server protocol selection.");
      PingBypassProtocol.Bridge api = api();
      if (api == null) {
         if (selection.requiresTranslation()) {
            throw new IllegalStateException(
               "The PingBypass worker needs ViaFabricPlus for "
                  + selection.label()
                  + ". Install the same ViaFabricPlus version on the local client and redeploy."
            );
         }
      } else {
         api.resolve(selection);
      }
   }

   public static void configureViewer(ServerData server) {
      PingBypassProtocol.Bridge api = api();
      if (api != null) {
         api.force(server, api.nativeVersion);
      }
   }

   public static void configureWorker(ServerData server, PingBypassProtocol.Selection selection) {
      validateWorker(selection);
      PingBypassProtocol.Bridge api = api();
      if (api != null) {
         api.force(server, api.resolve(selection));
      }
   }

   private static PingBypassProtocol.Bridge api() {
      if (!hasViaFabricPlus()) {
         return null;
      } else {
         PingBypassProtocol.Bridge result = bridge;
         if (result == null) {
            synchronized (PingBypassProtocol.class) {
               result = bridge;
               if (result == null) {
                  try {
                     result = new PingBypassProtocol.Bridge();
                     bridge = result;
                  } catch (LinkageError | ReflectiveOperationException var4) {
                     throw incompatible(var4);
                  }
               }
            }
         }

         return result;
      }
   }

   private static String string(JsonObject object, String name) {
      JsonElement element = object.get(name);
      if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
         return element.getAsString();
      } else {
         throw invalidSelection();
      }
   }

   private static int integer(JsonObject object, String name) {
      JsonElement element = object.get(name);
      if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
         try {
            return element.getAsBigDecimal().intValueExact();
         } catch (NumberFormatException | ArithmeticException var4) {
            throw invalidSelection();
         }
      } else {
         throw invalidSelection();
      }
   }

   private static IllegalArgumentException invalidSelection() {
      return new IllegalArgumentException("Invalid PingBypass server protocol selection.");
   }

   private static IllegalStateException incompatible(Throwable failure) {
      Throwable cause = failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
      return new IllegalStateException("PingBypass could not use the installed ViaFabricPlus API. Use ViaFabricPlus 4.6.1 for Minecraft 26.2.", cause);
   }

   private static final class Bridge {
      final Object platform;
      final Object nativeVersion;
      final Object autoVersion;
      final Object lastUnsequencedVersion;
      final Object firstSupportedVersion;
      final Object lastVersionWithoutLoadingEvent;
      final Method getTargetVersion;
      final Method openSelector;
      final Method forceVersion;
      final Method getOriginalVersion;
      final Method getVersionType;
      final Method getName;
      final Method getProtocols;
      final Method olderThanOrEqualTo;

      Bridge() throws ReflectiveOperationException {
         Class<?> entrypoint = Class.forName("com.viaversion.viafabricplus.ViaFabricPlus");
         Class<?> api = Class.forName("com.viaversion.viafabricplus.api.ViaFabricPlusBase");
         Class<?> protocol = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
         Class<?> translator = Class.forName("com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator");
         Class<?> server = Class.forName("com.viaversion.viafabricplus.injection.access.core.IServerData");
         this.platform = entrypoint.getMethod("getImpl").invoke(null);
         if (this.platform == null) {
            throw new IllegalStateException("ViaFabricPlus has not finished initializing.");
         } else {
            this.nativeVersion = translator.getField("NATIVE_VERSION").get(null);
            this.autoVersion = translator.getField("AUTO_DETECT_PROTOCOL").get(null);
            this.lastUnsequencedVersion = protocol.getField("v1_18_2").get(null);
            this.firstSupportedVersion = protocol.getField("v1_7_2").get(null);
            this.lastVersionWithoutLoadingEvent = protocol.getField("v1_20").get(null);
            this.getTargetVersion = api.getMethod("getTargetVersion");
            this.openSelector = api.getMethod("openProtocolSelectionScreen", Screen.class);
            this.forceVersion = server.getMethod("viaFabricPlus$forceVersion", protocol);
            this.getOriginalVersion = protocol.getMethod("getOriginalVersion");
            this.getVersionType = protocol.getMethod("getVersionType");
            this.getName = protocol.getMethod("getName");
            this.getProtocols = protocol.getMethod("getProtocols");
            this.olderThanOrEqualTo = protocol.getMethod("olderThanOrEqualTo", protocol);
         }
      }

      PingBypassProtocol.Selection selection(Object version) {
         if (this.nativeVersion.equals(version)) {
            return PingBypassProtocol.Selection.nativeVersion();
         } else if (version == this.autoVersion) {
            return PingBypassProtocol.Selection.autoDetect();
         } else {
            PingBypassProtocol.Selection selected = new PingBypassProtocol.Selection(
               "version",
               ((Enum)this.call(this.getVersionType, version)).name(),
               (Integer)this.call(this.getOriginalVersion, version),
               (String)this.call(this.getName, version)
            );
            this.requireSupported(version, selected);
            return selected;
         }
      }

      Object resolve(PingBypassProtocol.Selection selection) {
         if (selection.mode().equals("native")) {
            return this.nativeVersion;
         } else if (selection.mode().equals("auto")) {
            return this.autoVersion;
         } else {
            for (Object protocol : (Iterable)this.call(this.getProtocols, null)) {
               if (((Enum)this.call(this.getVersionType, protocol)).name().equals(selection.type())
                  && (Integer)this.call(this.getOriginalVersion, protocol) == selection.id()) {
                  this.requireSupported(protocol, selection);
                  if (!this.call(this.getName, protocol).equals(selection.name())) {
                     throw new IllegalStateException("ViaFabricPlus protocol mappings differ between the viewer and worker. Install matching versions.");
                  }

                  return protocol;
               }
            }

            throw new IllegalStateException(
               "The worker's ViaFabricPlus does not support " + selection.label() + ". Install the same ViaFabricPlus version on both clients."
            );
         }
      }

      void requireSupported(Object version, PingBypassProtocol.Selection selection) {
         if (!(Boolean)this.call(this.olderThanOrEqualTo, this.firstSupportedVersion, version)) {
            throw new IllegalArgumentException("PingBypass supports Java release protocols from 1.7.2 onward.");
         } else if (!(Boolean)this.call(this.olderThanOrEqualTo, version, this.nativeVersion)) {
            throw new IllegalArgumentException("PingBypass cannot translate to the newer protocol " + selection.label() + ".");
         }
      }

      void force(ServerData server, Object version) {
         if (!this.forceVersion.getDeclaringClass().isInstance(server)) {
            throw new IllegalStateException("ViaFabricPlus's per-server version mixin is unavailable. Restart with a compatible ViaFabricPlus build.");
         } else {
            this.call(this.forceVersion, server, version);
         }
      }

      Object call(Method method, Object receiver, Object... arguments) {
         try {
            return method.invoke(receiver, arguments);
         } catch (LinkageError | ReflectiveOperationException var5) {
            throw PingBypassProtocol.incompatible(var5);
         }
      }
   }

   public record Selection(String mode, String type, int id, String name) {
      public Selection(String mode, String type, int id, String name) {
         Objects.requireNonNull(mode, "Missing protocol selection mode.");
         Objects.requireNonNull(type, "Missing protocol selection type.");
         Objects.requireNonNull(name, "Missing protocol selection name.");
         switch (mode) {
            case "native":
               if (!type.isEmpty() || id != 0 || !name.equals("Native")) {
                  throw PingBypassProtocol.invalidSelection();
               }
               break;
            case "auto":
               if (!type.isEmpty() || id != 0 || !name.equals("Auto Detect")) {
                  throw PingBypassProtocol.invalidSelection();
               }
               break;
            case "version":
               if (!type.equals("RELEASE")) {
                  throw new IllegalArgumentException("PingBypass supports Java release protocols from 1.7.2 onward. Select one in ViaFabricPlus.");
               }

               if (id < 0 || name.isBlank() || name.length() > 100 || name.chars().anyMatch(Character::isISOControl)) {
                  throw PingBypassProtocol.invalidSelection();
               }
               break;
            default:
               throw PingBypassProtocol.invalidSelection();
         }

         this.mode = mode;
         this.type = type;
         this.id = id;
         this.name = name;
      }

      public static PingBypassProtocol.Selection nativeVersion() {
         return new PingBypassProtocol.Selection("native", "", 0, "Native");
      }

      public static PingBypassProtocol.Selection autoDetect() {
         return new PingBypassProtocol.Selection("auto", "", 0, "Auto Detect");
      }

      public boolean requiresTranslation() {
         return !this.mode.equals("native");
      }

      public String label() {
         return this.name;
      }

      public JsonObject toJson() {
         JsonObject result = new JsonObject();
         result.addProperty("mode", this.mode);
         if (this.mode.equals("version")) {
            result.addProperty("type", this.type);
            result.addProperty("id", this.id);
            result.addProperty("name", this.name);
         }

         return result;
      }

      public static PingBypassProtocol.Selection fromJson(JsonObject value) {
         if (value == null) {
            throw PingBypassProtocol.invalidSelection();
         } else {
            String var1 = PingBypassProtocol.string(value, "mode");

            return switch (var1) {
               case "native" -> nativeVersion();
               case "auto" -> autoDetect();
               case "version" -> new PingBypassProtocol.Selection(
                  "version", PingBypassProtocol.string(value, "type"), PingBypassProtocol.integer(value, "id"), PingBypassProtocol.string(value, "name")
               );
               default -> throw PingBypassProtocol.invalidSelection();
            };
         }
      }
   }
}
