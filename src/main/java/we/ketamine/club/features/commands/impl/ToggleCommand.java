package we.ketamine.club.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.commands.argument.ModuleArgumentType;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.manager.CommandManager;

public class ToggleCommand extends Command {
   public ToggleCommand() {
      super("toggle", "t");
      this.setDescription("Toggles a module");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      builder.then(
         argument("module", ModuleArgumentType.module(true))
            .executes(
               ctx -> {
                  Module module = ModuleArgumentType.getModule(ctx, "module");
                  module.toggle();
                  boolean toggled = module.isToggled();
                  return this.success(
                     "{gray} %s {reset} is now %s %s", new Object[]{module.getDisplayName(), toggled ? "{green}" : "{red}", toggled ? "enabled" : "disabled"}
                  );
               }
            )
      );
   }
}
