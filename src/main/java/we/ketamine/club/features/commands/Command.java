package we.ketamine.club.features.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import we.ketamine.club.features.Feature;
import we.ketamine.club.manager.CommandManager;
import we.ketamine.club.util.TextUtil;
import we.ketamine.club.util.player.ChatUtil;

public abstract class Command extends Feature {
   public static final int NO_OP = -1;
   public static final int SINGLE_FAILURE = 0;
   private final String[] aliases;
   private String description = "No description was provided for this command.";

   public Command(String... aliases) {
      super(aliases[0]);
      this.aliases = aliases;
   }

   public abstract void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> var1);

   protected int success(String message, Object... format) {
      sendMessage(message, format);
      return -1;
   }

   protected int success() {
      return 1;
   }

   protected int fail(String message, Object... format) {
      sendMessage("{red} " + message, format);
      return -1;
   }

   protected int fail() {
      return 0;
   }

   public String[] getAliases() {
      return this.aliases;
   }

   protected void setDescription(String description) {
      this.description = description;
   }

   public String getDescription() {
      return this.description;
   }

   public boolean isShown() {
      return true;
   }

   public static void sendMessage(String message, Object... format) {
      if (message != null) {
         ChatUtil.sendMessage(TextUtil.text(message, format));
      }
   }

   public static LiteralArgumentBuilder<CommandManager> literal(String literal) {
      return LiteralArgumentBuilder.literal(literal);
   }

   public static <T> RequiredArgumentBuilder<CommandManager, T> argument(String name, ArgumentType<T> type) {
      return RequiredArgumentBuilder.argument(name, type);
   }
}
