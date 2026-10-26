package we.ketamine.club.util;

import we.ketamine.club.features.settings.Bind;

public class KeyboardUtil {
   public KeyboardUtil() {
   }

   public static String getKeyName(int key) {
      String str = new Bind(key).toString().toUpperCase();
      return str.replace("KEY.KEYBOARD", "").replace(".", " ");
   }
}
