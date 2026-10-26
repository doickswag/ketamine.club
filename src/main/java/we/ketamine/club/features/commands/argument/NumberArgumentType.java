package we.ketamine.club.features.commands.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import we.ketamine.club.features.commands.CommandExceptions;

public record NumberArgumentType<T extends Number>(Class<T> type, NumberArgumentType.Constraint<T> constraint) implements ArgumentType<T> {
   public T parse(StringReader reader) throws CommandSyntaxException {
      int start = reader.getCursor();
      String input = reader.readString();
      int end = reader.getCursor();
      T value = null;

      try {
         if (this.type.isAssignableFrom(Integer.class)) {
            value = (T)Integer.valueOf(input);
         } else if (this.type.isAssignableFrom(Double.class)) {
            value = (T)Double.valueOf(input);
         } else if (this.type.isAssignableFrom(Float.class)) {
            value = (T)Float.valueOf(input);
         } else if (this.type.isAssignableFrom(Long.class)) {
            value = (T)Long.valueOf(input);
         }
      } catch (NumberFormatException var7) {
         reader.setCursor(start);
         throw CommandExceptions.invalidArgument("Could not parse \"%s\"", input).createWithContext(reader);
      }

      if (value == null) {
         reader.setCursor(start);
         throw CommandExceptions.invalidArgument("Could not parse \"%s\"", input).createWithContext(reader);
      } else if (!Double.isFinite(value.doubleValue())) {
         reader.setCursor(start);
         throw CommandExceptions.invalidArgument("Value must be finite").createWithContext(reader);
      } else {
         if (this.constraint != null) {
            reader.setCursor(start);
            this.constraint.validate(reader, value);
            reader.setCursor(end);
         }

         return value;
      }
   }

   public static <T extends Number> NumberArgumentType.Constraint<T> minMax(T min, T max) {
      return (reader, input) -> {
         double value = input.doubleValue();
         if (min.doubleValue() > value) {
            throw CommandExceptions.invalidArgument("Value is less than minimum(%s)", min.toString()).createWithContext(reader);
         } else if (max.doubleValue() < value) {
            throw CommandExceptions.invalidArgument("Value is more than maximum(%s)", max.toString()).createWithContext(reader);
         }
      };
   }

   public static <T extends Number> T get(Class<T> type, CommandContext<?> ctx, String name) {
      return (T)ctx.getArgument(name, type);
   }

   public static <T extends Number> NumberArgumentType<T> number(Class<T> type) {
      return number(type, null);
   }

   public static <T extends Number> NumberArgumentType<T> number(Class<T> type, NumberArgumentType.Constraint<T> constraint) {
      return new NumberArgumentType<>(type, constraint);
   }

   public interface Constraint<T extends Number> {
      void validate(StringReader var1, T var2) throws CommandSyntaxException;
   }
}
