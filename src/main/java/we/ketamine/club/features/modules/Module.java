package we.ketamine.club.features.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import net.minecraft.ChatFormatting;
import org.joml.Vector2f;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.ClientEvent;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.features.Feature;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.ConfigManager;
import we.ketamine.club.pingbypass.PingBypassModules;
import we.ketamine.club.util.traits.Jsonable;
import we.ketamine.club.util.traits.Toggleable;

public class Module extends Feature implements Jsonable, Toggleable {
   private final String description;
   private final Module.Category category;
   public final Setting<Boolean> enabled = this.bool("Enabled", false).setPage("General");
   public final Setting<Bind> bind = this.key("Keybind", new Bind(-1)).setPage("General");
   public final Setting<Module.BindMode> bindMode = this.mode("BindMode", Module.BindMode.TOGGLE).setPage("General");
   public final Setting<String> displayName;
   public boolean hidden;

   public Module(String name, String description, Module.Category category) {
      super(name);
      this.displayName = this.str("DisplayName", name).setPage("General");
      this.description = description;
      this.category = category;
   }

   public void onEnable() {
   }

   public void onDisable() {
   }

   public void onToggle() {
   }

   public void onLoad() {
   }

   public void onTick() {
   }

   public void onRender2D(Render2DEvent event) {
   }

   public void onRender3D(Render3DEvent event) {
   }

   public void onUnload() {
   }

   public String getDisplayInfo() {
      return null;
   }

   public String getMeta() {
      return null;
   }

   @Override
   public void enable() {
      if (!this.isToggled()) {
         if (PingBypassModules.isRemote(this)) {
            this.enabled.setValue(true);
            if (this.isToggled()) {
               EVENT_BUS.post(new ClientEvent(ClientEvent.Type.TOGGLE_MODULE, this));
               PingBypassModules.onModuleChanged(this);
            }
         } else {
            try {
               this.enabled.setValue(true);
               if (!this.isEnabled()) {
                  return;
               }

               EVENT_BUS.register(this);
               EVENT_BUS.post(new ClientEvent(ClientEvent.Type.TOGGLE_MODULE, this));
               this.onToggle();
               this.onEnable();
               PingBypassModules.onModuleChanged(this);
            } catch (Throwable var6) {
               Throwable rollbackFailure = var6;

               try {
                  this.enabled.setValueNoEvent(false);
               } catch (Throwable var5) {
                  rollbackFailure = addFailure(var6, var5);
               }

               try {
                  EVENT_BUS.unregister(this);
               } catch (Throwable var4) {
                  rollbackFailure = addFailure(rollbackFailure, var4);
               }

               PingBypassModules.onModuleChanged(this);
               rethrow(rollbackFailure);
            }
         }
      }
   }

   @Override
   public void disable() {
      if (this.isToggled()) {
         if (PingBypassModules.isRemote(this)) {
            this.enabled.setValue(false);
            this.enabled.setValueNoEvent(false);
            EVENT_BUS.unregister(this);
            EVENT_BUS.post(new ClientEvent(ClientEvent.Type.TOGGLE_MODULE, this));
            PingBypassModules.onModuleChanged(this);
         } else {
            Throwable failure = null;

            try {
               this.enabled.setValue(false);
            } catch (Throwable var8) {
               failure = addFailure(failure, var8);
            }

            try {
               this.enabled.setValueNoEvent(false);
            } catch (Throwable var7) {
               failure = addFailure(failure, var7);
            }

            try {
               EVENT_BUS.unregister(this);
            } catch (Throwable var6) {
               failure = addFailure(failure, var6);
            }

            try {
               EVENT_BUS.post(new ClientEvent(ClientEvent.Type.TOGGLE_MODULE, this));
            } catch (Throwable var5) {
               failure = addFailure(failure, var5);
            }

            try {
               this.onToggle();
            } catch (Throwable var4) {
               failure = addFailure(failure, var4);
            }

            try {
               this.onDisable();
            } catch (Throwable var3) {
               failure = addFailure(failure, var3);
            }

            PingBypassModules.onModuleChanged(this);
            if (failure != null) {
               rethrow(failure);
            }
         }
      }
   }

