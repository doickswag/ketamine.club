package we.ketamine.club.features.commands.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.awt.Color;
import java.util.Collection;
import java.util.List;
import we.ketamine.club.features.commands.CommandExceptions;

public class ColorArgumentType implements ArgumentType<Color> {
   private static final List<String> EXAMPLES = List.of("hsb:55,100,100", "255,0,255", "0,255,255,80");
   private static final String[] RGBA_VALUES = new String[]{"Red", "Green", "Blue", "Alpha"};

   public ColorArgumentType() {
   }

   public Color parse(StringReader reader) throws CommandSyntaxException {
      String value = reader.getRemaining();
      if (value.startsWith("#")) {
         String hex = value.substring(1);
         if (hex.length() != 6) {
            throw CommandExceptions.invalidArgument("Hex colors must use #RRGGBB").createWithContext(reader);
         } else {
            try {
               return finish(reader, new Color(Integer.parseInt(hex, 16)));
            } catch (NumberFormatException var7) {
               throw CommandExceptions.invalidArgument("Invalid hexadecimal color \"%s\"", value).createWithContext(reader);
            }
         }
      } else if (value.startsWith("hsb:")) {
         int[] elements = this.parseColorElements(reader, value.substring(4), 3, 3);
         int hue = elements[0];
         if (hue >= 0 && hue <= 360) {
            int saturation = elements[1];
            if (saturation >= 0 && saturation <= 100) {
               int brightness = elements[2];
               if (brightness >= 0 && brightness <= 100) {
                  return finish(reader, Color.getHSBColor(hue / 360.0F, saturation / 100.0F, brightness / 100.0F));
               } else {
                  throw CommandExceptions.invalidArgument("Brightness value must be between 0-100").createWithContext(reader);
               }
            } else {
               throw CommandExceptions.invalidArgument("Saturation value must be between 0-100").createWithContext(reader);
            }
         } else {
            throw CommandExceptions.invalidArgument("Hue value must be between 0-360").createWithContext(reader);
         }
      } else {
         int[] elements = this.parseColorElements(reader, value, 3, 4);

         for (int i = 0; i < elements.length; i++) {
            int element = elements[i];
            if (element < 0 || element > 255) {
               throw CommandExceptions.invalidArgument("%s value must be between 0-255", RGBA_VALUES[i]).createWithContext(reader);
            }
         }

         return finish(reader, new Color(elements[0], elements[1], elements[2], elements.length == 4 ? elements[3] : 255));
      }
   }

   private static Color finish(StringReader reader, Color color) {
      reader.setCursor(reader.getTotalLength());
      return color;
   }

   private int[] parseColorElements(StringReader reader, String value, int minimum, int maximum) throws CommandSyntaxException {
      String[] parts = value.split(",", -1);
      if (parts.length >= minimum && parts.length <= maximum) {
         int[] elements = new int[parts.length];

         try {
            for (int i = 0; i < parts.length; i++) {
               elements[i] = Integer.parseInt(parts[i]);
            }

            return elements;
         } catch (NumberFormatException var8) {
            throw CommandExceptions.invalidArgument("Color values must be integers").createWithContext(reader);
         }
      } else {
         throw CommandExceptions.invalidArgument("Color element must have three values (with optional alpha value), either RGB(A) or hsb:HSB")
            .createWithContext(reader);
      }
   }

   public Collection<String> getExamples() {
      return EXAMPLES;
   }

   public static Color getColor(CommandContext<?> ctx, String name) {
      return (Color)ctx.getArgument(name, Color.class);
   }

   public static ColorArgumentType color() {
      return new ColorArgumentType();
   }
}
