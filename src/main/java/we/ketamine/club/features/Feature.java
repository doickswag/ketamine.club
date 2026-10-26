package we.ketamine.club.features;

import java.util.ArrayList;
import java.util.List;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.features.settings.SettingFactory;
import we.ketamine.club.util.traits.Util;

public class Feature implements Util, SettingFactory {
   public List<Setting<?>> settings = new ArrayList<>();
   private String name;

   public Feature() {
   }

   public Feature(String name) {
      this.name = name;
   }

   public static boolean nullCheck() {
      return mc.player == null || mc.level == null;
   }

   public String getName() {
      return this.name;
   }

   public List<Setting<?>> getSettings() {
      return this.settings;
   }

   public boolean hasSettings() {
      return !this.settings.isEmpty();
   }

   public boolean isEnabled() {
      return false;
   }

   public boolean isDisabled() {
      return !this.isEnabled();
   }

   @Override
   public <T extends Setting<?>> T register(T setting) {
      setting.setFeature(this);
      this.settings.add(setting);
      return setting;
   }

   public Setting<?> getSettingByName(String name) {
      for (Setting<?> setting : this.settings) {
         if (setting.getName().equalsIgnoreCase(name)) {
            return setting;
         }
      }

      return null;
   }

   public void reset() {
      for (Setting<?> setting : this.settings) {
         setting.reset();
      }
   }
}
