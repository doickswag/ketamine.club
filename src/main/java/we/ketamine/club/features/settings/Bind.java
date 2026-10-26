package we.ketamine.club.features.settings;

import com.google.common.base.Converter;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import we.ketamine.club.pingbypass.PingBypass;
import we.ketamine.club.pingbypass.PingBypassInput;
import we.ketamine.club.util.traits.Util;

public class Bind implements Util {
   public static final int MOUSE_BUTTON_OFFSET = 1000;
   private int key;
   private String displayName;

   public Bind(int key) {
      this.key = key;
   }

   public static Bind none() {
      return new Bind(-1);
   }

   public static Bind fromMouseButton(int button) {
      return new Bind(1000 + button);
   }

   public static boolean isMouseButton(int key) {
      return key >= 1000;
   }

   public int getKey() {
      return this.key;
   }

   public void setKey(int key) {
      this.key = key;
      this.displayName = null;
   }

   public boolean isEmpty() {
      return this.key < 0;
   }

   @Override
   public String toString() {
      if (this.displayName != null) {
         return this.displayName;
      } else if (this.isEmpty()) {
         return this.displayName = "None";
      } else if (isMouseButton(this.key)) {
         return this.displayName = "M" + (this.key - 1000 + 1);
      } else {
         String raw = InputConstants.getKey(new KeyEvent(this.key, 0, 0)).getName();
         String cleaned = raw.replace("key.keyboard.", "").replace("key.mouse.", "").replace('.', ' ').trim();
         StringBuilder out = new StringBuilder(cleaned.length());

         for (String part : cleaned.split(" ")) {
            if (!part.isEmpty()) {
               if (out.length() > 0) {
                  out.append(' ');
               }

               out.append(this.capitalise(part));
            }
         }

         return this.displayName = out.toString();
      }
   }

   public boolean isDown() {
      if (this.isEmpty()) {
         return false;
      } else if (PingBypass.isWorker()) {
         return PingBypassInput.isKeyDown(this.key);
      } else {
         return isMouseButton(this.key)
            ? GLFW.glfwGetMouseButton(mc.getWindow().handle(), this.key - 1000) == 1
            : GLFW.glfwGetKey(mc.getWindow().handle(), this.getKey()) == 1;
      }
   }

   private String capitalise(String str) {
      return str.isEmpty() ? "" : Character.toUpperCase(str.charAt(0)) + (str.length() != 1 ? str.substring(1).toLowerCase() : "");
   }

   public static class BindConverter extends Converter<Bind, JsonElement> {
      public BindConverter() {
      }

      public JsonElement doForward(Bind bind) {
         return new JsonPrimitive(bind.toString());
      }

      public Bind doBackward(JsonElement jsonElement) {
         String s = jsonElement.getAsString();
         if (s.equalsIgnoreCase("None")) {
            return Bind.none();
         } else {
            if (s.toUpperCase().startsWith("M") && s.length() >= 2) {
               try {
                  int button = Integer.parseInt(s.substring(1)) - 1;
                  if (button >= 0) {
                     return Bind.fromMouseButton(button);
                  }
               } catch (NumberFormatException var6) {
               }
            }

            int key = -1;

            try {
               key = InputConstants.getKey(s.toUpperCase()).getValue();
            } catch (Exception var5) {
            }

            return key == 0 ? Bind.none() : new Bind(key);
         }
      }
   }
}
