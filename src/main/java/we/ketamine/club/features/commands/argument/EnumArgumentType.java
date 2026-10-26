package we.ketamine.club.features.commands.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import we.ketamine.club.features.commands.CommandExceptions;

public class EnumArgumentType<T extends Enum<?>> implements ArgumentType<T> {
   private final Map<String, T> enumNameMap = new HashMap<>();
   private final T defaultValue;

   public EnumArgumentType(Class<T> clazz, T defaultValue) {
      this.defaultValue = defaultValue;

      for (T constant : clazz.getEnumConstants()) {
         this.enumNameMap.put(constant.toString(), constant);
      }
   }

   public T parse(StringReader reader) throws CommandSyntaxException {
      String value = reader.readString();

      for (String name : this.enumNameMap.keySet()) {
         if (value.equalsIgnoreCase(name)) {
            return this.enumNameMap.get(name);
         }
      }

      if (this.defaultValue != null) {
         return this.defaultValue;
      } else {
         throw CommandExceptions.invalidArgument("Invalid enum type").createWithContext(reader);
      }
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String input = builder.getRemainingLowerCase();

      for (String key : this.enumNameMap.keySet()) {
         String name = key.toLowerCase();
         if (name.contains(input)) {
            builder.suggest(name);
         }
      }

      return builder.buildFuture();
   }

   public static <T extends Enum<?>> T getEnum(CommandContext<?> ctx, String name) {
      return (T)ctx.getArgument(name, Object.class);
   }

   public static <T extends Enum<?>> EnumArgumentType<T> _enum(Class<T> clazz) {
      return _enum(clazz, null);
   }

   public static <T extends Enum<?>> EnumArgumentType<T> _enum(Class<T> clazz, T defaultValue) {
      return new EnumArgumentType<>(clazz, defaultValue);
   }
}
