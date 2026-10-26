package we.ketamine.club.features.commands.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

public class OnlinePlayerArgumentType implements ArgumentType<String> {
   public OnlinePlayerArgumentType() {
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.readString();
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      ClientPacketListener conn = Minecraft.getInstance().getConnection();
      if (conn == null) {
         return builder.buildFuture();
      } else {
         String input = builder.getRemainingLowerCase();

         for (PlayerInfo info : conn.getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name != null && (input.isEmpty() || name.toLowerCase().contains(input))) {
               builder.suggest(name);
            }
         }

         return builder.buildFuture();
      }
   }

   public static OnlinePlayerArgumentType onlinePlayer() {
      return new OnlinePlayerArgumentType();
   }

   public static String getOnlinePlayer(CommandContext<?> ctx, String name) {
      return (String)ctx.getArgument(name, String.class);
   }
}
