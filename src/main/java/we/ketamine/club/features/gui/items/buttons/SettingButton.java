package we.ketamine.club.features.gui.items.buttons;

import java.awt.Color;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.features.settings.Setting;

public abstract class SettingButton<T> extends Button {
   protected final Setting<T> setting;

   protected SettingButton(Setting<T> setting) {
      super(setting.getName());
      this.setting = setting;
   }

   @Override
   public String getName() {
      return displayName(this.setting.getName());
   }

   @Override
   public void update() {
      this.setHidden(!this.setting.isVisible());
   }

   @Override
   public String getPage() {
      return this.setting.getPage();
   }

   public Setting<T> getSetting() {
      return this.setting;
   }

   private static String displayName(String name) {
      return name.replace('_', ' ').replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ");
   }

   public static SettingButton<?> create(Setting<?> setting) {
      if (setting.isColorSetting()) {
         return new ColorButton((Setting<Color>)setting);
      } else if (setting.isNumberSetting() && setting.hasRestriction()) {
         return new Slider((Setting<Number>)setting);
      } else if (setting.isEnumSetting()) {
         return new EnumButton((Setting<Enum<?>>)setting);
      } else {
         Object v = setting.getValue();
         if (v instanceof Boolean) {
            return new BooleanButton((Setting<Boolean>)setting);
         } else if (v instanceof Bind) {
            return new BindButton((Setting<Bind>)setting);
         } else {
            return v instanceof String ? new StringButton((Setting<String>)setting) : null;
         }
      }
   }
}
