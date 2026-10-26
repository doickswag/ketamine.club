package we.ketamine.club.util.player;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription.Resource;
import net.minecraft.resources.Identifier;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.mixin.client.ChatComponentAccessor;
import we.ketamine.club.pingbypass.PingBypass;
import we.ketamine.club.util.traits.Util;

public class ChatUtil {
   private static final Map<String, MessageSignature> PERSISTENT = new HashMap<>();
   private static final SecureRandom RNG = new SecureRandom();
   public static volatile Line noAnimateHead;
   private static final FontDescription LOGO_FONT = new Resource(Identifier.fromNamespaceAndPath("ketamineclub", "logo"));

   public ChatUtil() {
   }

   public static void sendMessage(Component message) {
      sendSilentMessage(prefixed(message));
   }

   public static void sendSilentMessage(Component message) {
      if (!PingBypass.get().workerMessage(message)) {
         if (!Command.nullCheck()) {
            Util.mc.gui.hud.getChat().addClientSystemMessage(message);
         }
      }
   }

   public static void sendPersistent(String key, Component message) {
      addPersistent(key, prefixed(message));
   }

   private static void removeSilently(MessageSignature signature) {
      if (!Command.nullCheck()) {
         ChatComponent hud = Util.mc.gui.hud.getChat();
         ChatComponentAccessor accessor = (ChatComponentAccessor)hud;
         boolean removed = accessor.ketamineclub$getAllMessages().removeIf(line -> signature.equals(line.signature()));
         if (removed) {
            accessor.ketamineclub$refreshTrimmedMessages();
         }
      }
   }

   public static Component getClientNameComponent() {
      return Component.literal("trashasspaste").withStyle(Style.EMPTY.withFont(LOGO_FONT)).withColor(KetamineClub.colorManager.getAsInt("chat"));
   }

   public static Component getSkullComponent() {
      return Component.literal("\u2620").withColor(16733525);
   }

   public static Component getInfoComponent() {
      return Component.literal("\u2139").withColor(16777045);
   }

   public static void sendPrefixed(Component icon, Component message) {
      sendSilentMessage(prefixedWith(icon, message));
   }

   public static void sendPersistentPrefixed(String key, Component icon, Component message) {
      addPersistent(key, prefixedWith(icon, message));
   }

   private static void addPersistent(String key, Component component) {
      if (!PingBypass.get().workerMessage(component)) {
         if (!Command.nullCheck()) {
            MessageSignature previous = PERSISTENT.remove(key);
            if (previous != null) {
               removeSilently(previous);
            }

            byte[] data = new byte[256];
            RNG.nextBytes(data);
            MessageSignature signature = new MessageSignature(data);
            ChatComponent chat = Util.mc.gui.hud.getChat();
            ((ChatComponentAccessor)chat).ketamineclub$addMessage(component, signature, GuiMessageSource.SYSTEM_CLIENT, null);
            PERSISTENT.put(key, signature);
            List<Line> trimmed = ((ChatComponentAccessor)chat).ketamineclub$getTrimmedMessages();
            noAnimateHead = trimmed.isEmpty() ? null : trimmed.get(0);
         }
      }
   }

   private static Component prefixedWith(Component icon, Component message) {
      return Component.empty()
         .append(Component.literal("[").withColor(KetamineClub.colorManager.getAsInt("chatBracket")))
         .append(icon)
         .append(Component.literal("]").withColor(KetamineClub.colorManager.getAsInt("chatBracket")))
         .append(Component.literal(" ").withColor(KetamineClub.colorManager.getAsInt("chatBracket")))
         .append(message);
   }

   private static Component prefixed(Component message) {
      return Component.empty()
         .append(Component.literal("[").withColor(KetamineClub.colorManager.getAsInt("chatBracket")))
         .append(getClientNameComponent())
         .append(Component.literal("]").withColor(KetamineClub.colorManager.getAsInt("chatBracket")))
         .append(Component.literal(" ").withColor(KetamineClub.colorManager.getAsInt("chatBracket")))
         .append(message);
   }
}
