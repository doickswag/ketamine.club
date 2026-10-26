package we.ketamine.club.util.render;

import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.ClickGuiModule;
import we.ketamine.club.util.ColorUtil;
import we.ketamine.club.util.render.font.Fonts;

public final class MenuRenderUtil {
   private static final String BRAND = "ketamine.club";
   private static final int BACKGROUND = -15921390;
   private static final int BUTTON = -434496223;
   private static final int BUTTON_HOVER = -266000082;
   private static final int BUTTON_DISABLED = -736946920;
   private static final int BORDER = -12828344;
   private static final int TEXT = -854793;
   private static final int TEXT_DISABLED = -9209471;

   private MenuRenderUtil() {
   }

   public static void drawTitleBackdrop(GuiGraphicsExtractor graphics, int width, int height) {
      graphics.fill(0, 0, width, height, -16250355);
      graphics.fillGradient(0, 0, width, height / 2, -15263201, -15921132);
      graphics.fillGradient(0, height / 2, width, height, -15921132, -16382199);
      int drift = (int)(System.currentTimeMillis() / 28L % Math.max(1, height + 48)) - 48;

      for (int band = 0; band < 8; band++) {
         int y = drift + band * (height + 48) / 8;
         if (y >= -2 && y < height) {
            drawProfileBar(graphics, 0, y, width, 2, 26);
         }
      }

      for (int x = 0; x < width; x += 32) {
         graphics.fill(x, 0, x + 1, height, 234881023);
      }

      for (int y = 0; y < height; y += 24) {
         graphics.fill(0, y, width, y + 1, 234881023);
      }

      int center = width / 2;
      graphics.fillGradient(Math.max(0, center - 180), 0, Math.min(width, center + 180), height, 135929896, 0);
      drawProfileBar(graphics, 0, Math.max(0, height / 2 - 1), width, 2, 76);
   }

   public static void drawTitleBrand(GuiGraphicsExtractor graphics, int width, float alpha) {
      float widthScale = (width - 36.0F) / Math.max(1, Fonts.width("ketamine.club"));
      float heightScale = 30.0F / Math.max(1, Fonts.lineHeight());
      float scale = Math.max(1.0F, Math.min(Math.min(2.5F, widthScale), heightScale));
      drawProfileText(graphics, "ketamine.club", width / 2.0F, 29.0F, scale, alpha);
      String detail = "utility client / v1.1.1";
      int detailWidth = Fonts.width(detail);
      float detailY = 29.0F + Fonts.lineHeight() * scale + 3.0F;
      Fonts.drawString(graphics, detail, (width - detailWidth) / 2.0F, detailY, withAlpha(profileColor(0.5F), Math.round(alpha * 205.0F)));
   }

   public static void drawServerBackground(GuiGraphicsExtractor graphics, int width, int height) {
      graphics.fill(0, 0, width, height, -15921390);
      graphics.fillGradient(0, 0, width, Math.min(height, 92), -15197666, -15921390);
      graphics.fillGradient(0, Math.max(0, height - 78), width, height, -15921390, -15263460);
      drawProfileBar(graphics, 0, 35, width, 1, 210);
   }

   public static void drawServerOverlay(GuiGraphicsExtractor graphics, Screen screen) {
      graphics.fill(0, 0, screen.width, 35, -99544297);
      drawProfileBar(graphics, 0, 35, screen.width, 1, 230);
      drawProfileText(graphics, "ketamine.club", screen.width / 2.0F, 6.0F, 1.25F, 1.0F);
      String section = "MULTIPLAYER";
      Fonts.drawString(graphics, section, (screen.width - Fonts.width(section)) / 2.0F, 23.0F, withAlpha(profileColor(0.5F), 190));
   }

   public static void drawButton(GuiGraphicsExtractor graphics, AbstractWidget button) {
      int x = button.getX();
      int y = button.getY();
      int right = button.getRight();
      int bottom = button.getBottom();
      boolean highlighted = button.active && button.isHoveredOrFocused();
      float widgetAlpha = Math.max(0.0F, Math.min(1.0F, button.getAlpha()));
      if (!(widgetAlpha <= 0.0F)) {
         graphics.fill(x + 1, y + 2, right + 1, bottom + 2, withWidgetAlpha(1694498816, widgetAlpha));
         graphics.fill(x, y, right, bottom, withWidgetAlpha(button.active ? (highlighted ? -266000082 : -434496223) : -736946920, widgetAlpha));
         int accent = profileColor(0.5F);
         graphics.outline(x, y, button.getWidth(), button.getHeight(), withWidgetAlpha(highlighted ? withAlpha(accent, 255) : -12828344, widgetAlpha));
         if (highlighted) {
            drawProfileBar(graphics, x + 1, y + 1, button.getWidth() - 2, 2, Math.round(widgetAlpha * 255.0F));
         } else {
            graphics.fill(x + 1, y + 1, x + 3, bottom - 1, withWidgetAlpha(button.active ? withAlpha(accent, 255) : -12236208, widgetAlpha));
         }

         String label = button.getMessage().getString();
         int textWidth = Fonts.width(label);
         int textY = y + Math.max(0, (button.getHeight() - Fonts.lineHeight()) / 2);
         int textColor = withWidgetAlpha(button.active ? -854793 : -9209471, widgetAlpha);
         if (textWidth <= button.getWidth() - 12) {
            Fonts.drawString(graphics, label, x + (button.getWidth() - textWidth) / 2.0F, textY, textColor);
         } else {
            String clipped = label;

            while (clipped.length() > 1 && Fonts.width(clipped + "...") > button.getWidth() - 12) {
               clipped = clipped.substring(0, clipped.length() - 1);
            }

            clipped = clipped + "...";
            int clippedWidth = Fonts.width(clipped);
            Fonts.drawString(graphics, clipped, x + (button.getWidth() - clippedWidth) / 2.0F, textY, textColor);
         }
      }
   }

