package we.ketamine.club.pingbypass;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.player.FreecamModule;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.traits.Util;

public final class PingBypassModules {
   private static volatile boolean viewer;
   private static Consumer<JsonObject> sender;
   private static int applying;
   private static final List<PingBypassModules.ManagerSuspension> suspendedManagers = new ArrayList<>();
   private static final Map<String, Long> revisions = new HashMap<>();
   private static final Set<Module> changedWhileApplying = new LinkedHashSet<>();
   private static final Map<String, PingBypassModules.RuntimeInfo> remoteRuntime = new HashMap<>();

   private PingBypassModules() {
   }

   public static boolean isViewer() {
      return viewer;
   }

   static long revision(String name) {
      return revisions.getOrDefault(name, 0L);
   }

   public static boolean isRemote(Module module) {
      return viewer && runsOnWorker(module);
   }

   public static boolean runsOnWorker(Module module) {
      if (module instanceof FreecamModule) {
         return false;
      } else {
         return switch (module.getCategory()) {
            case RENDER, CLIENT, HUD, SLOP -> false;
            default -> true;
         };
      }
   }

   private static boolean synchronizedModule(Module module) {
      return runsOnWorker(module) || module.getName().equals("Rotations") || module.getName().equals("Targets");
   }

   public static void enterViewer(Consumer<JsonObject> outbound) {
      Objects.requireNonNull(outbound, "outbound");
      if (viewer) {
         throw new IllegalStateException("PingBypass module ownership is already remote");
      } else {
         resetRevisions();
         List<Module> suspended = new ArrayList<>();
         applying++;

         try {
            for (Module module : KetamineClub.moduleManager.getModules()) {
               if (runsOnWorker(module) && module.isToggled()) {
                  suspended.add(module);

                  try {
                     module.disable();
                  } finally {
                     Util.EVENT_BUS.unregister(module);
                     module.enabled.setValueNoEvent(true);
                  }
               }
            }

            suspendManagers();
            viewer = true;
            sender = outbound;

            for (Module modulex : KetamineClub.moduleManager.getModules()) {
               if (synchronizedModule(modulex)) {
                  revisions.put(modulex.getName(), 1L);
               }
            }
         } catch (Throwable var17) {
            Throwable failure = var17;
            restoreManagers();

            for (Module modulexx : suspended) {
               try {
                  modulexx.enabled.setValueNoEvent(false);
                  modulexx.enable();
               } catch (Throwable var15) {
                  failure.addSuppressed(var15);
               }
            }

            Util.throwUnchecked(failure);
         } finally {
            applying--;
         }

         sender.accept(snapshot());
      }
   }

   public static void leaveViewer() {
      sender = null;
      if (viewer) {
         List<Module> restore = new ArrayList<>();
         applying++;

         try {
            for (Module module : KetamineClub.moduleManager.getModules()) {
               if (runsOnWorker(module)) {
                  if (module.isToggled()) {
                     restore.add(module);
                  }

                  module.enabled.setValueNoEvent(false);
                  Util.EVENT_BUS.unregister(module);
               }
            }

            viewer = false;
            restoreManagers();

            for (Module modulex : restore) {
               try {
                  modulex.enable();
               } catch (Throwable var7) {
                  KetamineClub.LOGGER.error("Could not restore local module {} after PingBypass", modulex.getName(), var7);
               }
            }
         } finally {
            viewer = false;
            applying--;
            resetRevisions();
         }
      }
   }

   public static void attachWorker(Consumer<JsonObject> outbound) {
      if (viewer) {
         throw new IllegalStateException("A viewer cannot own worker modules");
      } else {
         resetRevisions();
         sender = Objects.requireNonNull(outbound, "outbound");
      }
   }

   public static void detachWorker() {
      sender = null;
      resetRevisions();
   }

   public static JsonObject snapshot() {
      JsonObject modules = new JsonObject();

      for (Module module : KetamineClub.moduleManager.getModules()) {
         if (synchronizedModule(module)) {
            modules.add(module.getName(), module.toJson());
         }
      }

      return envelope(modules);
   }

   public static JsonObject snapshotRuntime() {
      JsonObject modules = new JsonObject();

      for (Module module : KetamineClub.moduleManager.getModules()) {
         if (runsOnWorker(module)) {
            JsonObject value = new JsonObject();
            String info = null;
            String meta = null;

            try {
               info = bounded(module.getDisplayInfo());
               meta = bounded(module.getMeta());
            } catch (RuntimeException var7) {
               KetamineClub.LOGGER.debug("Could not collect PingBypass HUD values for {}", module.getName(), var7);
            }

            value.addProperty("displayInfo", info);
            value.addProperty("meta", meta);
            modules.add(module.getName(), value);
         }
      }

      return envelope(modules);
   }

   public static void applyRuntime(JsonObject message) {
      if (viewer) {
         JsonObject modules = message.getAsJsonObject("modules");
         JsonObject versions = message.getAsJsonObject("revisions");
         if (modules != null && versions != null) {
            for (Entry<String, JsonElement> entry : modules.entrySet()) {
               Module module = KetamineClub.moduleManager.getModuleByName(entry.getKey());
               JsonElement revision = versions.get(entry.getKey());
               if (module != null
                  && runsOnWorker(module)
                  && entry.getValue().isJsonObject()
                  && revision != null
                  && revision.getAsLong() == revisions.getOrDefault(module.getName(), 0L)) {
                  JsonObject value = entry.getValue().getAsJsonObject();
                  remoteRuntime.put(module.getName(), new PingBypassModules.RuntimeInfo(runtimeText(value.get("displayInfo")), runtimeText(value.get("meta"))));
               }
            }
         } else {
            throw new IllegalArgumentException("Invalid module runtime snapshot");
         }
      }
   }

