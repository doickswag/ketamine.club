package we.ketamine.club.features.settings;

import java.awt.Color;
import org.joml.Vector2f;

public interface SettingFactory {
   <T extends Setting<?>> T register(T var1);

   default Setting<Boolean> bool(String name, boolean value) {
      return this.register(new Setting<>(name, value));
   }

   default <T extends Number> Setting<T> num(String name, T value, T min, T max) {
      return this.register(new Setting<>(name, value, min, max));
   }

   default Setting<String> str(String name, String value) {
      return this.register(new Setting<>(name, value));
   }

   default <T extends Enum<?>> Setting<T> mode(String name, T value) {
      return this.register(new Setting<>(name, value));
   }

   default Setting<Bind> key(String name, Bind bind) {
      return this.register(new Setting<>(name, bind));
   }

   default Setting<Color> color(String name, Color value) {
      return this.register(new Setting<>(name, value));
   }

   default Setting<Color> color(String name, int r, int g, int b, int a) {
      return this.register(new Setting<>(name, new Color(r, g, b, a)));
   }

   default Setting<Vector2f> vec2f(String name, Vector2f value) {
      return this.register(new Setting<>(name, value));
   }

   default Setting<Vector2f> vec2f(String name, float x, float y) {
      return this.register(new Setting<>(name, new Vector2f(x, y)));
   }
}
