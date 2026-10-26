package we.ketamine.club.manager;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import we.ketamine.club.features.modules.client.ClickGuiModule;
import we.ketamine.club.util.ColorUtil;

public class ColorManager {
   private final Map<String, Supplier<Color>> colors = new HashMap<>();

   public ColorManager() {
   }

   public void register(String name, Supplier<Color> supplier) {
      this.colors.put(name, supplier);
   }

   public Color get(String name) {
      Supplier<Color> s = this.colors.get(name);
      return s != null ? s.get() : Color.RED;
   }

   public int getAsInt(String name) {
      return ColorUtil.toRGBA(this.get(name));
   }

   public int getAsIntFullAlpha(String name) {
      Color c = this.get(name);
      return ColorUtil.toRGBA(new Color(c.getRed(), c.getGreen(), c.getBlue(), 255));
   }

   public int getWithAlpha(String name, float offset, int alpha) {
      if (ClickGuiModule.getInstance().theme.getValue() == ClickGuiModule.Theme.RAINBOW) {
         return ColorUtil.rainbow((int)(offset / 10.0F * ClickGuiModule.getInstance().rainbowHue.getValue().intValue())).getRGB();
      } else {
         Color c = this.get(name);
         return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha).getRGB();
      }
   }
}
