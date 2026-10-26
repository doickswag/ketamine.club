package we.ketamine.club.util;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import we.ketamine.club.KetamineClub;

public final class TextUtil {
   private static final Pattern STYLE_PATTERN = Pattern.compile("\\{([^}]*)}");
   private static boolean initialized;

   private TextUtil() {
      throw new AssertionError("Can't create an instance of utility class");
   }

   public static void init() {
      if (initialized) {
         throw new IllegalStateException("Formatter is already initialized");
      } else {
         initialized = true;
      }
   }

   public static MutableComponent text(String content, Object... obj) {
      String formatted = obj != null && obj.length != 0 ? content.formatted(obj) : content;
      MutableComponent result = Component.empty();
      ArrayDeque<Style> styles = new ArrayDeque<>();
      styles.push(Style.EMPTY);
      Matcher matcher = STYLE_PATTERN.matcher(formatted);
      int chunkStart = 0;
      boolean previousTextEndedWithSpace = true;

      while (matcher.find()) {
         String chunk = formatted.substring(chunkStart, matcher.start());
         if (!chunk.isEmpty()) {
            result.append(Component.literal(chunk).setStyle(styles.peek()));
            previousTextEndedWithSpace = chunk.charAt(chunk.length() - 1) == ' ';
         }

         String styleName = matcher.group(1);
         if (styleName.isEmpty()) {
            if (styles.size() > 1) {
               styles.pop();
            }
         } else {
            styles.push(resolveStyle(styleName));
         }

         chunkStart = matcher.end();
         if (previousTextEndedWithSpace && chunkStart < formatted.length() && formatted.charAt(chunkStart) == ' ') {
            chunkStart++;
         }
      }

      if (chunkStart < formatted.length()) {
         result.append(Component.literal(formatted.substring(chunkStart)).setStyle(styles.peek()));
      }

      return result;
   }

   private static Style resolveStyle(String name) {
      if (name.equalsIgnoreCase("global")) {
         return Style.EMPTY.withColor(KetamineClub.colorManager.getAsInt("ui"));
      } else if (name.charAt(0) == '#') {
         String hex = name.substring(1);
         if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
         }

         return Style.EMPTY.withColor(Integer.parseInt(hex, 16));
      } else {
         try {
            TextColor color = TextColor.fromLegacyFormat(ChatFormatting.valueOf(name.toUpperCase(Locale.ROOT)));
            if (color != null) {
               return Style.EMPTY.withColor(color);
            }
         } catch (IllegalArgumentException var2) {
         }

         return Style.EMPTY.withColor(-1);
      }
   }
}
