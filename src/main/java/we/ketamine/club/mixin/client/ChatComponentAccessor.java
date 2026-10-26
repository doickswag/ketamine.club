package we.ketamine.club.mixin.client;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.multiplayer.chat.GuiMessage.Line;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({ChatComponent.class})
public interface ChatComponentAccessor {
   @Accessor("allMessages")
   List<GuiMessage> ketamineclub$getAllMessages();

   @Accessor("trimmedMessages")
   List<Line> ketamineclub$getTrimmedMessages();

   @Invoker("refreshTrimmedMessages")
   void ketamineclub$refreshTrimmedMessages();

   @Invoker("addMessage")
   void ketamineclub$addMessage(Component var1, MessageSignature var2, GuiMessageSource var3, GuiMessageTag var4);
}
