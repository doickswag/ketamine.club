package we.ketamine.club.features.gui;

import java.awt.Color;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.ClickGuiModule;

public final class GuiTheme {
   public static final int PANEL_WIDTH = 114;
   public static final int HEADER_HEIGHT = 16;
   public static final int PANEL_SPACING = 6;
   public static final int MODULE_HEIGHT = 14;
   public static final int SETTING_HEIGHT = 13;
   public static final int SETTING_INDENT = 6;
   public static final int SETTING_RIGHT_PAD = 2;
   public static final int PANEL_CLIP = 3;
   public static final int PANEL_HEADER_BG = new Color(26, 31, 37, 232).getRGB();
   public static final int PANEL_HEADER_HOVER = new Color(38, 45, 53, 234).getRGB();
   public static final int BODY_BG = new Color(18, 22, 27, 214).getRGB();
   public static final int BODY_BG_SOFT = new Color(25, 30, 36, 198).getRGB();
   public static final int SETTINGS_TRAY_BG = new Color(13, 17, 21, 194).getRGB();
   public static final int SCREEN_DIM = new Color(0, 0, 0, 126).getRGB();
   public static final int ROW_BG = new Color(24, 28, 34, 126).getRGB();
   public static final int ROW_HOVER_BG = new Color(255, 255, 255, 24).getRGB();
   public static final int ROW_ACTIVE_BG = new Color(35, 40, 48, 198).getRGB();
   public static final int ROW_SEPARATOR = new Color(255, 255, 255, 20).getRGB();
   public static final int SEPARATOR = new Color(255, 255, 255, 28).getRGB();
   public static final int OUTLINE = new Color(0, 0, 0, 186).getRGB();
   public static final int OUTLINE_INNER = new Color(255, 255, 255, 24).getRGB();
   public static final int HIGHLIGHT_TOP = new Color(255, 255, 255, 22).getRGB();
   public static final int HIGHLIGHT_SOFT = new Color(255, 255, 255, 14).getRGB();
   public static final int TEXT_HEADER = -1;
   public static final int TEXT_MODULE_ON = -1;
   public static final int TEXT_MODULE_OFF = -3090980;
   public static final int TEXT_SETTING = -1512465;
   public static final int TEXT_SETTING_VALUE = -5327168;
   public static final int SLIDER_TRACK = new Color(255, 255, 255, 34).getRGB();
   public static final int SLIDER_TRACK_HOVER = new Color(255, 255, 255, 56).getRGB();
   public static final int CONTROL_BG = new Color(255, 255, 255, 18).getRGB();
   public static final int CONTROL_BG_HOVER = new Color(255, 255, 255, 34).getRGB();
   public static final int CHECKBOX_OFF_BG = new Color(255, 255, 255, 20).getRGB();
   public static final int CHECKBOX_BORDER = new Color(255, 255, 255, 50).getRGB();

   private GuiTheme() {
   }

   public static Color categoryColor(Module.Category category) {
      return categoryColor(category, 0.0F);
   }

   public static Color categoryColor(Module.Category category, float yOffset) {
      ClickGuiModule m = ClickGuiModule.getInstance();
      return m == null ? new Color(70, 75, 82) : m.categoryAccent(category, yOffset);
   }

   public static boolean isFlat() {
      ClickGuiModule module = ClickGuiModule.getInstance();
      return module != null && module.style.getValue() == ClickGuiModule.Style.FLAT;
   }

   public static Color moduleColor(Module.Category category, float yOffset) {
      ClickGuiModule m = ClickGuiModule.getInstance();
      return m == null ? new Color(70, 75, 82) : m.moduleAccent(category, yOffset);
   }

   public static Color darken(Color c, float f) {
      return new Color((int)Math.max(0.0F, c.getRed() * f), (int)Math.max(0.0F, c.getGreen() * f), (int)Math.max(0.0F, c.getBlue() * f), c.getAlpha());
   }

   public static Color brighten(Color c, float amount) {
      return new Color(
         (int)Math.min(255.0F, c.getRed() + (255 - c.getRed()) * amount),
         (int)Math.min(255.0F, c.getGreen() + (255 - c.getGreen()) * amount),
         (int)Math.min(255.0F, c.getBlue() + (255 - c.getBlue()) * amount),
         c.getAlpha()
      );
   }

   public static Color withAlpha(Color c, int alpha) {
      return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
   }
}