   private static void drawProfileText(GuiGraphicsExtractor graphics, String text, float centerX, float y, float scale, float alpha) {
      int width = Fonts.width(text);
      MutableComponent component = Component.empty();
      MutableComponent shadow = Component.empty();

      for (int i = 0; i < text.length(); i++) {
         String glyph = String.valueOf(text.charAt(i));
         component.append(Component.literal(glyph).withStyle(Style.EMPTY.withColor(profileColor((float)i / Math.max(1, text.length() - 1)))));
         shadow.append(Component.literal(glyph).withStyle(Style.EMPTY.withColor(-16777216)));
      }

      FormattedCharSequence visual = component.getVisualOrderText();
      FormattedCharSequence shadowVisual = shadow.getVisualOrderText();
      Matrix3x2fStack pose = graphics.pose();
      pose.pushMatrix();

      try {
         pose.translate(centerX, y);
         pose.scale(scale, scale);
         Fonts.drawText(graphics, shadowVisual, -width / 2.0F + 0.7F, 0.7F, withAlpha(-16777216, Math.round(alpha * 180.0F)));
         Fonts.drawText(graphics, visual, -width / 2.0F, 0.0F, withAlpha(-1, Math.round(alpha * 255.0F)));
      } finally {
         pose.popMatrix();
      }
   }

   private static void drawProfileBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int alpha) {
      if (width > 0 && height > 0) {
         int segments = 6;

         for (int i = 0; i < segments; i++) {
            int left = x + width * i / segments;
            int right = x + width * (i + 1) / segments;
            RenderUtil.horizontalGradient(
               graphics,
               (float)left,
               (float)y,
               (float)right,
               (float)(y + height),
               new Color(withAlpha(profileColor((float)i / segments), alpha), true),
               new Color(withAlpha(profileColor((float)(i + 1) / segments), alpha), true)
            );
         }
      }
   }

   private static int profileColor(float offset) {
      ClickGuiModule config = ClickGuiModule.getInstance();
      if (config == null) {
         return -6307112;
      } else {
         offset = Math.max(0.0F, Math.min(1.0F, offset));

         return switch ((ClickGuiModule.Theme)config.theme.getValue()) {
            case RAINBOW -> ColorUtil.rainbow(Math.round(offset * config.rainbowHue.getValue().intValue())).getRGB();
            case COLORS -> categoryGradient(config, offset);
            case CUSTOM -> profileShade(config.customColor.getValue(), offset);
            case KETAMINECLUB -> profileShade(config.categoryAccent(Module.Category.CLIENT), offset);
         };
      }
   }

   private static int categoryGradient(ClickGuiModule config, float offset) {
      Module.Category[] categories = new Module.Category[]{
         Module.Category.COMBAT, Module.Category.WORLD, Module.Category.RENDER, Module.Category.MOVEMENT, Module.Category.PLAYER, Module.Category.FUNNY
      };
      float scaled = offset * (categories.length - 1);
      int index = Math.min(categories.length - 2, (int)scaled);
      float local = scaled - index;
      Color left = config.categoryAccent(categories[index]);
      Color right = config.categoryAccent(categories[index + 1]);
      return new Color(
            Math.round(left.getRed() + (right.getRed() - left.getRed()) * local),
            Math.round(left.getGreen() + (right.getGreen() - left.getGreen()) * local),
            Math.round(left.getBlue() + (right.getBlue() - left.getBlue()) * local)
         )
         .getRGB();
   }

   private static int profileShade(Color base, float offset) {
      if (base == null) {
         base = new Color(159, 194, 216);
      }

      float factor = 0.78F + offset * 0.22F;
      return new Color(
            Math.min(255, Math.round(base.getRed() * factor)),
            Math.min(255, Math.round(base.getGreen() * factor)),
            Math.min(255, Math.round(base.getBlue() * factor))
         )
         .getRGB();
   }

   private static int withAlpha(int color, int alpha) {
      return Math.max(0, Math.min(255, alpha)) << 24 | color & 16777215;
   }

   private static int withWidgetAlpha(int color, float alpha) {
      return withAlpha(color, Math.round((color >>> 24 & 0xFF) * alpha));
   }
}
