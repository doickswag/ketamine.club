package we.ketamine.club.features.commands.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import we.ketamine.club.KetamineClub;

public class FriendArgumentType implements ArgumentType<String> {
   public FriendArgumentType() {
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      return reader.readString();
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String input = builder.getRemainingLowerCase();

      for (String name : KetamineClub.friendManager.getFriends()) {
         if (input.isEmpty() || name.toLowerCase().contains(input)) {
            builder.suggest(name);
         }
      }

      return builder.buildFuture();
   }

   public static FriendArgumentType friend() {
      return new FriendArgumentType();
   }

   public static String getFriend(CommandContext<?> ctx, String name) {
      return (String)ctx.getArgument(name, String.class);
   }
}
