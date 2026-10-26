package we.ketamine.club.features.commands.impl;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.commands.argument.CommandArgumentType;
import we.ketamine.club.features.commands.argument.NumberArgumentType;
import we.ketamine.club.manager.CommandManager;

public class HelpCommand extends Command {
   private static final int ITEMS_PER_PAGE = 5;

   public HelpCommand() {
      super("help", "commands", "h", "cmds");
      this.setDescription("Displays all executable commands and additional information");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.then(
         ((RequiredArgumentBuilder)((RequiredArgumentBuilder)argument(
                     "page", NumberArgumentType.number(Integer.class, NumberArgumentType.minMax(1, Integer.MAX_VALUE))
                  )
                  .executes(ctx -> this.helpCommands(ctx, NumberArgumentType.get(Integer.class, ctx, "page"))))
               .then(argument("command_name", CommandArgumentType.command()).executes(this::helpSpecific)))
            .executes(ctx -> this.helpCommands(ctx, 1))
      );
   }

   private int helpSpecific(CommandContext<CommandManager> ctx) {
      Command command = CommandArgumentType.getCommand(ctx, "command_name");
      sendMessage("Usages for %s:", new Object[]{command.getName()});
      ParseResults<CommandManager> results = ((CommandManager)ctx.getSource()).getDispatcher().parse(command.getName(), (CommandManager)ctx.getSource());
      if (results.getContext().getNodes().isEmpty()) {
         return this.success("No usages available", new Object[0]);
      } else {
         Map<CommandNode<CommandManager>, String> smartUsages = ((CommandManager)ctx.getSource())
            .getDispatcher()
            .getSmartUsage(((ParsedCommandNode)Iterables.getLast(results.getContext().getNodes())).getNode(), (CommandManager)ctx.getSource());

         for (String usage : smartUsages.values()) {
            sendMessage(".%s %s", new Object[]{results.getReader().getString(), usage});
         }

         return this.success();
      }
   }

   private int helpCommands(CommandContext<CommandManager> ctx, int page) {
      List<Command> commands = ((CommandManager)ctx.getSource()).getCommands().stream().filter(Command::isShown).toList();
      int pageIndex = page;
      int pages = (int)Math.ceil(commands.size() / 5.0);
      if (page > pages) {
         pageIndex = 1;
      }

      List<Command> paginated = commands.subList((pageIndex - 1) * 5, Math.min(commands.size(), pageIndex * 5));
      sendMessage("Commands (%s):", new Object[]{commands.size()});

      for (Command command : paginated) {
         StringJoiner joiner = new StringJoiner(", ");

         for (String alias : command.getAliases()) {
            joiner.add(alias);
         }

         sendMessage("{dark_gray} %s: {reset}\n   %s", new Object[]{joiner, command.getDescription()});
      }

      return this.success("Page %s/%s", new Object[]{pageIndex, pages});
   }
}
