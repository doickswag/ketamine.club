package we.ketamine.club.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class BlockNameList {
   private BlockNameList() {
      throw new AssertionError();
   }

   public static Set<String> parse(String raw) {
      if (raw != null && !raw.isBlank()) {
         Set<String> ids = new LinkedHashSet<>();
         Arrays.stream(raw.split("[,\\s]+")).map(String::trim).filter(value -> !value.isEmpty()).map(BlockNameList::normalize).forEach(ids::add);
         return Set.copyOf(ids);
      } else {
         return Set.of();
      }
   }

   public static String readSetting(JsonElement element) {
      if (element == null || element.isJsonNull()) {
         return "";
      } else if (element.isJsonPrimitive()) {
         return element.getAsString();
      } else if (!element.isJsonObject()) {
         return "";
      } else {
         JsonObject object = element.getAsJsonObject();
         JsonElement entries = object.has("entries") ? object.get("entries") : object.get("values");
         if (entries == null || entries.isJsonNull()) {
            return "";
         } else if (entries.isJsonPrimitive()) {
            return entries.getAsString();
         } else if (!entries.isJsonArray()) {
            return "";
         } else {
            StringBuilder result = new StringBuilder();
            entries.getAsJsonArray().forEach(value -> {
               if (result.length() > 0) {
                  result.append(',');
               }

               result.append(value.getAsString());
            });
            return result.toString();
         }
      }
   }

   private static String normalize(String value) {
      String normalized = value.toLowerCase(Locale.ROOT);
      return normalized.contains(":") ? normalized : "minecraft:" + normalized;
   }
}