   public static String displayInfo(Module module) {
      if (!isRemote(module)) {
         return module.getDisplayInfo();
      } else {
         PingBypassModules.RuntimeInfo value = remoteRuntime.get(module.getName());
         return value == null ? null : value.displayInfo();
      }
   }

   public static String getMeta(Module module) {
      if (!isRemote(module)) {
         return module.getMeta();
      } else {
         PingBypassModules.RuntimeInfo value = remoteRuntime.get(module.getName());
         return value == null ? null : value.meta();
      }
   }

   private static String runtimeText(JsonElement value) {
      if (value == null || value.isJsonNull()) {
         return null;
      } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
         return bounded(value.getAsString());
      } else {
         throw new IllegalArgumentException("Invalid module HUD text");
      }
   }

   private static String bounded(String value) {
      return value != null && value.length() > 512 ? value.substring(0, 512) : value;
   }

   public static void apply(JsonObject message) {
      JsonElement value = message.get("modules");
      if (value != null && value.isJsonObject()) {
         JsonElement revisionValue = message.get("revisions");
         if (revisionValue != null && revisionValue.isJsonObject()) {
            JsonObject incomingRevisions = revisionValue.getAsJsonObject();
            JsonObject normalized = new JsonObject();
            applying++;

            try {
               for (Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                  Module module = KetamineClub.moduleManager.getModuleByName(entry.getKey());
                  if (module != null && synchronizedModule(module) && entry.getValue().isJsonObject()) {
                     JsonElement revision = incomingRevisions.get(entry.getKey());
                     if (revision == null || !revision.isJsonPrimitive() || !revision.getAsJsonPrimitive().isNumber()) {
                        throw new IllegalArgumentException("Missing revision for " + entry.getKey());
                     }

                     long incoming = revision.getAsLong();
                     if (incoming < 0L) {
                        throw new IllegalArgumentException("Negative module revision");
                     }

                     long current = revisions.getOrDefault(module.getName(), 0L);
                     if (viewer ? incoming == current : incoming > current) {
                        if (!viewer) {
                           revisions.put(module.getName(), incoming);
                        }

                        module.fromJson(entry.getValue());
                        normalized.add(module.getName(), module.toJson());
                     }
                  }
               }
            } finally {
               applying--;
            }

            for (Module module : changedWhileApplying) {
               normalized.add(module.getName(), module.toJson());
            }

            changedWhileApplying.clear();
            if (!viewer && sender != null && !normalized.isEmpty()) {
               sender.accept(envelope(normalized));
            }
         } else {
            throw new IllegalArgumentException("Missing module revisions");
         }
      } else {
         throw new IllegalArgumentException("Missing module snapshot");
      }
   }

   public static void onSettingChanged(Setting<?> setting) {
      if (setting.getFeature() instanceof Module module && setting != module.enabled) {
         onModuleChanged(module);
      }
   }

   public static void onModuleChanged(Module module) {
      if (viewer) {
         PingBypassRenderState.clearModule(module.getName());
      }

      if (sender != null && synchronizedModule(module)) {
         if (applying != 0) {
            if (!viewer) {
               changedWhileApplying.add(module);
            }
         } else {
            if (viewer) {
               revisions.merge(module.getName(), 1L, Math::addExact);
            }

            JsonObject modules = new JsonObject();
            modules.add(module.getName(), module.toJson());
            sender.accept(envelope(modules));
         }
      }
   }

   private static JsonObject envelope(JsonObject modules) {
      JsonObject message = new JsonObject();
      message.add("modules", modules);
      JsonObject versions = new JsonObject();

      for (String name : modules.keySet()) {
         versions.addProperty(name, revisions.getOrDefault(name, 0L));
      }

      message.add("revisions", versions);
      return message;
   }

   private static void resetRevisions() {
      PingBypassRenderState.reset();
      revisions.clear();
      changedWhileApplying.clear();
      remoteRuntime.clear();
   }

   private static void suspendManagers() {
      if (KetamineClub.placementManager != null) {
         suspend(KetamineClub.placementManager, KetamineClub.placementManager::resetSessionState);
      }

      if (KetamineClub.swapManager != null) {
         suspend(KetamineClub.swapManager, KetamineClub.swapManager::resetSessionState);
      }

      if (KetamineClub.rotationManager != null) {
         suspend(KetamineClub.rotationManager, KetamineClub.rotationManager::resetSessionState);
      }

      if (KetamineClub.positionManager != null) {
         suspend(KetamineClub.positionManager, KetamineClub.positionManager::resetSessionState);
      }
   }

   private static void suspend(Object manager, Runnable reset) {
      boolean registered = Util.EVENT_BUS.isRegistered(manager);
      suspendedManagers.add(new PingBypassModules.ManagerSuspension(manager, reset, registered));

      try {
         reset.run();
      } finally {
         Util.EVENT_BUS.unregister(manager);
      }
   }

   private static void restoreManagers() {
      for (PingBypassModules.ManagerSuspension suspension : suspendedManagers) {
         try {
            suspension.reset().run();
            if (suspension.registered()) {
               Util.EVENT_BUS.register(suspension.manager());
            }
         } catch (Throwable var3) {
            KetamineClub.LOGGER.error("Could not restore manager {} after PingBypass", suspension.manager().getClass().getSimpleName(), var3);
         }
      }

      suspendedManagers.clear();
   }

   private record ManagerSuspension(Object manager, Runnable reset, boolean registered) {
   }

   private record RuntimeInfo(String displayInfo, String meta) {
   }
}
