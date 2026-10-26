package we.ketamine.club.util.render.font;

import java.awt.Font;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.modules.client.ClickGuiModule;

public final class Fonts {
   private static final Minecraft mc = Minecraft.getInstance();
   private static final String BUNDLED_PATH = "/assets/ketamineclub/font/default.ttf";
   private static final float SHADOW_OFFSET = 0.7F;
   private static volatile FontRenderer renderer;
   private static volatile boolean dirty = true;
   private static volatile boolean failureLatched;
   private static volatile boolean hudPass = false;
   private static volatile boolean preserveHudCase = false;
   private static final Map<String, Integer> CLICK_GUI_WIDTH_CACHE = new ConcurrentHashMap<>();
   private static Font bundledBase;
   private static boolean bundledLoaded;

   private Fonts() {
   }

   private static ClickGuiModule cfg() {
      return ClickGuiModule.getInstance();
   }

   private static boolean clickGuiOn() {
      ClickGuiModule c = cfg();
      return c != null && c.clickGuiFont.getValue();
   }

   private static boolean hudOn() {
      ClickGuiModule c = cfg();
      return c != null && c.hudFont.getValue();
   }

   private static boolean anyOn() {
      return clickGuiOn() || hudOn();
   }

   private static float clickGuiScale() {
      ClickGuiModule c = cfg();
      return c == null ? 1.0F : c.clickGuiFontScale.getValue();
   }

   public static synchronized void markDirty() {
      dirty = true;
      failureLatched = false;
      CLICK_GUI_WIDTH_CACHE.clear();
      ClickGuiModule c = cfg();
      boolean plannedOn = c != null && (Boolean.TRUE.equals(c.clickGuiFont.getPlannedValue()) || Boolean.TRUE.equals(c.hudFont.getPlannedValue()));
      if (!plannedOn) {
         try {
            disposeIfPresent();
         } catch (RuntimeException var3) {
            handleFailure(var3);
         }
      }
   }

   public static synchronized void cleanup() {
      failureLatched = false;
      dirty = true;
      CLICK_GUI_WIDTH_CACHE.clear();
      disposeIfPresent();
   }

   public static void beginHudPass() {
      hudPass = true;
      preserveHudCase = false;
   }

   public static void preserveHudCase(boolean preserve) {
      preserveHudCase = preserve;
   }

   public static void endHudPass() {
      preserveHudCase = false;
      hudPass = false;
   }

   public static String applyHudTextCase(String text) {
      return hudPass && !preserveHudCase ? ClickGuiModule.applyTextCase(text) : text;
   }

   public static FormattedCharSequence applyHudTextCase(FormattedCharSequence text) {
      return text != null && hudPass && !preserveHudCase && cfg() != null && cfg().lowercase.getValue()
         ? sink -> text.accept((index, style, codePoint) -> sink.accept(index, style, Character.toLowerCase(codePoint)))
         : text;
   }

   public static void drawString(GuiGraphicsExtractor graphics, String text, float x, float y, int color) {
      text = ClickGuiModule.applyTextCase(text);
      FontRenderer fr = clickGuiOn() ? renderer() : null;
      if (fr == null) {
         graphics.text(mc.font, text, (int)x, (int)y, color);
      } else {
         try {
            fr.drawString(graphics, text, x, y, color, false, clickGuiScale());
         } catch (RuntimeException var7) {
            handleFailure(var7);
            graphics.text(mc.font, text, (int)x, (int)y, color);
         }
      }
   }

   public static void drawText(GuiGraphicsExtractor graphics, FormattedCharSequence text, float x, float y, int color) {
      FontRenderer fr = clickGuiOn() ? renderer() : null;
      if (fr == null) {
         graphics.text(mc.font, text, Math.round(x), Math.round(y), color);
      } else {
         try {
            fr.drawText(graphics, text, x, y, color, false, clickGuiScale());
         } catch (RuntimeException var7) {
            handleFailure(var7);
            graphics.text(mc.font, text, Math.round(x), Math.round(y), color);
         }
      }
   }

   public static int width(String text) {
      text = ClickGuiModule.applyTextCase(text);
      FontRenderer fr = clickGuiOn() ? renderer() : null;
      if (fr == null) {
         return mc.font.width(text);
      } else {
         try {
            float scale = clickGuiScale();
            String cacheKey = Float.floatToIntBits(scale) + ":" + text;
            String cachedText = text;
            return CLICK_GUI_WIDTH_CACHE.computeIfAbsent(cacheKey, ignored -> Math.round(fr.getTextWidth(cachedText) * scale));
         } catch (RuntimeException var5) {
            handleFailure(var5);
            return mc.font.width(text);
         }
      }
   }