   private static Throwable addFailure(Throwable failure, Throwable next) {
      if (failure == null) {
         return next;
      } else {
         if (failure != next) {
            failure.addSuppressed(next);
         }

         return failure;
      }
   }

   private static void rethrow(Throwable failure) {
      if (failure instanceof RuntimeException runtimeException) {
         throw runtimeException;
      } else if (failure instanceof Error error) {
         throw error;
      } else {
         throw new RuntimeException(failure);
      }
   }

   public String getDisplayName() {
      return this.displayName.getValue();
   }

   public void setDisplayName(String name) {
      Module module = KetamineClub.moduleManager.getModuleByDisplayName(name);
      Module originalModule = KetamineClub.moduleManager.getModuleByName(name);
      if (module != null && module != this || originalModule != null && originalModule != this) {
         Command.sendMessage("{red} A module of this name already exists.");
      } else {
         Command.sendMessage(this.getDisplayName() + ", name: " + this.getName() + ", has been renamed to: " + name);
         this.displayName.setValue(name);
      }
   }

   @Override
   public boolean isEnabled() {
      return this.enabled.getValue() && !PingBypassModules.isRemote(this);
   }

   @Override
   public boolean isToggled() {
      return this.enabled.getValue();
   }

   public String getDescription() {
      return this.description;
   }

   public Module.Category getCategory() {
      return this.category;
   }

   public String getInfo() {
      return null;
   }

   public Bind getBind() {
      return this.bind.getValue();
   }

   public void setBind(int key) {
      this.bind.setValue(new Bind(key));
   }

   public String getFullArrayString() {
      String info = PingBypassModules.displayInfo(this);
      return this.getDisplayName() + ChatFormatting.GRAY + (info != null ? " [" + ChatFormatting.WHITE + info + ChatFormatting.GRAY + "]" : "");
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();

      for (Setting<?> setting : this.getSettings()) {
         try {
            if (setting.getValue() instanceof Bind keyBind) {
               object.addProperty(setting.getName(), keyBind.getKey());
            } else if (setting.getValue() instanceof Color color) {
               object.addProperty(setting.getName(), color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + color.getAlpha());
            } else if (setting.getValue() instanceof Vector2f pos) {
               object.addProperty(setting.getName(), pos.x() + "," + pos.y());
            } else {
               object.addProperty(setting.getName(), setting.getValueAsString());
            }
         } catch (Throwable var8) {
            KetamineClub.LOGGER.error("Failed to create JSON field", var8);
         }
      }

      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element != null && !element.isJsonNull()) {
         JsonObject object = element.getAsJsonObject();
         boolean shouldEnable = object.has("Enabled") && Boolean.parseBoolean(object.get("Enabled").getAsString());

         for (Setting<?> setting : this.getSettings()) {
            if (setting != this.enabled) {
               try {
                  JsonElement settingElement = object.get(setting.getName());
                  if (settingElement != null && !settingElement.isJsonNull()) {
                     ConfigManager.setValueFromJson(this, setting, settingElement);
                  }
               } catch (Throwable var8) {
                  KetamineClub.LOGGER.error("Failed to load from JSON", var8);
               }
            }
         }

         try {
            this.setToggled(shouldEnable);
         } catch (Throwable var7) {
            KetamineClub.LOGGER.error("Failed to set module {} enabled state during load", this.getName(), var7);
         }
      }
   }

   public Module.BindMode getBindMode() {
      return this.bindMode.getValue();
   }

   public static enum BindMode {
      TOGGLE,
      HOLD;

      private BindMode() {
      }
   }

   public static enum Category {
      COMBAT("Combat"),
      WORLD("World"),
      RENDER("Render"),
      MOVEMENT("Movement"),
      PLAYER("Player"),
      FUNNY("Funny"),
      SLOP("Slop"),
      CLIENT("Client"),
      HUD("Hud");

      private final String name;

      private Category(String name) {
         this.name = name;
      }

      public String getName() {
         return this.name;
      }
   }
}
