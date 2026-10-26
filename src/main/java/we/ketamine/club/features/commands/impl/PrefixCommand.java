package we.ketamine.club.features.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.modules.client.ClickGuiModule;
import we.ketamine.club.manager.CommandManager;

public class PrefixCommand extends Command {
   public PrefixCommand() {
      super("prefix", "setprefix");
      this.setDescription("Sets the command prefix");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.then(argument("prefix", StringArgumentType.word()).executes(ctx -> {
         String prefix = StringArgumentType.getString(ctx, "prefix");
         if (!((CommandManager)ctx.getSource()).setCommandPrefix(prefix)) {
            return this.fail("Prefix must be nonblank, contain no whitespace, and not start with '/'", new Object[0]);
         } else {
            ClickGuiModule clickGui = ClickGuiModule.getInstance();
            if (clickGui != null) {
               clickGui.prefix.setValueNoEvent(prefix);
            }

            return this.success("Prefix changed to {green} %s", new Object[]{prefix});
         }
      }));
   }
}
