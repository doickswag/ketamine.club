package we.ketamine.club.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import we.ketamine.club.features.Feature;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.features.settings.EnumConverter;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.util.BlockNameList;
import we.ketamine.club.util.traits.Jsonable;

public class ConfigManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("ConfigManager");
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getGameDir().resolve("ketamine.club");
   private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance().getGameDir().resolve("homovore");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final List<Jsonable> jsonables = new ArrayList<>();
   private final Set<String> failedLoads = new HashSet<>();

   public ConfigManager() {
   }

   public synchronized void addConfig(Jsonable jsonable) {
      String fileName = jsonable.getFileName();
      if (this.jsonables.stream().anyMatch(existing -> existing.getFileName().equals(fileName))) {
         throw new IllegalArgumentException("Duplicate config file registration: " + fileName);
      } else {
         this.jsonables.add(jsonable);
      }
   }

   public synchronized void load() {
      this.mkdirs();
      this.migrateLegacyConfigs();

      for (Jsonable jsonable : this.jsonables) {
         String fileName = jsonable.getFileName();
         Path path = CONFIG_PATH.resolve(fileName);
         if (!Files.notExists(path)) {
            try {
               String read = Files.readString(path);
               jsonable.fromJson(JsonParser.parseString(read));
               this.failedLoads.remove(fileName);
            } catch (Throwable var6) {
               this.failedLoads.add(fileName);
               LOGGER.error("Failed to load config {}", fileName, var6);
            }
         }
      }
   }

   public synchronized void save() {
      this.mkdirs();

      for (Jsonable jsonable : this.jsonables) {
         String fileName = jsonable.getFileName();
         if (this.failedLoads.contains(fileName)) {
            LOGGER.error("Skipping save for config {} because its last load or migration failed", fileName);
         } else {
            try {
               JsonElement json = jsonable.toJson();
               this.writeAtomically(CONFIG_PATH.resolve(fileName), GSON.toJson(json));
            } catch (Throwable var5) {
               LOGGER.error("Failed to write config {}", fileName, var5);
            }
         }
      }
   }

   private void writeAtomically(Path path, String contents) throws IOException {
      Path temporaryPath = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");

      try {
         Files.writeString(temporaryPath, contents);

         try {
            Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (AtomicMoveNotSupportedException var8) {
            Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING);
         }
      } finally {
         Files.deleteIfExists(temporaryPath);
      }
   }

   private void mkdirs() {
      try {
         Files.createDirectories(CONFIG_PATH);
      } catch (IOException var2) {
         throw new RuntimeException("Failed to create config directory " + CONFIG_PATH, var2);
      }
   }

   private void migrateLegacyConfigs() {
      if (Files.isDirectory(LEGACY_CONFIG_PATH)) {
         for (Jsonable jsonable : this.jsonables) {
            String fileName = jsonable.getFileName();
            Path source = LEGACY_CONFIG_PATH.resolve(fileName);
            Path destination = CONFIG_PATH.resolve(fileName);
            if (Files.isRegularFile(source) && !Files.exists(destination)) {
               Path temporaryPath = null;

               try {
                  temporaryPath = Files.createTempFile(CONFIG_PATH, fileName, ".migration");
                  Files.copy(source, temporaryPath, StandardCopyOption.REPLACE_EXISTING);
                  Files.move(temporaryPath, destination);
                  this.failedLoads.remove(fileName);
                  LOGGER.info("Migrated legacy config {} to ketamine.club", fileName);
               } catch (FileAlreadyExistsException var18) {
                  this.failedLoads.remove(fileName);
               } catch (IOException var19) {
                  this.failedLoads.add(fileName);
                  LOGGER.error("Failed to migrate legacy config {}", fileName, var19);
               } finally {
                  if (temporaryPath != null) {
                     try {
                        Files.deleteIfExists(temporaryPath);
                     } catch (IOException var17) {
                        LOGGER.warn("Failed to clean migration file for {}", fileName, var17);
                     }
                  }
               }
            }
         }
      }
   }

   public static void setValueFromJson(Feature feature, Setting setting, JsonElement element) {
      if (element != null && !element.isJsonNull()) {
         String var3 = setting.getType();
         switch (var3) {
            case "Boolean":
               setting.setValue(element.getAsBoolean());
               break;
            case "Double":
               setting.setValue(element.getAsDouble());
               break;
            case "Float":
               setting.setValue(element.getAsFloat());
               break;
            case "Integer":
               setting.setValue(element.getAsInt());
               break;
            case "String":
               setting.setValue(BlockNameList.readSetting(element));
               break;
            case "Bind":
               setting.setValue(new Bind(element.getAsInt()));
               break;
            case "Color":
               try {
                  String colorStr = element.getAsString();
                  String[] parts = colorStr.split(",");
                  if (parts.length == 4) {
                     int r = Integer.parseInt(parts[0]);
                     int g = Integer.parseInt(parts[1]);
                     int b = Integer.parseInt(parts[2]);
                     int a = Integer.parseInt(parts[3]);
                     setting.setValue(new Color(r, g, b, a));
                  }
               } catch (Exception var13) {
                  LOGGER.error("Error parsing color for: {} : {}", feature.getName(), setting.getName());
               }
               break;
            case "Pos":
               try {
                  String posStr = element.getAsString();
                  String[] parts = posStr.split(",");
                  if (parts.length == 2) {
                     float x = Float.parseFloat(parts[0]);
                     float y = Float.parseFloat(parts[1]);
                     setting.setValue(new Vector2f(x, y));
                  }
               } catch (Exception var12) {
                  LOGGER.error("Error parsing position for: {} : {}", feature.getName(), setting.getName());
               }
               break;
            case "Enum":
               try {
                  EnumConverter converter = new EnumConverter(setting.getValue().getClass());
                  Enum value = converter.doBackward(element);
                  if (value != null) {
                     setting.setValue(value);
                  }
               } catch (Exception var11) {
                  LOGGER.error("Error parsing enum for {}.{}: {}", new Object[]{feature.getName(), setting.getName(), var11});
               }
               break;
            default:
               LOGGER.error("Unknown Setting type for: {} : {}", feature.getName(), setting.getName());
         }
      }
   }
}