   public static int lineHeight() {
      FontRenderer fr = clickGuiOn() ? renderer() : null;
      return fr == null ? 9 : Math.max(1, Math.round(fr.getHeight() * clickGuiScale()));
   }

   public static boolean drawOverrideActive() {
      return hudPass && hudOn() && renderer() != null;
   }

   public static boolean renderOverrideString(GuiGraphicsExtractor graphics, String text, float x, float y, int color, boolean shadow) {
      FontRenderer fr = renderer();
      if (fr == null) {
         return false;
      } else {
         try {
            if (shadow) {
               fr.drawString(graphics, text, x + 0.7F, y + 0.7F, color, true);
            }

            fr.drawString(graphics, text, x, y, color, false);
            return true;
         } catch (RuntimeException var8) {
            handleFailure(var8);
            return false;
         }
      }
   }

   public static boolean renderOverrideText(GuiGraphicsExtractor graphics, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
      FontRenderer fr = renderer();
      if (fr == null) {
         return false;
      } else {
         try {
            if (shadow) {
               fr.drawText(graphics, text, x + 0.7F, y + 0.7F, color, true);
            }

            fr.drawText(graphics, text, x, y, color, false);
            return true;
         } catch (RuntimeException var8) {
            handleFailure(var8);
            return false;
         }
      }
   }

   public static int overrideWidth(String text) {
      FontRenderer fr = renderer();
      if (fr == null) {
         return mc.font.width(text);
      } else {
         try {
            return Math.round(fr.getTextWidth(text));
         } catch (RuntimeException var3) {
            handleFailure(var3);
            return mc.font.width(text);
         }
      }
   }

   public static int overrideWidth(FormattedCharSequence text) {
      FontRenderer fr = renderer();
      if (fr == null) {
         return mc.font.width(text);
      } else {
         try {
            return Math.round(fr.getTextWidth(text));
         } catch (RuntimeException var3) {
            handleFailure(var3);
            return mc.font.width(text);
         }
      }
   }

   private static FontRenderer renderer() {
      try {
         if (!anyOn()) {
            disposeIfPresent();
            return null;
         } else if (!failureLatched && mc.getWindow() != null) {
            if (dirty || renderer == null) {
               rebuild();
            }

            return renderer;
         } else {
            return null;
         }
      } catch (RuntimeException var1) {
         handleFailure(var1);
         return null;
      }
   }

   private static synchronized void rebuild() {
      if (dirty || renderer == null) {
         disposeIfPresent();
         CLICK_GUI_WIDTH_CACHE.clear();
         ClickGuiModule c = cfg();
         Font base = resolveBaseFont(c == null ? "" : c.fontName.getValue());
         renderer = new FontRenderer(new Font[]{base});
         dirty = false;
      }
   }

   private static synchronized void disposeIfPresent() {
      FontRenderer old = renderer;
      renderer = null;
      if (old != null) {
         old.close();
      }
   }

   public static void handleFailure(Throwable failure) {
      FontRenderer old;
      boolean shouldLog;
      synchronized (Fonts.class) {
         shouldLog = !failureLatched;
         failureLatched = true;
         dirty = true;
         CLICK_GUI_WIDTH_CACHE.clear();
         old = renderer;
         renderer = null;
      }

      if (old != null) {
         try {
            old.close();
         } catch (RuntimeException var5) {
            failure.addSuppressed(var5);
         }
      }

      if (shouldLog) {
         KetamineClub.LOGGER.error("Custom font rendering failed; using the vanilla font until font settings change", failure);
      }
   }

   private static Font resolveBaseFont(String familyName) {
      if (familyName != null && !familyName.isBlank()) {
         return new Font(familyName.trim(), 0, 1);
      } else {
         Font bundled = bundled();
         return bundled != null ? bundled : new Font("SansSerif", 0, 1);
      }
   }

   private static Font bundled() {
      if (bundledLoaded) {
         return bundledBase;
      } else {
         bundledLoaded = true;

         try (InputStream is = Fonts.class.getResourceAsStream("/assets/ketamineclub/font/default.ttf")) {
            if (is != null) {
               bundledBase = Font.createFont(0, is).deriveFont(1.0F);
            }
         } catch (Exception var5) {
            bundledBase = null;
         }

         return bundledBase;
      }
   }
}
